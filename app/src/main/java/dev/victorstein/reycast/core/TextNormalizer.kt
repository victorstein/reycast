package dev.victorstein.reycast.core

import java.text.Normalizer

class NormalizedText(val text: String, val sourceIndex: IntArray)

object TextNormalizer {
    // Latin diacritics only: \p{Mn} would also strip kana voicing marks (グ -> ク).
    private val combiningMarks = Regex("[\\u0300-\\u036F]+")

    fun normalize(input: String): NormalizedText {
        val text = StringBuilder()
        val sourceIndex = ArrayList<Int>()
        var i = 0
        while (i < input.length) {
            val codePoint = input.codePointAt(i)
            if (codePoint < 0x80) {
                text.append(codePoint.toChar().lowercaseChar())
                sourceIndex.add(i)
                i++
                continue
            }
            val folded = Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFD)
                .replace(combiningMarks, "")
                .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
                .lowercase()
            for (c in folded) {
                text.append(c)
                sourceIndex.add(i)
            }
            i += Character.charCount(codePoint)
        }
        return NormalizedText(text.toString(), sourceIndex.toIntArray())
    }
}
