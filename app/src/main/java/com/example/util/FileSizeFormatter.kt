// File: /app/src/main/java/com/example/util/FileSizeFormatter.kt
package com.example.util

import java.util.Locale

object FileSizeFormatter {
    fun formatSize(bytes: Long): String {
        if (bytes < 0) return if (isArabic()) "٠ كيلوبايت" else "0 KB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0

        val locale = Locale.getDefault()
        val isAr = isArabic()

        val kbUnit = if (isAr) "كيلوبايت" else "KB"
        val mbUnit = if (isAr) "ميغابايت" else "MB"
        val gbUnit = if (isAr) "غيغابايت" else "GB"

        return when {
            mb < 1.0 -> {
                String.format(locale, "%.0f %s", kb, kbUnit)
            }
            mb < 1000.0 -> {
                String.format(locale, "%.0f %s", mb, mbUnit)
            }
            else -> {
                if (gb < 10.0) {
                    val formattedValue = String.format(locale, "%.2f", gb)
                    // Normalize trailing zeros for decimal representation
                    if (formattedValue.endsWith(".00") || formattedValue.endsWith("٫٠٠")) {
                        String.format(locale, "%.0f %s", gb, gbUnit)
                    } else if (formattedValue.endsWith("0") || formattedValue.endsWith("٠")) {
                        String.format(locale, "%.1f %s", gb, gbUnit)
                    } else {
                        "$formattedValue $gbUnit"
                    }
                } else {
                    String.format(locale, "%.1f %s", gb, gbUnit)
                }
            }
        }
    }

    private fun isArabic(): Boolean {
        return Locale.getDefault().language == "ar"
    }
}
