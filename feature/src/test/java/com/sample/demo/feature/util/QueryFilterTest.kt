package com.sample.demo.feature.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Plain JUnit — no Compose rule, no dispatcher, no ViewModel. That is the point of `util/`. */
class QueryFilterTest {

    private val words = listOf("Kotlin", "Java", "Kotlin Multiplatform")

    @Test
    fun `blank query returns every element`() {
        assertEquals(words, words.filterByQuery("") { it })
        assertEquals(words, words.filterByQuery("   ") { it })
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf("Kotlin", "Kotlin Multiplatform"), words.filterByQuery("kot") { it })
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertEquals(listOf("Java"), words.filterByQuery("  java  ") { it })
    }

    @Test
    fun `no match yields an empty list`() {
        assertEquals(emptyList<String>(), words.filterByQuery("rust") { it })
    }

    @Test
    fun `selector chooses the matched text`() {
        val pairs = listOf("a" to "Kotlin", "b" to "Java")

        assertEquals(listOf("a" to "Kotlin"), pairs.filterByQuery("kotlin") { it.second })
        assertEquals(emptyList<Pair<String, String>>(), pairs.filterByQuery("kotlin") { it.first })
    }
}
