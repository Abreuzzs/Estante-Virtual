package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfModelTest {

    @Test
    fun `series grouping orders by series index and puts standalone books last`() {
        val books = listOf(
            book("b3", series = "Mistborn", index = 3.0),
            book("solo", title = "Solo"),
            book("b1", series = "mistborn", index = 1.0),
            book("h1", series = "Harry Potter", index = 1.0),
            book("b2", series = "Mistborn", index = 2.0),
        )

        val groups = buildBookshelfGroups(books, BookshelfGrouping.SERIES, manualOrder = null)

        assertEquals(listOf("Harry Potter", "Mistborn", null), groups.map { it.label })
        assertEquals(listOf("b1", "b2", "b3"), groups[1].books.map { it.bookId })
        assertEquals(BookshelfGroupKind.STANDALONE, groups.last().kind)
        assertEquals(books.size, groups.sumOf { it.books.size })
    }

    @Test
    fun `status grouping uses reading unread finished buckets without duplicating books`() {
        val books = listOf(
            book("unread", progress = 0f),
            book("reading", progress = 42f),
            book("done", progress = 100f),
            book("none", progress = null),
        )

        val groups = buildBookshelfGroups(books, BookshelfGrouping.STATUS, manualOrder = null)

        assertEquals(
            listOf(BookshelfGroupKind.READING, BookshelfGroupKind.UNREAD, BookshelfGroupKind.FINISHED),
            groups.map { it.kind },
        )
        assertEquals(listOf("unread", "none"), groups[1].books.map { it.bookId })
        assertEquals(books.map { it.bookId }.toSet(), groups.flatMap { g -> g.books.map { it.bookId } }.toSet())
    }

    @Test
    fun `author grouping separates unknown authors`() {
        val books = listOf(
            book("a", author = "Orwell"),
            book("b", author = "Unknown"),
            book("c", author = "  "),
            book("d", author = "orwell"),
        )

        val groups = buildBookshelfGroups(books, BookshelfGrouping.AUTHOR, manualOrder = null)

        assertEquals(2, groups.size)
        assertEquals(listOf("a", "d"), groups[0].books.map { it.bookId })
        assertEquals(BookshelfGroupKind.UNKNOWN_AUTHOR, groups[1].kind)
    }

    @Test
    fun `empty library produces no groups`() {
        assertTrue(buildBookshelfGroups(emptyList(), BookshelfGrouping.SERIES, null).isEmpty())
    }

    @Test
    fun `manual order is applied and new books are appended`() {
        val books = listOf(book("a"), book("b"), book("c"), book("new"))

        val ordered = applyManualOrder(books, listOf("c", "gone", "a", "b"))

        assertEquals(listOf("c", "a", "b", "new"), ordered.map { it.bookId })
    }

    @Test
    fun `moveInOrder places book before target and keeps hidden ids`() {
        val order = listOf("a", "hidden", "b", "c")
        val visible = listOf("a", "b", "c")

        val moved = moveInOrder(order, visible, movingId = "c", beforeId = "a")

        assertEquals(listOf("c", "a", "hidden", "b"), moved)
    }

    @Test
    fun `moveInOrder to end and onto itself`() {
        val visible = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), moveInOrder(emptyList(), visible, "a", null))
        assertEquals(listOf("x"), moveInOrder(listOf("x"), visible, "a", "a"))
    }

    @Test
    fun `mixed display puts only books being read on covers`() {
        val group = BookshelfGroup(
            "g",
            BookshelfGroupKind.ALL,
            null,
            listOf(book("1"), book("2"), book("3", progress = 10f), book("4"), book("5")),
        )
        val metrics = shelfMetricsFor(360f)

        val slots = buildShelfSlots(group, BookshelfDisplay.MIXED, metrics)

        assertEquals(listOf(false, false, true, false, false), slots.map { it.asCover })
        assertTrue(slots.filter { !it.asCover }.all { it.width < metrics.coverWidth / 2 })
        assertTrue(buildShelfSlots(group, BookshelfDisplay.COVERS, metrics).all { it.asCover })
        assertTrue(buildShelfSlots(group, BookshelfDisplay.SPINES, metrics).none { it.asCover })
    }

    @Test
    fun `series share a spine tint key`() {
        val group = BookshelfGroup(
            "g",
            BookshelfGroupKind.SERIES,
            "Mistborn",
            listOf(book("a", series = "Mistborn"), book("b", series = "MISTBORN"), book("c")),
        )

        val slots = buildShelfSlots(group, BookshelfDisplay.SPINES, shelfMetricsFor(360f))

        assertEquals(slots[0].tintKey, slots[1].tintKey)
        assertFalse(slots[0].tintKey == slots[2].tintKey)
    }

    @Test
    fun `packShelfRows never exceeds the row width and keeps every slot in order`() {
        val metrics = shelfMetricsFor(360f)
        val group = BookshelfGroup("g", BookshelfGroupKind.ALL, null, (1..30).map { book("id$it") })
        val slots = buildShelfSlots(group, BookshelfDisplay.MIXED, metrics)
        val rowWidth = 332f
        val gap = 3f

        val rows = packShelfRows(slots, rowWidth, gap)

        assertEquals(slots.map { it.item.bookId }, rows.flatten().map { it.item.bookId })
        rows.forEach { row ->
            val used = row.sumOf { it.width.toDouble() } + gap * (row.size - 1)
            assertTrue("row too wide: $used", used <= rowWidth + 0.01)
        }
    }

    @Test
    fun `a slot wider than the row still gets its own row`() {
        val wide = ShelfSlot(book("w"), true, 200f, 300f, "w")

        val rows = packShelfRows(listOf(wide, wide), rowWidth = 100f, gap = 3f)

        assertEquals(2, rows.size)
        assertTrue(packShelfRows(emptyList(), 100f, 3f).isEmpty())
    }

    @Test
    fun `order encoding round trips and tolerates garbage`() {
        val order = listOf("a", "b \"quoted\"", "c,d")

        assertEquals(order, decodeBookshelfOrder(encodeBookshelfOrder(order)))
        assertTrue(decodeBookshelfOrder(null).isEmpty())
        assertTrue(decodeBookshelfOrder("not json").isEmpty())
    }

    @Test
    fun `shelf metrics stay within sane bounds`() {
        assertEquals(72f, shelfMetricsFor(200f).coverWidth, 0.01f)
        assertEquals(118f, shelfMetricsFor(1200f).coverWidth, 0.01f)
        assertEquals(1.5f, shelfMetricsFor(360f).let { it.coverHeight / it.coverWidth }, 0.001f)
    }

    private fun book(
        id: String,
        title: String? = null,
        author: String? = null,
        series: String? = null,
        index: Double? = null,
        progress: Float? = null,
    ) = RecentFileItem(
        bookId = id,
        uriString = "content://$id",
        type = FileType.EPUB,
        displayName = "$id.epub",
        timestamp = 1L,
        title = title,
        author = author,
        progressPercentage = progress,
        seriesName = series,
        seriesIndex = index,
    )
}
