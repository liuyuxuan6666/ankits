package com.example.ankits

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.graphics.Typeface

enum class SectionType { HERO, SUB, H3, BODY, BLANK, CODE, QUOTE, LIST_ITEM, HR }

data class MarkdownSection(
    val type: SectionType,
    val title: CharSequence?,
    val body: SpannableStringBuilder,
    val meta: String = ""
)

object MarkdownParser {

    fun parse(raw: String): List<MarkdownSection> {
        if (raw.isEmpty()) return emptyList()

        val sections = mutableListOf<MarkdownSection>()
        val lines = raw.split("\n")
        var i = 0
        var blankCount = 0
        val bodyBuffer = mutableListOf<String>()

        fun flushBody() {
            if (bodyBuffer.isNotEmpty()) {
                val text = bodyBuffer.joinToString("\n")
                sections.add(MarkdownSection(SectionType.BODY, null, parseInline(text)))
                bodyBuffer.clear()
            }
        }

        fun flushBlanks() {
            repeat(blankCount) {
                sections.add(MarkdownSection(SectionType.BLANK, null, SpannableStringBuilder()))
            }
            blankCount = 0
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Fenced code block
            if (trimmed.startsWith("```")) {
                flushBody()
                flushBlanks()
                val lang = trimmed.removePrefix("```").trim()
                i++
                val codeLines = mutableListOf<String>()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                val sb = SpannableStringBuilder(codeLines.joinToString("\n"))
                sections.add(MarkdownSection(SectionType.CODE, null, sb, lang))
                if (i < lines.size) i++ // skip closing ```
                continue
            }

            // Blank line
            if (trimmed.isEmpty()) {
                flushBody()
                blankCount++
                i++
                continue
            }

            // Heading (# through ######)
            val headingMatch = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
            if (headingMatch != null) {
                flushBody()
                flushBlanks()
                val level = headingMatch.groupValues[1].length
                val titleText = parseInline(headingMatch.groupValues[2])
                val type = when (level) {
                    1 -> SectionType.HERO
                    2 -> SectionType.SUB
                    else -> SectionType.H3
                }
                sections.add(MarkdownSection(type, titleText, SpannableStringBuilder(), level.toString()))
                i++
                continue
            }

            // Horizontal rule: ---, ***, ___ (3+ chars, nothing else on line)
            if (trimmed.matches(Regex("^(-{3,}|\\*{3,}|_{3,})\\s*$"))) {
                flushBody()
                flushBlanks()
                sections.add(MarkdownSection(SectionType.HR, null, SpannableStringBuilder()))
                i++
                continue
            }

            // Blockquote
            if (trimmed.startsWith("> ") || trimmed == ">") {
                flushBody()
                flushBlanks()
                val quoteContent = if (trimmed == ">") "" else trimmed.removePrefix("> ").trim()
                sections.add(MarkdownSection(SectionType.QUOTE, null, parseInline(quoteContent)))
                i++
                continue
            }

            // Unordered list: -, *, +
            val ulMatch = Regex("^([-*+])\\s+(.+)$").find(trimmed)
            if (ulMatch != null) {
                flushBody()
                flushBlanks()
                val itemBody = parseInline(ulMatch.groupValues[2])
                sections.add(MarkdownSection(SectionType.LIST_ITEM, null, itemBody, "\u2022"))
                i++
                continue
            }

            // Ordered list: 1. or 1)
            val olMatch = Regex("^(\\d+)[.)]\\s+(.+)$").find(trimmed)
            if (olMatch != null) {
                flushBody()
                flushBlanks()
                val num = olMatch.groupValues[1]
                val itemBody = parseInline(olMatch.groupValues[2])
                sections.add(MarkdownSection(SectionType.LIST_ITEM, null, itemBody, "$num."))
                i++
                continue
            }

            // Normal text line — accumulate into paragraph buffer
            flushBlanks()
            bodyBuffer.add(trimmed)
            i++
        }

        flushBody()
        flushBlanks()

        if (sections.isEmpty()) return emptyList()
        return sections
    }

    private fun parseInline(text: String): SpannableStringBuilder {
        val result = SpannableStringBuilder()
        var i = 0

        while (i < text.length) {
            when {
                // Bold: **text** (check before single *)
                i + 1 < text.length && text[i] == '*' && text[i + 1] == '*' -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i + 2) {
                        val inner = parseInline(text.substring(i + 2, end))
                        val start = result.length
                        result.append(inner)
                        result.setSpan(StyleSpan(Typeface.BOLD), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else {
                        result.append(text[i])
                        i++
                    }
                }
                // Italic: *text*
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        val inner = parseInline(text.substring(i + 1, end))
                        val start = result.length
                        result.append(inner)
                        result.setSpan(StyleSpan(Typeface.ITALIC), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        result.append(text[i])
                        i++
                    }
                }
                // Strikethrough: ~~text~~
                i + 1 < text.length && text[i] == '~' && text[i + 1] == '~' -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        val inner = parseInline(text.substring(i + 2, end))
                        val start = result.length
                        result.append(inner)
                        result.setSpan(StrikethroughSpan(), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else {
                        result.append(text[i])
                        i++
                    }
                }
                // Inline code: `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        val start = result.length
                        result.append(text.substring(i + 1, end))
                        result.setSpan(TypefaceSpan("monospace"), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        result.append(text[i])
                        i++
                    }
                }
                // Link: [text](url)
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket > i + 1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket + 2) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val start = result.length
                            result.append(linkText)
                            result.setSpan(StyleSpan(Typeface.ITALIC), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = closeParen + 1
                        } else {
                            result.append(text[i])
                            i++
                        }
                    } else {
                        result.append(text[i])
                        i++
                    }
                }
                else -> {
                    result.append(text[i])
                    i++
                }
            }
        }

        return result
    }
}
