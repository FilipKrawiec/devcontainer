package devcli.issuetracker.app

import devcli.issuetracker.domain.CommentBody
import devcli.issuetracker.domain.BoardColumn
import devcli.issuetracker.domain.RepositorySlug
import devcli.issuetracker.domain.WorkItem
import devcli.issuetracker.domain.WorkItemBody
import devcli.issuetracker.domain.WorkItemId
import devcli.issuetracker.domain.WorkItemTitle
import devcli.issuetracker.domain.WorkItemType
import devcli.issuetracker.domain.WorkItems

class CreateWorkItemUseCase(private val workItems: WorkItems) {
    sealed interface Outcome {
        /** [boardWarning] says why the created item is on no board, when adding it failed. */
        data class Success(val workItem: WorkItem, val boardWarning: String? = null) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, title: WorkItemTitle, body: WorkItemBody, type: WorkItemType): Outcome {
        val item = try {
            workItems.create(repo, title, body, type)
        } catch (e: Exception) {
            return Outcome.Failure(e.message ?: "Failed to create work item")
        }
        return try {
            val column = workItems.addToBoard(repo, item)
                ?: return Outcome.Success(item, "Not added to a board: no ${item.type.board.displayName} is configured")
            Outcome.Success(WorkItem(item.id, item.title, item.body, item.type, column, item.url))
        } catch (e: Exception) {
            Outcome.Success(item, "Not added to a board: ${e.message ?: e::class.simpleName}")
        }
    }
}

class UpdateWorkItemColumnUseCase(private val workItems: WorkItems) {
    sealed interface Outcome {
        data class Success(val workItem: WorkItem) : Outcome
        data class NotFound(val message: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, id: WorkItemId, column: BoardColumn): Outcome {
        return try {
            val current = workItems.findById(repo, id) ?: return Outcome.NotFound("Work item #${id.value} not found in $repo")
            if (!current.type.board.allows(column)) return Outcome.Failure(current.type.board.refusal(column))
            val item = workItems.updateColumn(repo, id, column)
            Outcome.Success(item)
        } catch (e: NoSuchElementException) {
            Outcome.NotFound(e.message ?: "Work item #$id not found")
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to update column")
        }
    }
}

class GetWorkItemUseCase(private val workItems: WorkItems) {
    sealed interface Outcome {
        data class Success(val workItem: WorkItem) : Outcome
        data class NotFound(val message: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, id: WorkItemId): Outcome {
        return try {
            val item = workItems.findById(repo, id)
            if (item != null) Outcome.Success(item) else Outcome.NotFound("Work item #$id not found in $repo")
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to get work item")
        }
    }
}

class AddCommentUseCase(private val workItems: WorkItems) {
    sealed interface Outcome {
        data class Success(val commentUrl: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, id: WorkItemId, comment: CommentBody): Outcome {
        return try {
            val url = workItems.addComment(repo, id, comment)
            Outcome.Success(url)
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to add comment")
        }
    }
}
