/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE file in the project root. This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.ashudialer.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Colours the part of [text] that was typed in a search bar - the same cue the onboarding shows - and leaves the rest
 * exactly as the caller styled it (so the caller's own Text colour still applies).
 *
 *  - A name (or any text) is matched the way it was typed: every occurrence of [query], ignoring upper/lower case.
 *  - A phone number is matched on its DIGITS, so "98765" still lights up inside "+91 98765 43210" even though the
 *    number is printed with spaces and a country code.
 *
 *  - Several typed words ("ritik rai" typed as "rai ritik") are each coloured wherever they appear.
 *
 * The coloured part is bold, in the accent colour, on a soft tint of the same colour, so it is easy to spot from the
 * first letter. With no query, or no match, the text comes back unchanged.
 */
fun highlightMatch(text: String, query: String, highlight: Color): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val style = SpanStyle(color = highlight, fontWeight = FontWeight.Bold, background = highlight.copy(alpha = 0.16f))

    // 1) the text itself contains what was typed (names, and numbers typed exactly as they are printed)
    if (text.contains(q, ignoreCase = true)) {
        return buildAnnotatedString {
            var from = 0
            while (from < text.length) {
                val at = text.indexOf(q, from, ignoreCase = true)
                if (at < 0) {
                    append(text.substring(from))
                    break
                }
                append(text.substring(from, at))
                withStyle(style) { append(text.substring(at, at + q.length)) }
                from = at + q.length
            }
        }
    }

    // 2) several typed words, in any order: colour each word wherever it shows up
    val words = q.split(' ').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (words.size > 1) {
        val hit = BooleanArray(text.length)
        var any = false
        for (w in words) {
            var from = 0
            while (from <= text.length - w.length) {
                val at = text.indexOf(w, from, ignoreCase = true)
                if (at < 0) break
                for (k in at until at + w.length) hit[k] = true
                any = true
                from = at + w.length
            }
        }
        if (any) {
            return buildAnnotatedString {
                var i = 0
                while (i < text.length) {
                    var j = i
                    while (j < text.length && hit[j] == hit[i]) j++
                    if (hit[i]) withStyle(style) { append(text.substring(i, j)) } else append(text.substring(i, j))
                    i = j
                }
            }
        }
    }

    // 3) a number: compare digits only and walk the printed text so spaces / + / - stay where they are
    val queryDigits = q.filter { it.isDigit() }
    if (queryDigits.length < 2) return AnnotatedString(text)
    val textDigits = text.filter { it.isDigit() }
    val start = textDigits.indexOf(queryDigits)
    if (start < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        var seen = 0
        for (ch in text) {
            val isDigit = ch.isDigit()
            val inMatch = isDigit && seen >= start && seen < start + queryDigits.length
            if (inMatch) withStyle(style) { append(ch) } else append(ch)
            if (isDigit) seen++
        }
    }
}
