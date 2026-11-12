package com.aryan.reader

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.aryan.reader.data.RecentFileItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Pure model for the 3D bookshelf presentation of the unified library.
 *
 * The bookshelf is a *view* over the existing library: it never owns books. It receives the
 * already filtered/sorted [RecentFileItem] list (so Room's `RecentFileEntity`, `ShelfEntity` and
 * `BookShelfCrossRef` stay the single source of truth) and only persists presentation choices
 * (grouping, finish, manual order of book ids).
 */
internal enum class BookshelfGrouping { NONE, SERIES, AUTHOR, STATUS }

internal enum class BookshelfDisplay { MIXED, COVERS, SPINES }

internal enum class BookshelfFinish { WALNUT, OAK, EBONY, WHITE }

internal enum class BookshelfWall { AUTO, CREAM, SAND, SLATE }

internal data class BookshelfSettings(
    val grouping: BookshelfGrouping = BookshelfGrouping.SERIES,
    val display: BookshelfDisplay = BookshelfDisplay.MIXED,
    val finish: BookshelfFinish = BookshelfFinish.WALNUT,
    val wall: BookshelfWall = BookshelfWall.CREAM,
)

internal enum class BookshelfGroupKind {
    ALL,
    SERIES,
    STANDALONE,
    AUTHOR,
    UNKNOWN_AUTHOR,
    READING,
    UNREAD,
    FINISHED,
}

internal data class BookshelfGroup(
    val id: String,
    val kind: BookshelfGroupKind,
    /** Series or author name when [kind] is SERIES / AUTHOR, otherwise null. */
    val label: String?,
    val books: List<RecentFileItem>,
)

/** One book as placed on a shelf row. Sizes are in dp. */
internal data class ShelfSlot(
    val item: RecentFileItem,
    val asCover: Boolean,
    val width: Float,
    val height: Float,
    /** Stable key used to tint spines; books of one series share it. */
    val tintKey: String,
)

internal data class ShelfMetrics(val coverWidth: Float, val coverHeight: Float)

private const val MIN_COVER_WIDTH = 72f
private const val MAX_COVER_WIDTH = 118f
private const val COVER_ASPECT = 1.5f

/** Cover size so that roughly 3.6 covers fit across compact phones; larger on tablets. */
internal fun shelfMetricsFor(availableWidthDp: Float): ShelfMetrics {
    val coverWidth = (availableWidthDp / 4.3f).coerceIn(MIN_COVER_WIDTH, MAX_COVER_WIDTH)
    return ShelfMetrics(coverWidth, coverWidth * COVER_ASPECT)
}

private fun stableHash(value: String): Int = value.hashCode() and 0x7fffffff

private fun RecentFileItem.isReading(): Boolean {
    val progress = progressPercentage ?: 0f
    return progress in 0.01f..<100f
}

private fun RecentFileItem.isFinished(): Boolean = (progressPercentage ?: 0f) >= 100f

private fun RecentFileItem.normalizedSeries(): String? =
    seriesName?.trim()?.takeIf { it.isNotEmpty() }

private fun RecentFileItem.normalizedAuthor(): String? =
    author?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }

/**
 * Applies a user defined order. Ids missing from [order] (books added later) keep their relative
 * position and land after the explicitly ordered ones, so new imports never disappear or jump.
 */
internal fun applyManualOrder(books: List<RecentFileItem>, order: List<String>): List<RecentFileItem> {
    if (order.isEmpty()) return books
    val position = HashMap<String, Int>(order.size)
    order.forEachIndexed { index, id -> position.putIfAbsent(id, index) }
    val (ordered, unordered) = books.partition { it.bookId in position }
    return ordered.sortedBy { position.getValue(it.bookId) } + unordered
}

/**
 * Moves [movingId] right before [beforeId] (or to the end when null) inside the full order.
 * [currentBookIds] is the order the user currently sees; ids in [order] that are not visible
 * (hidden by a filter) keep their slot, so filtering never destroys a manual arrangement.
 */
