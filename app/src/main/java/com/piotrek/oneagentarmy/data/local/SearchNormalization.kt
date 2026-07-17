package com.piotrek.oneagentarmy.data.local

import java.text.Normalizer

private val COMBINING_MARKS = Regex("\\p{Mn}+")

// Lowercases and strips diacritics so "głowa", "Glowa" and "GŁOWA" all compare equal.
// NFD decomposition handles most accented letters; stroked letters like ł don't
// decompose in Unicode and need explicit mapping.
fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('ł', 'l')
