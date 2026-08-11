package com.sample.demo.core.data.model

/**
 * Domain model for a post.
 *
 * Shaped by what the app renders, not by any wire format — when `:core:network` lands, its
 * `PostDto` is mapped into this type in `mapper/`, and every nullable wire field is resolved
 * there so this model has none.
 */
data class Post(
    val id: Int,
    val title: String,
    val body: String,
)