internal fun moveInOrder(
    order: List<String>,
    currentBookIds: List<String>,
    movingId: String,
    beforeId: String?,
): List<String> {
    if (movingId == beforeId) return order
    val base = (order + currentBookIds).distinct().toMutableList()
    // Make the visible sequence authoritative for the visible books.
    val visible = currentBookIds.toHashSet()
    val visibleSlots = base.indices.filter { base[it] in visible }
    currentBookIds.forEachIndexed { i, id -> if (i < visibleSlots.size) base[visibleSlots[i]] = id }
    base.remove(movingId)
    val target = beforeId?.let(base::indexOf)?.takeIf { it >= 0 }
    if (target == null) base.add(movingId) else base.add(target, movingId)
    return base
}

internal fun buildBookshelfGroups(
    books: List<RecentFileItem>,
    grouping: BookshelfGrouping,
    manualOrder: List<String>?,
): List<BookshelfGroup> {
    if (books.isEmpty()) return emptyList()
    val ordered = if (manualOrder != null) applyManualOrder(books, manualOrder) else books
    return when (grouping) {
        BookshelfGrouping.NONE ->
            listOf(BookshelfGroup("all", BookshelfGroupKind.ALL, null, ordered))

        BookshelfGrouping.SERIES -> {
            val series = LinkedHashMap<String, MutableList<RecentFileItem>>()
            val names = HashMap<String, String>()
            val standalone = mutableListOf<RecentFileItem>()
            ordered.forEach { book ->
                val name = book.normalizedSeries()
                if (name == null) {
                    standalone += book
                } else {
                    val key = name.lowercase()
                    names.putIfAbsent(key, name)
                    series.getOrPut(key) { mutableListOf() } += book
                }
            }
            val groups = series.entries
                .sortedBy { it.key }
                .map { (key, members) ->
                    val inSeriesOrder = if (manualOrder != null) {
                        members
                    } else {
                        // sortedWith is stable: books without an index keep the library sort.
                        members.sortedWith(
                            compareBy<RecentFileItem> { it.seriesIndex == null }
                                .thenBy { it.seriesIndex ?: 0.0 },
                        )
                    }
                    BookshelfGroup("series:$key", BookshelfGroupKind.SERIES, names.getValue(key), inSeriesOrder)
                }
            if (standalone.isEmpty()) groups
            else groups + BookshelfGroup("standalone", BookshelfGroupKind.STANDALONE, null, standalone)
        }

        BookshelfGrouping.AUTHOR -> {
            val byAuthor = LinkedHashMap<String, MutableList<RecentFileItem>>()
            val names = HashMap<String, String>()
            val unknown = mutableListOf<RecentFileItem>()
            ordered.forEach { book ->
                val name = book.normalizedAuthor()
                if (name == null) {
                    unknown += book
                } else {
                    val key = name.lowercase()
                    names.putIfAbsent(key, name)
                    byAuthor.getOrPut(key) { mutableListOf() } += book
                }
            }
            val groups = byAuthor.entries
                .sortedBy { it.key }
                .map { (key, members) ->
                    BookshelfGroup("author:$key", BookshelfGroupKind.AUTHOR, names.getValue(key), members)
                }
            if (unknown.isEmpty()) groups
            else groups + BookshelfGroup("author:?", BookshelfGroupKind.UNKNOWN_AUTHOR, null, unknown)
        }

        BookshelfGrouping.STATUS -> {
            val reading = ordered.filter { it.isReading() }
            val unread = ordered.filter { !it.isReading() && !it.isFinished() }
            val finished = ordered.filter { it.isFinished() }
            listOfNotNull(
                reading.takeIf { it.isNotEmpty() }
                    ?.let { BookshelfGroup("status:reading", BookshelfGroupKind.READING, null, it) },
                unread.takeIf { it.isNotEmpty() }
                    ?.let { BookshelfGroup("status:unread", BookshelfGroupKind.UNREAD, null, it) },
                finished.takeIf { it.isNotEmpty() }
                    ?.let { BookshelfGroup("status:finished", BookshelfGroupKind.FINISHED, null, it) },
            )
        }
    }
}

