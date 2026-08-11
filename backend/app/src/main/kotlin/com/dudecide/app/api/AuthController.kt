package com.dudecide.app.api

import com.dudecide.app.api.dto.CreateGuestSessionRequest
import com.dudecide.app.api.dto.CurrentActorResponse
import com.dudecide.app.domain.AuthSessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
class AuthController(
    private val authSessionService: AuthSessionService,
) {

    @PostMapping("/guest-sessions")
    @Operation(summary = "Create a guest-backed local session")
    fun createGuestSession(
        @RequestBody request: CreateGuestSessionRequest,
        servletRequest: HttpServletRequest,
    ): ResponseEntity<CurrentActorResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authSessionService.createGuestSession(request, servletRequest))

    @GetMapping("/me")
    @Operation(summary = "Get the current caller identity")
    fun getCurrentActor(): ResponseEntity<CurrentActorResponse> =
        ResponseEntity.ok(authSessionService.getCurrentActor())

    @DeleteMapping("/session")
    @Operation(summary = "Clear the current local session")
    fun clearSession(servletRequest: HttpServletRequest): ResponseEntity<Void> {
        authSessionService.clearSession(servletRequest)
        return ResponseEntity.noContent().build()
    }
}
