package com.verse.explorer.core.coroutine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TaskState {
    IDLE, RUNNING, SUCCEEDED, FAILED, CANCELLED
}

data class TaskProgress(
    val title: String = "",
    val message: String = "",
    val progress: Float = 0f,
    val state: TaskState = TaskState.IDLE
)

abstract class Task<T>(
    val title: String,
    val description: String = ""
) {
    private val _progress = MutableStateFlow(TaskProgress(title = title, state = TaskState.IDLE))
    val progress: StateFlow<TaskProgress> = _progress.asStateFlow()

    protected suspend fun updateProgress(message: String, progress: Float) {
        _progress.value = TaskProgress(
            title = title,
            message = message,
            progress = progress.coerceIn(0f, 1f),
            state = TaskState.RUNNING
        )
    }

    protected suspend fun succeed(result: T) {
        _progress.value = TaskProgress(
            title = title,
            message = "完成",
            progress = 1f,
            state = TaskState.SUCCEEDED
        )
    }

    protected suspend fun fail(error: String) {
        _progress.value = TaskProgress(
            title = title,
            message = error,
            progress = 0f,
            state = TaskState.FAILED
        )
    }

    abstract suspend fun execute(): T
}

class TaskFlowExecutor {
    private val _tasks = MutableStateFlow<List<TaskProgress>>(emptyList())
    val tasks: StateFlow<List<TaskProgress>> = _tasks.asStateFlow()

    private val _currentTask = MutableStateFlow<TaskProgress?>(null)
    val currentTask: StateFlow<TaskProgress?> = _currentTask.asStateFlow()

    suspend fun <T> execute(task: Task<T>): Result<T> {
        return try {
            val progress = TaskProgress(title = task.title, state = TaskState.RUNNING)
            _currentTask.value = progress
            _tasks.value = _tasks.value + progress

            task.progress.collect { p ->
                _currentTask.value = p
                _tasks.value = _tasks.value.map { if (it.title == task.title) p else it }
            }

            val result = task.execute()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