/**
 * Decides, per book, whether it faces the viewer (cover) or shows its spine, and its size.
 * Mixed mode mirrors a real shelf: books being read face forward, everything else shows its spine
 * with slightly varied heights/widths.
 */
internal fun buildShelfSlots(
    group: BookshelfGroup,
    display: BookshelfDisplay,
    metrics: ShelfMetrics,
): List<ShelfSlot> = group.books.mapIndexed { index, book ->
    val asCover = when (display) {
        BookshelfDisplay.COVERS -> true
        BookshelfDisplay.SPINES -> false
        BookshelfDisplay.MIXED -> book.isReading()
    }
    val h = stableHash(book.bookId)
    val tintKey = book.normalizedSeries()?.lowercase() ?: book.bookId
    if (asCover) {
        ShelfSlot(book, true, metrics.coverWidth, metrics.coverHeight, tintKey)
    } else {
        // Tall, slim spines with varied heights, like the packed rows of a real shelf.
        val spineWidth = metrics.coverWidth * (0.22f + (h % 9) * 0.012f)
        val spineHeight = metrics.coverHeight * (1.10f + ((h / 9) % 4) * 0.05f)
        ShelfSlot(book, false, spineWidth, spineHeight, tintKey)
    }
}

/** Greedy left-to-right packing of slots into shelf rows of [rowWidth] dp. */
internal fun packShelfRows(slots: List<ShelfSlot>, rowWidth: Float, gap: Float): List<List<ShelfSlot>> {
    if (slots.isEmpty()) return emptyList()
    val rows = mutableListOf<MutableList<ShelfSlot>>()
    var used = 0f
    for (slot in slots) {
        val current = rows.lastOrNull()
        val needed = if (current.isNullOrEmpty()) slot.width else used + gap + slot.width
        if (current == null || (current.isNotEmpty() && needed > rowWidth)) {
            rows += mutableListOf(slot)
            used = slot.width
        } else {
            current += slot
            used = needed
        }
    }
    return rows
}

private val orderSerializer = ListSerializer(String.serializer())

internal fun encodeBookshelfOrder(order: List<String>): String = Json.encodeToString(orderSerializer, order)

internal fun decodeBookshelfOrder(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        Json.decodeFromString(orderSerializer, raw)
    } catch (_: Exception) {
        emptyList()
    }
}

private inline fun <reified E : Enum<E>> String?.toEnumOrDefault(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default

/**
 * Persists the bookshelf look and the manual order in `reader_prefs`, which is on the backup
 * allowlist, so a restored device keeps its arranged shelves.
 */
internal class BookshelfPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSettings(): BookshelfSettings = BookshelfSettings(
        grouping = prefs.getString(KEY_GROUPING, null).toEnumOrDefault(BookshelfGrouping.SERIES),
        display = prefs.getString(KEY_DISPLAY, null).toEnumOrDefault(BookshelfDisplay.MIXED),
        finish = prefs.getString(KEY_FINISH, null).toEnumOrDefault(BookshelfFinish.WALNUT),
        wall = prefs.getString(KEY_WALL, null).toEnumOrDefault(BookshelfWall.AUTO),
    )

    fun saveSettings(settings: BookshelfSettings) {
        prefs.edit {
            putString(KEY_GROUPING, settings.grouping.name)
            putString(KEY_DISPLAY, settings.display.name)
            putString(KEY_FINISH, settings.finish.name)
            putString(KEY_WALL, settings.wall.name)
        }
    }

    fun loadOrder(): List<String> = decodeBookshelfOrder(prefs.getString(KEY_ORDER, null))

    fun saveOrder(order: List<String>) {
        prefs.edit {
            if (order.isEmpty()) remove(KEY_ORDER) else putString(KEY_ORDER, encodeBookshelfOrder(order))
        }
    }

    private companion object {
        const val PREFS_NAME = "reader_prefs"
        const val KEY_GROUPING = "bookshelf_grouping"
        const val KEY_DISPLAY = "bookshelf_display"
        const val KEY_FINISH = "bookshelf_finish"
        const val KEY_WALL = "bookshelf_wall"
        const val KEY_ORDER = "bookshelf_manual_order"
    }
}
