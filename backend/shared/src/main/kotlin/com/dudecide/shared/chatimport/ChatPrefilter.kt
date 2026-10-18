package com.dudecide.shared.chatimport

/**
 * Drops messages that carry no planning signal before they reach the LLM.
 * Short stance reactions ("ㅇㅇ", "응", "콜") are kept because they count as support or opposition.
 */
class ChatPrefilter {

    fun filter(messages: List<ImportedMessage>): PrefilterResult {
        val (dropped, kept) = messages.partition { isNoise(it.body) }
        return PrefilterResult(kept = kept, dropped = dropped)
    }

    fun isNoise(body: String): Boolean {
        val compact = body.filterNot { it.isWhitespace() }
        val letters = compact.filter { it.isLetterOrDigit() }.lowercase()

        if (letters in STANCE_TOKENS) return false
        if (letters.isEmpty()) return true
        if (compact.codePointCount(0, compact.length) < MIN_VISIBLE_CHARS) return true
        return LAUGHTER.matches(letters)
    }

    private companion object {
        const val MIN_VISIBLE_CHARS = 2

        val STANCE_TOKENS = setOf(
            "ㅇ", "ㅇㅇ", "ㅇㅋ", "ㅇㅋㅇㅋ", "응", "웅", "네", "넵", "넹", "예", "콜", "ㄱㄱ", "고고",
            "좋아", "좋아요", "좋음", "찬성", "오케이", "ok", "okay", "yes", "agree",
            "ㄴㄴ", "노노", "싫어", "싫어요", "반대", "no", "nope",
        )

        val LAUGHTER = Regex("^(?:[ㅋㅎㅠㅜㄷㄲ]|하|허|히|크|키|흑|ha|ah|he|lol|lmao)+$")
    }
}
