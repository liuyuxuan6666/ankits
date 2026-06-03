package com.example.ankits

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import java.util.regex.Pattern

enum class SectionType { HERO, SUB, MAIN, BODY }

data class MarkdownSection(
    val type: SectionType,
    val title: CharSequence?,
    val body: SpannableStringBuilder
)

object MarkdownParser {

    fun parse(raw: String): List<MarkdownSection> {
        if (raw.isBlank()) return emptyList()

        val sections = mutableListOf<MarkdownSection>()
        val paragraphs = raw.split(Regex("\\n{2,}")).filter { it.isNotBlank() }

        for (para in paragraphs) {
            val trimmed = para.trim()
            when {
                trimmed.startsWith("# ") -> {
                    val title = parseInline(trimmed.removePrefix("# ").trim())
                    sections.add(MarkdownSection(SectionType.HERO, title, SpannableStringBuilder()))
                }
                trimmed.startsWith("## ") -> {
                    val title = parseInline(trimmed.removePrefix("## ").trim())
                    sections.add(MarkdownSection(SectionType.SUB, title, SpannableStringBuilder()))
                }
                else -> {
                    val last = sections.lastOrNull()
                    if (last != null && last.type == SectionType.BODY) {
                        last.body.append("\n\n")
                        last.body.append(parseInline(trimmed))
                    } else {
                        sections.add(MarkdownSection(SectionType.BODY, null, parseInline(trimmed)))
                    }
                }
            }
        }

        return sections
    }

    private fun parseInline(text: String): SpannableStringBuilder {
        val result = SpannableStringBuilder()

        val pattern = Pattern.compile("(\\*\\*(.+?)\\*\\*)|(\\*(.+?)\\*)|(~~(.+?)~~)")
        val matcher = pattern.matcher(text)

        var last = 0
        while (matcher.find()) {
            if (matcher.start() > last) {
                result.append(text.substring(last, matcher.start()))
            }

            when {
                matcher.group(1) != null -> {
                    val start = result.length
                    result.append(matcher.group(2) ?: "")
                    result.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start, result.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                matcher.group(3) != null -> {
                    val start = result.length
                    result.append(matcher.group(4) ?: "")
                    result.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        start, result.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                matcher.group(5) != null -> {
                    val start = result.length
                    result.append(matcher.group(6) ?: "")
                    result.setSpan(
                        StrikethroughSpan(),
                        start, result.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }

            last = matcher.end()
        }

        if (last < text.length) {
            result.append(text.substring(last))
        }

        if (result.isEmpty()) {
            result.append(text)
        }

        return result
    }
}
