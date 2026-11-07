package com.dudecide.app.config

import com.dudecide.app.ratelimit.FixedWindowRateLimiter
import com.dudecide.app.ratelimit.RateLimitInterceptor
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Limits are per app instance; a shared store is only needed once several instances run. */
@ConfigurationProperties("dudecide.rate-limits")
data class RateLimitProperties(
    val enabled: Boolean = true,
    val guestSessionsPerHour: Int = 30,
    val inviteJoinsPerHour: Int = 30,
    val telemetryPerHour: Int = 120,
    val messagesPerMinute: Int = 60,
)

@Configuration
@EnableConfigurationProperties(RateLimitProperties::class)
class RateLimitConfig(
    private val properties: RateLimitProperties,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        if (properties.enabled) {
            registry.addInterceptor(RateLimitInterceptor(properties, FixedWindowRateLimiter()))
                .addPathPatterns(RateLimitInterceptor.PATH_PATTERNS)
        }
    }
}
