package devcli.issuetracker.domain

interface WorkItems {
    fun findById(repo: RepositorySlug, id: WorkItemId): WorkItem?
    fun create(repo: RepositorySlug, title: WorkItemTitle, body: WorkItemBody, type: WorkItemType): WorkItem
    /** Puts the item on the board for its type; null when no board is configured for it. */
    fun addToBoard(repo: RepositorySlug, item: WorkItem): BoardColumn?
    fun updateColumn(repo: RepositorySlug, id: WorkItemId, column: BoardColumn): WorkItem
    fun addComment(repo: RepositorySlug, id: WorkItemId, comment: CommentBody): String
}
