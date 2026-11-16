package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLookupQueryTest {

    @Test
    fun `series suffix, extension and noise are removed from the title`() {
        val query = BookLookupQuery.from(
            book(title = "Crepúsculo - Livro 1", author = "Meyer, Stephenie"),
        )
        assertNotNull(query)
        assertEquals("Crepúsculo", query!!.title)
        assertEquals("Meyer", query.authorSurname)
    }

    @Test
    fun `file name is used when there is no title`() {
        val query = BookLookupQuery.from(
            book(title = null, author = null, displayName = "A_Morte_de_Ivan_Ilitch (z-lib.org).epub"),
        )
        assertEquals("A Morte de Ivan Ilitch", query!!.title)
        assertNull(query.authorSurname)
    }

    @Test
    fun `too short titles are not looked up`() {
        assertNull(BookLookupQuery.from(book(title = "ab", author = null, displayName = "ab.pdf")))
    }

    @Test
    fun `author given as first and last name uses the last word as surname`() {
        assertEquals("Tolstói", BookLookupQuery.from(book(title = "A Morte de Ivan Ilitch", author = "Liev Tolstói"))!!.authorSurname)
    }

    @Test
    fun `matching title and author scores high, unrelated book scores low`() {
        val query = BookLookupQuery.from(book(title = "A Morte de Ivan Ilitch", author = "Lev Tolstói"))!!
        assertTrue(query.score("A morte de Ivan Ilitch", listOf("Leo Tolstói")) >= 0.6)
        assertTrue(query.score("Guerra e Paz", listOf("Leon Tolstói")) < 0.6)
        assertTrue(query.score("A Morte de Ivan Ilitch", listOf("Outro Autor")) >= 0.6)
    }

    @Test
    fun `accents and case do not matter`() {
        val query = BookLookupQuery.from(book(title = "CREPÚSCULO", author = "meyer"))!!
        assertTrue(query.score("Crepusculo", listOf("Stephenie Meyer")) >= 0.75)
    }

    private fun book(
        title: String?,
        author: String?,
        displayName: String = "livro.epub",
    ) = RecentFileItem(
        bookId = "id",
        uriString = "content://id",
        type = FileType.EPUB,
        displayName = displayName,
        timestamp = 1L,
        title = title,
        author = author,
    )
}
