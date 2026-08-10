package com.dudecide.app.api

import com.dudecide.app.api.dto.SearchResponse
import com.dudecide.app.domain.SearchService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/meetings/{meetingId}/search")
@Tag(name = "Search")
class SearchController(
    private val searchService: SearchService,
) {

    @GetMapping
    @Operation(summary = "Search meeting messages, decisions, and tasks")
    fun search(
        @PathVariable meetingId: UUID,
        @RequestParam q: String,
    ): ResponseEntity<SearchResponse> =
        ResponseEntity.ok(searchService.search(meetingId, q))
}
