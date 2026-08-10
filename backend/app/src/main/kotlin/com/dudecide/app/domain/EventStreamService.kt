package com.dudecide.app.domain

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class EventStreamService(
    private val objectMapper: ObjectMapper,
) {
    private val emitters: MutableMap<UUID, CopyOnWriteArrayList<SseEmitter>> = ConcurrentHashMap()

    fun open(meetingId: UUID): SseEmitter {
        val emitter = SseEmitter(0L)
        val meetingEmitters = emitters.computeIfAbsent(meetingId) { CopyOnWriteArrayList() }
        meetingEmitters.add(emitter)

        emitter.onCompletion { meetingEmitters.remove(emitter) }
        emitter.onTimeout { meetingEmitters.remove(emitter) }
        emitter.onError { meetingEmitters.remove(emitter) }

        send(
            emitter = emitter,
            eventType = "meeting.updated",
            payload = mapOf(
                "meetingId" to meetingId,
                "connectedAt" to OffsetDateTime.now().toString(),
            ),
        )

        return emitter
    }

    fun publish(meetingId: UUID, eventType: String, payload: Map<String, Any?>) {
        val meetingEmitters = emitters[meetingId] ?: return
        meetingEmitters.toList().forEach { emitter ->
            send(emitter, eventType, payload)
        }
    }

    private fun send(emitter: SseEmitter, eventType: String, payload: Map<String, Any?>) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name(eventType)
                    .data(objectMapper.writeValueAsString(payload)),
            )
        } catch (_: Exception) {
            emitter.complete()
        }
    }
}
