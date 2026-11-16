package com.aryan.reader

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.RecentFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.TimeUnit

/**
 * Recognises books that have no cover and downloads the real one.
 *
 * It only ever fills an *empty* cover: covers embedded in the file, extracted from a page or chosen by
 * the user are never replaced. The title/author already stored for the book are used to search Google
 * Books (good Portuguese coverage) and, if that finds nothing, Open Library. A result is accepted only
 * when its title (or author) really matches, so a wrong cover is worse than none and is never saved.
 *
 * The work is opt-out (see [isEnabled]), runs only when a network is available, and remembers which
 * books were already tried so each book is looked up at most once.
 */
class BookCoverEnrichmentWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!isEnabled(applicationContext)) return@withContext Result.success()

        val repository = RecentFilesRepository(applicationContext)
        val attempted = prefs.getStringSet(KEY_ATTEMPTED, emptySet()).orEmpty().toMutableSet()
        val candidates = repository.getAllFilesForSync()
            .filter { it.needsOnlineCover() && it.bookId !in attempted }
            .take(MAX_BOOKS_PER_RUN)
        if (candidates.isEmpty()) return@withContext Result.success()

        var networkFailure = false
        for (book in candidates) {
            if (isStopped) break
            val query = BookLookupQuery.from(book)
            if (query == null) {
                attempted += book.bookId
                continue
            }
            val coverBytes = try {
                findCoverBytes(query)
            } catch (e: IOException) {
                Timber.d(e, "Cover lookup paused (network): ${book.bookId}")
                networkFailure = true
                break
            }
            if (coverBytes != null) {
                saveCover(repository, book.bookId, coverBytes)
            }
            attempted += book.bookId
            delay(REQUEST_SPACING_MS)
        }
        prefs.edit().putStringSet(KEY_ATTEMPTED, attempted).apply()
        if (networkFailure) Result.retry() else Result.success()
    }

    private suspend fun saveCover(repository: RecentFilesRepository, bookId: String, bytes: ByteArray) {
        // Re-read right before writing: the book may have received a cover in the meantime.
        val fresh = repository.getFileByBookId(bookId) ?: return
        if (!fresh.needsOnlineCover()) return
        val path = repository.saveEmbeddedCoverToCache(bytes, "book:$bookId".toUri(), "jpg") ?: return
        repository.updateExtractedMetadata(listOf(fresh.copy(coverImagePath = path)))
    }

    private fun findCoverBytes(query: BookLookupQuery): ByteArray? =
        googleBooksCover(query) ?: openLibraryCover(query)

    // --- Google Books -------------------------------------------------------------------------

    private fun googleBooksCover(query: BookLookupQuery): ByteArray? {
        val q = buildString {
            append("intitle:\"").append(query.title).append('"')
            query.authorSurname?.let { append("+inauthor:\"").append(it).append('"') }
        }
        val url = "https://www.googleapis.com/books/v1/volumes?q=${URLEncoder.encode(q, "UTF-8")}" +
            "&maxResults=6&printType=books&fields=items(volumeInfo(title,authors,imageLinks))"
        val body = httpGet(url)?.toString(Charsets.UTF_8) ?: return null
        val items = JSONObject(body).optJSONArray("items") ?: return null

        var best: Pair<Double, String>? = null
        for (i in 0 until items.length()) {
            val info = items.getJSONObject(i).optJSONObject("volumeInfo") ?: continue
            val image = info.optJSONObject("imageLinks")
                ?.let { it.optString("thumbnail").ifBlank { it.optString("smallThumbnail") } }
                ?.takeIf { it.isNotBlank() } ?: continue
            val authors = info.optJSONArray("authors")?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
            val score = query.score(info.optString("title"), authors)
            if (score >= ACCEPT_SCORE && (best == null || score > best.first)) best = score to image
        }
        val link = best?.second ?: return null
        // Ask for a larger rendition, and drop the "curled page" effect Google adds by default.
        val base = link.replace("http://", "https://").replace("&edge=curl", "")
        for (zoom in listOf(3, 2, 1)) {
            val sized = base.replace(Regex("zoom=\\d"), "zoom=$zoom")
            val bytes = httpGet(sized) ?: continue
            if (isUsableCover(bytes)) return bytes
        }
        return null
    }

    // --- Open Library -------------------------------------------------------------------------

    private fun openLibraryCover(query: BookLookupQuery): ByteArray? {
        val params = buildString {
            append("title=").append(URLEncoder.encode(query.title, "UTF-8"))
            query.authorSurname?.let { append("&author=").append(URLEncoder.encode(it, "UTF-8")) }
            append("&limit=6&fields=title,author_name,cover_i")
        }
        val body = httpGet("https://openlibrary.org/search.json?$params")?.toString(Charsets.UTF_8) ?: return null
        val docs = JSONObject(body).optJSONArray("docs") ?: return null

        var best: Pair<Double, Long>? = null
        for (i in 0 until docs.length()) {
            val doc = docs.getJSONObject(i)
            val coverId = doc.optLong("cover_i", 0L).takeIf { it > 0L } ?: continue
            val authors = doc.optJSONArray("author_name")?.let { arr -> List(arr.length()) { arr.getString(it) } }.orEmpty()
            val score = query.score(doc.optString("title"), authors)
            if (score >= ACCEPT_SCORE && (best == null || score > best.first)) best = score to coverId
        }
        val id = best?.second ?: return null
        val bytes = httpGet("https://covers.openlibrary.org/b/id/$id-L.jpg?default=false") ?: return null
        return bytes.takeIf(::isUsableCover)
    }

    // --- Helpers ------------------------------------------------------------------------------

    /** Returns the body, null for "no such thing" (HTTP 4xx/404), and throws [IOException] on network trouble. */
    private fun httpGet(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Episteme/1.0 (personal book cover lookup)")
        }
        return try {
            when (val code = connection.responseCode) {
                200 -> connection.inputStream.use { it.readBytes() }
                429, in 500..599 -> throw IOException("HTTP $code")
                else -> null
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Rejects tiny placeholders ("image not available") and undecodable data. */
    private fun isUsableCover(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_COVER_BYTES) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth >= MIN_COVER_WIDTH && bounds.outHeight >= bounds.outWidth
    }

    companion object {
        const val WORK_NAME = "BookCoverEnrichmentWorker"
        private const val PREFS_NAME = "reader_prefs"
        private const val KEY_ENABLED = "online_cover_lookup_enabled"
        private const val KEY_ATTEMPTED = "online_cover_lookup_attempted_ids"
        private const val MAX_BOOKS_PER_RUN = 40
        private const val REQUEST_SPACING_MS = 300L
        private const val MIN_COVER_BYTES = 4_000
        private const val MIN_COVER_WIDTH = 120
        private const val ACCEPT_SCORE = 0.6

        private val SUPPORTED_TYPES = setOf(
            FileType.PDF, FileType.EPUB, FileType.MOBI, FileType.FB2,
            FileType.ODT, FileType.FODT, FileType.DOCX,
        )

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) enqueue(context)
        }

        /** Queues one lookup pass; harmless to call often (a pass already queued or running is kept). */
        fun enqueue(context: Context) {
            if (!isEnabled(context)) return
            val request = OneTimeWorkRequestBuilder<BookCoverEnrichmentWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        internal fun RecentFileItem.needsOnlineCover(): Boolean =
            coverImagePath.isNullOrBlank() && !isDeleted && type in SUPPORTED_TYPES
    }
}

