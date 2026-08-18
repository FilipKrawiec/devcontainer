package devcli.issuetracker.domain

interface WorkItems {
    fun findById(repo: RepositorySlug, id: WorkItemId): WorkItem?
    fun create(repo: RepositorySlug, title: WorkItemTitle, body: WorkItemBody, type: WorkItemType): WorkItem
    fun updatePhase(repo: RepositorySlug, id: WorkItemId, phase: DeliveryPhase): WorkItem
    fun addComment(repo: RepositorySlug, id: WorkItemId, comment: CommentBody): String
}
