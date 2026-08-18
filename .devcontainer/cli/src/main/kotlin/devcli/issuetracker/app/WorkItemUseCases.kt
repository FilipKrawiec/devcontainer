package devcli.issuetracker.app

import devcli.issuetracker.domain.CommentBody
import devcli.issuetracker.domain.DeliveryPhase
import devcli.issuetracker.domain.RepositorySlug
import devcli.issuetracker.domain.WorkItem
import devcli.issuetracker.domain.WorkItemBody
import devcli.issuetracker.domain.WorkItemId
import devcli.issuetracker.domain.WorkItemTitle
import devcli.issuetracker.domain.WorkItemType
import devcli.issuetracker.domain.WorkItems

class CreateWorkItemUseCase(private val workItems: WorkItems) {
    sealed interface Outcome {
        data class Success(val workItem: WorkItem) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, title: WorkItemTitle, body: WorkItemBody, type: WorkItemType): Outcome {
        return try {
            val item = workItems.create(repo, title, body, type)
            Outcome.Success(item)
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to create work item")
        }
    }
}

class UpdateWorkItemPhaseUseCase(private val workItems: WorkItems) {
    sealed interface Outcome {
        data class Success(val workItem: WorkItem) : Outcome
        data class NotFound(val message: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun execute(repo: RepositorySlug, id: WorkItemId, phase: DeliveryPhase): Outcome {
        return try {
            val item = workItems.updatePhase(repo, id, phase)
            Outcome.Success(item)
        } catch (e: NoSuchElementException) {
            Outcome.NotFound(e.message ?: "Work item #$id not found")
        } catch (e: Exception) {
            Outcome.Failure(e.message ?: "Failed to update phase")
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