/** What we know about a book, cleaned up for searching, plus the matching rule. */
internal class BookLookupQuery private constructor(
    val title: String,
    val authorSurname: String?,
) {
    private val titleTokens = tokens(title)

    /** 0..1 confidence that a search hit is this book: title overlap, boosted by a matching author. */
    fun score(candidateTitle: String, candidateAuthors: List<String>): Double {
        val candidate = tokens(candidateTitle)
        if (titleTokens.isEmpty() || candidate.isEmpty()) return 0.0
        val common = titleTokens.intersect(candidate).size.toDouble()
        val titleScore = common / minOf(titleTokens.size, candidate.size)
        val authorMatches = authorSurname != null && candidateAuthors.any { normalize(it).contains(normalize(authorSurname)) }
        return when {
            authorMatches && titleScore >= 0.5 -> maxOf(titleScore, 0.75)
            else -> titleScore * (if (authorSurname == null || authorMatches) 1.0 else 0.8)
        }
    }

    companion object {
        private val EXTENSION = Regex("\\.(pdf|epub|mobi|fb2|odt|fodt|docx)$", RegexOption.IGNORE_CASE)
        private val BRACKETS = Regex("\\[[^]]*]|\\([^)]*\\)")
        private val SERIES_SUFFIX = Regex("\\s*[-–:]\\s*(livro|book|vol\\.?|volume|parte)\\s*\\d+.*$", RegexOption.IGNORE_CASE)
        private val NOISE = Regex("(z-?lib|libgen|ebook|epub|pdf|\\.org)", RegexOption.IGNORE_CASE)

        fun from(book: RecentFileItem): BookLookupQuery? {
            val rawTitle = book.title?.takeIf { it.isNotBlank() }
                ?: book.customName?.takeIf { it.isNotBlank() }
                ?: book.displayName
            val title = rawTitle
                .replace(EXTENSION, "")
                .replace('_', ' ')
                .replace(BRACKETS, " ")
                .replace(SERIES_SUFFIX, "")
                .replace(NOISE, " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            // Too short or file-name-like titles would match random books.
            if (title.length < 3 || tokens(title).isEmpty()) return null
            val surname = book.author?.trim()?.takeIf { it.isNotBlank() && !it.equals("Unknown", true) }?.let { author ->
                val first = author.split(';', '&', '/').first().trim()
                if (',' in first) first.substringBefore(',').trim() else first.split(' ').last()
            }?.takeIf { it.length >= 2 }
            return BookLookupQuery(title, surname)
        }

        internal fun normalize(value: String): String =
            Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]+"), " ")
                .trim()

        private val STOP_WORDS = setOf("a", "o", "as", "os", "de", "do", "da", "dos", "das", "e", "the", "of", "and", "an")

        internal fun tokens(value: String): Set<String> =
            normalize(value).split(' ').filter { it.isNotEmpty() && it !in STOP_WORDS }.toSet()
    }
}
