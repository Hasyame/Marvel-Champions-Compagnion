package com.hasyame.marvelchampions.ui.util

/**
 * MarvelCDB card text carries a little HTML (`<b>`, `<i>`, `<p>`) and `[star]`
 * style icon placeholders.
 *
 * For now the markup is stripped rather than rendered. Turning it into styled
 * text and mapping the icon placeholders onto the game's glyph font is a
 * polish-milestone job; showing raw tags to the user is not acceptable in the
 * meantime.
 */
private val htmlTag = Regex("<[^>]*>")

fun stripHtml(input: String): String = htmlTag
    .replace(input, "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .lineSequence()
    .joinToString("\n") { it.trimEnd() }
    .trim()
