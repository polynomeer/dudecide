package com.dudecide.app.domain.chatimport

import com.dudecide.shared.chatimport.SubjectEventKind

/**
 * Deterministic keyword-based stand-in for the LLM, for local development and tests.
 * It only needs to produce plausible structure, not good analysis.
 */
class FakeLlmClient : LlmClient {

    override val modelVersions: Map<String, String> = mapOf("map" to "fake-map-v1", "reduce" to "fake-reduce-v1")

    override fun mapEpisode(request: EpisodeMapRequest): EpisodeMapResult {
        val subjectEvents = linkedMapOf<String, MutableList<LocalSubjectEvent>>()
        val labels = mutableListOf<MessageLabel>()
        val questions = mutableListOf<ExtractedQuestion>()
        val tasks = mutableListOf<ExtractedTask>()
        var currentType: String? = null

        request.messages.forEachIndexed { index, message ->
            val text = message.body.trim()
            val lower = text.lowercase()
            val dates = DATE.findAll(text).map { it.value.replace(" ", "") }.toList()
            val type = detectType(lower, dates.isNotEmpty())
            if (type != null) currentType = type
            val targetType = type ?: currentType
            val kind = detectKind(lower)
            val intents = mutableListOf<String>()

            if (kind != null && targetType != null) {
                subjectEvents.getOrPut(targetType) { mutableListOf() } += LocalSubjectEvent(
                    messageId = message.id,
                    speaker = message.speaker,
                    kind = kind,
                    option = extractOption(targetType, text, dates),
                    confidence = CONFIDENCE,
                )
                intents += kind.name.lowercase()
            }
            if (TASK_MARKERS.any { it in lower }) {
                tasks += ExtractedTask(message.id, text.take(200), acceptedBy = message.speaker)
                intents += "task"
            }
            if (text.endsWith("?") && kind != SubjectEventKind.PROPOSAL) {
                val answeredBy = request.messages.drop(index + 1).take(3).firstOrNull { it.speaker != message.speaker }?.speaker
                questions += ExtractedQuestion(message.id, answeredBy)
                intents += "question"
            }

            labels += MessageLabel(
                id = message.id,
                topics = listOf(targetType ?: "free_chat"),
                intents = intents.ifEmpty { listOf("chatter") },
                entities = if (dates.isEmpty()) emptyMap() else mapOf("dates" to dates),
            )
        }

        return EpisodeMapResult(
            messages = labels,
            subjects = subjectEvents.map { (type, events) ->
                LocalSubject("${request.episodeIndex}-$type", type, TITLES.getValue(type), events)
            },
            questions = questions,
            tasks = tasks,
        )
    }

    override fun reduceSubjects(request: SubjectReduceRequest): SubjectReduceResult =
        SubjectReduceResult(
            request.subjects.groupBy { it.decisionType }.map { (type, subjects) ->
                MergedSubject(
                    subjectKey = type,
                    decisionType = type,
                    title = subjects.first().title,
                    events = subjects.flatMap { it.events },
                    mergedFrom = subjects.map { it.localKey },
                )
            },
        )

    private fun detectType(lower: String, hasDate: Boolean): String? = when {
        hasDate -> "date"
        LODGING.any { it in lower } -> "lodging"
        TRANSPORT.any { it in lower } -> "transport"
        BUDGET.containsMatchIn(lower) -> "budget"
        LOCATION.any { it in lower } -> "location"
        DATE_WORDS.any { it in lower } -> "date"
        else -> null
    }

    private fun detectKind(lower: String): SubjectEventKind? {
        val tokens = lower.split(NON_WORD).filter { it.isNotEmpty() }.toSet()
        return when {
            CONFIRMATION.any { it in lower } -> SubjectEventKind.CONFIRMATION
            OPPOSITION.any { it in lower } || tokens.any { it in OPPOSITION_TOKENS } -> SubjectEventKind.OPPOSITION
            SUPPORT.any { it in lower } || tokens.any { it in SUPPORT_TOKENS } -> SubjectEventKind.SUPPORT
            PROPOSAL.any { it in lower } -> SubjectEventKind.PROPOSAL
            CONDITION.any { it in lower } -> SubjectEventKind.CONDITION
            else -> null
        }
    }

    private fun extractOption(type: String, text: String, dates: List<String>): String? {
        if (type == "date") return dates.firstOrNull()
        val match = OPTION_PATTERNS.firstNotNullOfOrNull { it.find(text) } ?: return null
        return match.groupValues[1].trim().trimEnd('?', '.', '!').removeSuffixes().takeIf { it.isNotEmpty() }?.take(40)
    }

    private fun String.removeSuffixes(): String =
        PARTICLES.firstOrNull { endsWith(it) && length > it.length }?.let { dropLast(it.length) } ?: this

    private companion object {
        const val CONFIDENCE = 0.8

        val TITLES = mapOf(
            "date" to "Date",
            "location" to "Location",
            "lodging" to "Lodging",
            "transport" to "Transport",
            "budget" to "Budget",
        )

        val DATE = Regex(
            "\\d{1,2}\\s*/\\s*\\d{1,2}|\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일|\\d{1,2}\\s*일|\\b\\d{1,2}(?:st|nd|rd|th)\\b",
            RegexOption.IGNORE_CASE,
        )
        val BUDGET = Regex("\\d+\\s*(?:만원|원)|budget|예산")
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

        val DATE_WORDS = listOf("날짜", "언제", "주말", "date", "weekend")
        val LODGING = listOf("숙소", "호텔", "펜션", "캠핑장", "에어비앤비", "lodging", "hotel", "airbnb")
        val TRANSPORT = listOf("렌트", "렌터카", "기차", "ktx", "버스", "비행기", "배편", "ferry", "car", "train", "flight")
        val LOCATION = listOf("어디", "장소", "지역", "location", "where", "place")

        val CONFIRMATION = listOf("확정", "결정", "그걸로 하자", "그걸로 해", "confirmed", "decided", "go with", "final")
        val OPPOSITION = listOf("ㄴㄴ", "싫어", "반대", "별로", "안돼", "안 돼", "disagree")
        val OPPOSITION_TOKENS = setOf("no", "nope")
        val SUPPORT = listOf("ㅇㅇ", "ㅇㅋ", "좋아", "좋다", "찬성", "sounds good")
        val SUPPORT_TOKENS = setOf("콜", "ok", "okay", "yes", "agree")
        val PROPOSAL = listOf("어때", "할까", "가자", "how about", "what about", "let's", "shall we")
        val CONDITION = listOf("만약", "if ", "unless")
        val TASK_MARKERS = listOf("할게", "제가 할", "내가 할", "i'll", "i will")

        val OPTION_PATTERNS = listOf(
            Regex("(?i)go with (?:the )?(.+)$"),
            Regex("(?i)(?:how|what) about (.+)$"),
            Regex("^(.+?)\\s*(?:어때|할까|가자|로 하자|으로 하자)"),
        )
        val PARTICLES = listOf("으로", "로", "은", "는", "이", "가")
    }
}
