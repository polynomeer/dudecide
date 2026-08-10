package com.dudecide.app.api

import com.dudecide.app.api.dto.CreateTaskRequest
import com.dudecide.app.api.dto.TaskView
import com.dudecide.app.api.dto.UpdateTaskRequest
import com.dudecide.app.domain.TaskService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Tasks")
class TaskController(
    private val taskService: TaskService,
) {

    @GetMapping("/meetings/{meetingId}/tasks")
    @Operation(summary = "List meeting tasks")
    fun listTasks(@PathVariable meetingId: UUID): ResponseEntity<List<TaskView>> =
        ResponseEntity.ok(taskService.listTasks(meetingId))

    @PostMapping("/meetings/{meetingId}/tasks")
    @Operation(summary = "Create a meeting task")
    fun createTask(
        @PathVariable meetingId: UUID,
        @RequestBody request: CreateTaskRequest,
    ): ResponseEntity<TaskView> =
        ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(meetingId, request))

    @PatchMapping("/tasks/{taskId}")
    @Operation(summary = "Update a task")
    fun updateTask(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateTaskRequest,
    ): ResponseEntity<TaskView> =
        ResponseEntity.ok(taskService.updateTask(taskId, request))
}
