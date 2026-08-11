package com.sample.demo.feature.util

/**
 * Filters [this] by a user-typed [query], comparing against the text returned by [selector].
 *
 * A blank query matches everything; matching is case-insensitive and ignores surrounding
 * whitespace.
 *
 * Deliberately free of Compose and Android types — this is shared logic, not a UI component, so
 * it is testable as plain Kotlin.
 *
 * @param query the raw text the user typed.
 * @param selector the text to match each element on.
 */
fun <T> List<T>.filterByQuery(query: String, selector: (T) -> String): List<T> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { selector(it).contains(trimmed, ignoreCase = true) }
}
