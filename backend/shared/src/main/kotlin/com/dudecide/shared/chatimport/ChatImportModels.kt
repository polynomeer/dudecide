package com.dudecide.shared.chatimport

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

data class ImportedMessage(
    val id: String,
    val speaker: String,
    val sentAt: Instant,
    val body: String,
)

data class PrefilterResult(
    val kept: List<ImportedMessage>,
    val dropped: List<ImportedMessage>,
)

data class Episode(
    val index: Int,
    val messages: List<ImportedMessage>,
    val filteredCount: Int,
) {
    val startAt: Instant get() = messages.first().sentAt
    val endAt: Instant get() = messages.last().sentAt
}

data class SegmentationConfig(
    val zone: ZoneId = ZoneId.of("Asia/Seoul"),
    val hardGap: Duration = Duration.ofHours(3),
    val dayChangeGap: Duration = Duration.ofHours(1),
    val maxEpisodeSize: Int = 150,
    val minEpisodeSize: Int = 5,
) {
    init {
        require(minEpisodeSize >= 1) { "minEpisodeSize must be positive" }
        require(maxEpisodeSize >= minEpisodeSize * 2) { "maxEpisodeSize must be at least twice minEpisodeSize" }
    }
}

enum class SubjectEventKind {
    PROPOSAL,
    SUPPORT,
    OPPOSITION,
    CONDITION,
    CONFIRMATION,
    WITHDRAWAL,
}

data class SubjectEvent(
    val messageId: String,
    val speaker: String,
    val kind: SubjectEventKind,
    val option: String?,
    val sentAt: Instant,
    val confidence: Double,
)

data class Subject(
    val subjectKey: String,
    val decisionType: String,
    val title: String,
    val events: List<SubjectEvent>,
)

enum class SubjectStatus {
    LIKELY_DECIDED,
    CONTESTED,
    OPEN,
    ABANDONED,
}

data class OptionAssessment(
    val option: String,
    val supporterCount: Int,
    val objectorCount: Int,
    val superseded: Boolean,
)

data class SubjectAssessment(
    val subjectKey: String,
    val decisionType: String,
    val title: String,
    val status: SubjectStatus,
    val leadingOption: String?,
    val options: List<OptionAssessment>,
    val evidenceMessageIds: List<String>,
    val objectionMessageIds: List<String>,
    val confidence: Double,
)
