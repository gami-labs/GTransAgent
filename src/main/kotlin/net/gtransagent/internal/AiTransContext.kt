package net.gtransagent.internal

import org.slf4j.LoggerFactory

/**
 * Utility for building translation context from surrounding items.
 * Helps LLM translators maintain consistency by providing neighboring text as context.
 * Ported and simplified from the reference implementation.
 */
object AiTransContext {
    private val log = LoggerFactory.getLogger(AiTransContext::class.java)

    // Context window size: max number of neighboring items to include
    private const val CONTEXT_WINDOW_SIZE = 4

    // Max context characters for CJK languages
    private const val MAX_CONTEXT_CHARS_CJK = 80
    // Max context characters for non-CJK languages
    private const val MAX_CONTEXT_CHARS_NON_CJK = 160
    // Max context words for non-CJK languages
    private const val MAX_CONTEXT_WORDS_NON_CJK = 30

    // CJK character regex (Chinese, Japanese, Korean)
    private val CJK_REGEX = Regex("[\\u4E00-\\u9FFF\\u3040-\\u30FF\\uAC00-\\uD7AF]")

    /**
     * Check if text contains CJK characters
     */
    fun containsCJK(text: String): Boolean {
        return CJK_REGEX.containsMatchIn(text)
    }

    /**
     * Truncate context text to fit within limits
     * @param text the text to truncate
     * @param isCJK whether to use CJK character limits
     * @return truncated text
     */
    fun truncateContext(text: String, isCJK: Boolean): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        if (isCJK) {
            return if (trimmed.length <= MAX_CONTEXT_CHARS_CJK) {
                trimmed
            } else {
                trimmed.take(MAX_CONTEXT_CHARS_CJK)
            }
        } else {
            val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }

            if (trimmed.length <= MAX_CONTEXT_CHARS_NON_CJK && words.size <= MAX_CONTEXT_WORDS_NON_CJK) {
                return trimmed
            }

            val result = StringBuilder()
            var wordCount = 0
            for (word in words) {
                val newLength = if (result.isEmpty()) word.length else result.length + 1 + word.length
                if (newLength > MAX_CONTEXT_CHARS_NON_CJK || wordCount >= MAX_CONTEXT_WORDS_NON_CJK) {
                    break
                }
                if (result.isNotEmpty()) result.append(" ")
                result.append(word)
                wordCount++
            }
            return result.toString()
        }
    }

    /**
     * Build context strings from surrounding items in the input list.
     * @param targetIndex the index of the current item being translated
     * @param records the full list of input texts
     * @param buildPrevious true to build context from items before targetIndex, false for items after
     * @return list of truncated context strings
     */
    fun buildContext(
        targetIndex: Int,
        records: List<String>,
        buildPrevious: Boolean
    ): List<String> {
        if (records.isEmpty() || targetIndex < 0 || targetIndex >= records.size) {
            return emptyList()
        }
        val targetRecord = records[targetIndex]
        val targetIsCJK = containsCJK(targetRecord)

        val minContextChars =
            if (targetIsCJK) MAX_CONTEXT_CHARS_CJK / 2 else MAX_CONTEXT_CHARS_NON_CJK / 2

        val contextParts = mutableListOf<String>()
        for (offset in 1..CONTEXT_WINDOW_SIZE) {
            val idx = if (buildPrevious) {
                targetIndex - offset
            } else {
                targetIndex + offset
            }

            if ((buildPrevious && idx >= 0) || (!buildPrevious && idx < records.size)) {
                val contextText = records[idx]
                val isCJK = containsCJK(contextText) || targetIsCJK
                val truncated = truncateContext(contextText, isCJK)
                if (truncated.isNotEmpty()) {
                    contextParts.add(0, truncated)
                    if (truncated.length >= minContextChars) {
                        break
                    }
                }
            }
        }
        return contextParts
    }
}
