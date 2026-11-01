package com.dudecide.app.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Rate limits for live decision suggestions, which call the configured LLM provider.
 * See docs/engineering/decision-suggestions.md for the cost policy.
 */
@ConfigurationProperties("dudecide.suggestions")
data class DecisionSuggestionProperties(
    val enabled: Boolean = true,
    val quietMinutes: Long = 3,
    val minNewMessages: Int = 4,
    val windowMessages: Int = 60,
    val dailyPerMeeting: Int = 20,
    val maxPendingPerThread: Int = 3,
)

@Configuration
@EnableConfigurationProperties(DecisionSuggestionProperties::class)
class DecisionSuggestionConfig
