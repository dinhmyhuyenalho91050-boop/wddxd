package com.example.aichat.util

/**
 * Kotlin 复刻版的 StreamB 状态机，用于在流式渲染时高亮处理
 * 1) 半角双引号包裹的文本
 * 2) 中文引号包裹的文本
 * 3) 星号包裹的文本
 *
 * 同时返回“已完成前缀”和“未闭合尾部”两段，以便 UI 追加光标效果。
 */
class StreamFormatter {
    private val prefixSegments = mutableListOf<FormattedSegment>()
    private val plain = StringBuilder()
    private val doubleQuote = StringBuilder()
    private val chineseQuote = StringBuilder()
    private val bold = StringBuilder()

    private var dqOpen = false
    private var cqOpen = false
    private var boldOpen = false

    fun feed(delta: CharSequence): FormattedContent {
        for (ch in delta) {
            when {
                ch == '\"' && !cqOpen && !boldOpen -> {
                    if (dqOpen) closeDoubleQuote() else openDoubleQuote()
                    continue
                }

                ch == '“' && !dqOpen && !boldOpen -> {
                    if (!cqOpen) openChineseQuote() else chineseQuote.append(ch)
                    continue
                }

                ch == '”' && cqOpen -> {
                    closeChineseQuote()
                    continue
                }

                (ch == '*' || ch.code == 0xFF0A) && !dqOpen && !cqOpen -> {
                    if (boldOpen) closeBold() else openBold()
                    continue
                }

                else -> appendChar(ch)
            }
        }
        return snapshot()
    }

    fun snapshot(): FormattedContent = FormattedContent(prefixSegments.toList(), buildTail())

    fun fullText(): String = buildString {
        appendSegments(prefixSegments)
        appendSegments(buildTail())
    }

    private fun buildTail(): List<FormattedSegment> {
        val tail = mutableListOf<FormattedSegment>()
        if (dqOpen) {
            tail += FormattedSegment(text = "\"${doubleQuote}", style = SegmentStyle.DOUBLE_QUOTE, incomplete = true)
        }
        if (cqOpen) {
            tail += FormattedSegment(text = "“${chineseQuote}", style = SegmentStyle.CHINESE_QUOTE, incomplete = true)
        }
        if (boldOpen) {
            tail += FormattedSegment(text = "*", style = SegmentStyle.PLAIN, incomplete = true)
            if (bold.isNotEmpty()) {
                tail += FormattedSegment(text = bold.toString(), style = SegmentStyle.BOLD, incomplete = true)
            }
        }
        if (plain.isNotEmpty()) {
            tail += FormattedSegment(text = plain.toString(), style = SegmentStyle.PLAIN)
        }
        return tail
    }

    private fun appendChar(ch: Char) {
        when {
            dqOpen -> doubleQuote.append(ch)
            cqOpen -> chineseQuote.append(ch)
            boldOpen -> bold.append(ch)
            else -> plain.append(ch)
        }
    }

    private fun openDoubleQuote() {
        commitPlain()
        dqOpen = true
    }

    private fun closeDoubleQuote() {
        prefixSegments += FormattedSegment(text = "\"${doubleQuote}\"", style = SegmentStyle.DOUBLE_QUOTE)
        doubleQuote.clear()
        dqOpen = false
    }

    private fun openChineseQuote() {
        commitPlain()
        cqOpen = true
    }

    private fun closeChineseQuote() {
        prefixSegments += FormattedSegment(text = "“${chineseQuote}”", style = SegmentStyle.CHINESE_QUOTE)
        chineseQuote.clear()
        cqOpen = false
    }

    private fun openBold() {
        commitPlain()
        boldOpen = true
    }

    private fun closeBold() {
        prefixSegments += FormattedSegment(text = bold.toString(), style = SegmentStyle.BOLD)
        bold.clear()
        boldOpen = false
    }

    private fun commitPlain() {
        if (plain.isNotEmpty()) {
            prefixSegments += FormattedSegment(text = plain.toString(), style = SegmentStyle.PLAIN)
            plain.clear()
        }
    }

    companion object {
        fun formatAll(text: String): FormattedContent {
            val formatter = StreamFormatter()
            if (text.isNotEmpty()) {
                formatter.feed(text)
            }
            return formatter.snapshot()
        }
    }
}

private fun StringBuilder.appendSegments(segments: List<FormattedSegment>) {
    segments.forEach { append(it.text) }
}

enum class SegmentStyle { PLAIN, DOUBLE_QUOTE, CHINESE_QUOTE, BOLD }

data class FormattedSegment(
    val text: String,
    val style: SegmentStyle,
    val incomplete: Boolean = false
)

data class FormattedContent(
    val prefix: List<FormattedSegment>,
    val tail: List<FormattedSegment>
) {
    fun isBlank(): Boolean = prefix.all { it.text.isBlank() } && tail.all { it.text.isBlank() }

    companion object {
        val Empty = FormattedContent(emptyList(), emptyList())
    }
}
