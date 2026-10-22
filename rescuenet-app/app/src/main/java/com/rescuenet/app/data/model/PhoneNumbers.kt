package com.rescuenet.app.data.model

/**
 * Phone-number cleaning. Mirrors the backend's `normalize_phone` exactly (7–15 digits, optional
 * leading "+", spaces/dashes/dots/brackets ignored), so a number the app accepts is a number the
 * server accepts, and the other way round.
 */
object PhoneNumbers {
    private val separators = Regex("[\\s\\-.()]")
    private val shape = Regex("^\\+?[0-9]+$")

    /** "+92 300-123 4567" -> "+923001234567". Null if it isn't a plausible phone number. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.trim().replace(separators, "")
        if (!shape.matches(cleaned)) return null
        val digits = cleaned.removePrefix("+")
        return if (digits.length in 7..15) cleaned else null
    }
}
