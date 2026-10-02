package devcli.issuetracker.domain

interface WorkItems {
    fun findById(repo: RepositorySlug, id: WorkItemId): WorkItem?
    fun create(repo: RepositorySlug, title: WorkItemTitle, body: WorkItemBody, type: WorkItemType): WorkItem
    fun updateColumn(repo: RepositorySlug, id: WorkItemId, column: BoardColumn): WorkItem
    fun addComment(repo: RepositorySlug, id: WorkItemId, comment: CommentBody): String
}
