package devcli.issuetracker

import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.testing.test
import devcli.issuetracker.api.CommentResponseDto
import devcli.issuetracker.api.IssueTrackerCommand
import devcli.issuetracker.api.ErrorDto
import devcli.issuetracker.api.JsonFormat
import devcli.issuetracker.api.WorkItemDto
import devcli.issuetracker.app.AddCommentUseCase
import devcli.issuetracker.app.CreateWorkItemUseCase
import devcli.issuetracker.app.GetWorkItemUseCase
import devcli.issuetracker.app.UpdateWorkItemColumnUseCase
import devcli.issuetracker.domain.CommentBody
import devcli.issuetracker.domain.BoardColumn
import devcli.issuetracker.domain.RepositorySlug
import devcli.issuetracker.domain.WorkItem
import devcli.issuetracker.domain.WorkItemBody
import devcli.issuetracker.domain.WorkItemId
import devcli.issuetracker.domain.WorkItemTitle
import devcli.issuetracker.domain.WorkItemType
import devcli.issuetracker.domain.WorkItems
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InMemoryWorkItems : WorkItems {
    private val items = mutableMapOf<Long, WorkItem>()
    private var sequence = 1L
    val comments = mutableListOf<Pair<Long, String>>()
    var boardFailure: String? = null
    var hasBoard = true

    override fun findById(repo: RepositorySlug, id: WorkItemId): WorkItem? = items[id.value]

    override fun create(
        repo: RepositorySlug,
        title: WorkItemTitle,
        body: WorkItemBody,
        type: WorkItemType
    ): WorkItem {
        val id = WorkItemId.of(sequence++)
        val item = WorkItem(id, title, body, type, null, "https://github.com/${repo.value}/issues/${id.value}")
        items[id.value] = item
        return item
    }

    override fun addToBoard(repo: RepositorySlug, item: WorkItem): BoardColumn? {
        boardFailure?.let { throw IllegalStateException(it) }
        if (!hasBoard) return null
        items[item.id.value] = WorkItem(item.id, item.title, item.body, item.type, BoardColumn.BACKLOG, item.url)
        return BoardColumn.BACKLOG
    }

    override fun updateColumn(
        repo: RepositorySlug,
        id: WorkItemId,
        column: BoardColumn
    ): WorkItem {
        val existing = items[id.value] ?: throw NoSuchElementException("WorkItem #${id.value} not found")
        val updated = WorkItem(existing.id, existing.title, existing.body, existing.type, column, existing.url)
        items[id.value] = updated
        return updated
    }

    override fun addComment(
        repo: RepositorySlug,
        id: WorkItemId,
        comment: CommentBody
    ): String {
        comments.add(id.value to comment.value)
        return "https://github.com/${repo.value}/issues/${id.value}#issuecomment-999"
    }
}

class WorkItemTest {

    @Test
    fun `WorkItemId validates positive numbers`() {
        assertEquals(42L, WorkItemId.of(42).value)
        assertEquals(10L, WorkItemId.of("10").value)
        assertFailsWith<IllegalArgumentException> { WorkItemId.of(0) }
        assertFailsWith<IllegalArgumentException> { WorkItemId.of(-5) }
    }

    @Test
    fun `WorkItemTitle rejects blank input`() {
        assertEquals("Valid Title", WorkItemTitle.of("  Valid Title  ").value)
        assertFailsWith<IllegalArgumentException> { WorkItemTitle.of("   ") }
    }

    @Test
    fun `WorkItemType parses standard labels`() {
        assertEquals(WorkItemType.FEATURE, WorkItemType.of("feature"))
        assertEquals(WorkItemType.FEATURE, WorkItemType.of("type:feature"))
        assertEquals(WorkItemType.BUG, WorkItemType.of("BUG"))
        assertEquals(WorkItemType.EPIC, WorkItemType.of("type:epic"))
        assertFailsWith<IllegalArgumentException> { WorkItemType.of("unknown") }
    }

    @Test
    fun `BoardColumn parses normalized names`() {
        assertEquals(BoardColumn.BACKLOG, BoardColumn.of("Backlog"))
        assertEquals(BoardColumn.TODO, BoardColumn.of("todo"))
        assertEquals(BoardColumn.IN_PROGRESS, BoardColumn.of("In progress"))
        assertEquals(BoardColumn.IN_PROGRESS, BoardColumn.of("in-progress"))
        assertEquals(BoardColumn.IN_PROGRESS, BoardColumn.of("IN_PROGRESS"))
        assertEquals(BoardColumn.REVIEW, BoardColumn.of(" Review "))
        assertEquals(BoardColumn.DONE, BoardColumn.of("done"))
        assertFailsWith<IllegalArgumentException> { BoardColumn.of("04 Execute") }
    }

    @Test
    fun `set-column rejects an unknown column with exit code 1`() {
        val command = IssueTrackerCommand(InMemoryWorkItems())
        val result = assertFailsWith<ProgramResult> {
            command.parse(listOf("set-column", "1", "--column", "04-execute", "--repo", "owner/repo"))
        }
        assertEquals(1, result.statusCode)
    }

    @Test
    fun `RepositorySlug parses owner and name`() {
        val slug = RepositorySlug.of("FilipKrawiec/devcontainer")
        assertEquals("FilipKrawiec", slug.owner)
        assertEquals("devcontainer", slug.name)
        assertEquals("FilipKrawiec/devcontainer", slug.value)
        assertFailsWith<IllegalArgumentException> { RepositorySlug.of("invalid_slug") }
    }

    @Test
    fun `CreateWorkItemUseCase creates item in the Backlog column`() {
        val workItems = InMemoryWorkItems()
        val useCase = CreateWorkItemUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val outcome = useCase.execute(repo, WorkItemTitle.of("Test Issue"), WorkItemBody.of("Body text"), WorkItemType.FEATURE)
        assertIs<CreateWorkItemUseCase.Outcome.Success>(outcome)
        assertEquals(1L, outcome.workItem.id.value)
        assertEquals("Test Issue", outcome.workItem.title.value)
        assertEquals(BoardColumn.BACKLOG, outcome.workItem.column)
    }

    @Test
    fun `UpdateWorkItemColumnUseCase moves the item to the column`() {
        val workItems = InMemoryWorkItems()
        val createUseCase = CreateWorkItemUseCase(workItems)
        val updateUseCase = UpdateWorkItemColumnUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val created = (createUseCase.execute(repo, WorkItemTitle.of("Test"), WorkItemBody.of(""), WorkItemType.BUG) as CreateWorkItemUseCase.Outcome.Success).workItem

        val outcome = updateUseCase.execute(repo, created.id, BoardColumn.IN_PROGRESS)
        assertIs<UpdateWorkItemColumnUseCase.Outcome.Success>(outcome)
        assertEquals(BoardColumn.IN_PROGRESS, outcome.workItem.column)
    }

    @Test
    fun `CreateWorkItemUseCase keeps the issue and warns when it cannot reach a board`() {
        val workItems = InMemoryWorkItems().apply { boardFailure = "HTTP 500 reading .github/lanes.json" }
        val repo = RepositorySlug.of("acme/app")

        val outcome = CreateWorkItemUseCase(workItems).execute(repo, WorkItemTitle.of("Epic"), WorkItemBody.of(""), WorkItemType.EPIC)

        assertIs<CreateWorkItemUseCase.Outcome.Success>(outcome)
        assertEquals(null, outcome.workItem.column)
        assertEquals("Not added to a board: HTTP 500 reading .github/lanes.json", outcome.boardWarning)
    }

    @Test
    fun `CreateWorkItemUseCase warns when no board is configured for the type`() {
        val workItems = InMemoryWorkItems().apply { hasBoard = false }

        val outcome = CreateWorkItemUseCase(workItems).execute(RepositorySlug.of("acme/app"), WorkItemTitle.of("Epic"), WorkItemBody.of(""), WorkItemType.EPIC)

        assertIs<CreateWorkItemUseCase.Outcome.Success>(outcome)
        assertEquals("Not added to a board: no epic board is configured", outcome.boardWarning)
    }

    @Test
    fun `create prints the board warning on stderr and still exits 0`() {
        val command = IssueTrackerCommand(InMemoryWorkItems().apply { boardFailure = "HTTP 500" })

        val result = command.test(listOf("create", "--title", "Epic", "--type", "epic", "--repo", "acme/app"))

        assertEquals(0, result.statusCode)
        assertEquals("⚠ Not added to a board: HTTP 500\n", result.stderr)
        assertTrue(result.stdout.startsWith("✔ Created epic #1: Epic"))
    }

    @Test
    fun `create puts the board warning in its JSON output`() {
        val command = IssueTrackerCommand(InMemoryWorkItems().apply { boardFailure = "HTTP 500" })

        val result = command.test(listOf("create", "--title", "Epic", "--type", "epic", "--repo", "acme/app", "--json"))

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("\"boardWarning\": \"Not added to a board: HTTP 500\""))
        assertTrue(result.stdout.contains("\"column\": null"))
    }

    @Test
    fun `UpdateWorkItemColumnUseCase rejects Todo and Review for an epic`() {
        val workItems = InMemoryWorkItems()
        val repo = RepositorySlug.of("acme/app")
        val epic = (CreateWorkItemUseCase(workItems).execute(repo, WorkItemTitle.of("Epic"), WorkItemBody.of(""), WorkItemType.EPIC)
            as CreateWorkItemUseCase.Outcome.Success).workItem
        val update = UpdateWorkItemColumnUseCase(workItems)

        val rejected = update.execute(repo, epic.id, BoardColumn.TODO)
        assertIs<UpdateWorkItemColumnUseCase.Outcome.Failure>(rejected)
        assertEquals("The epic board has only Backlog, In progress and Done; got Todo", rejected.message)
        assertIs<UpdateWorkItemColumnUseCase.Outcome.Failure>(update.execute(repo, epic.id, BoardColumn.REVIEW))
        assertEquals(BoardColumn.DONE, (update.execute(repo, epic.id, BoardColumn.DONE) as UpdateWorkItemColumnUseCase.Outcome.Success).workItem.column)
    }

    @Test
    fun `UpdateWorkItemColumnUseCase returns NotFound for missing item`() {
        val workItems = InMemoryWorkItems()
        val updateUseCase = UpdateWorkItemColumnUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val outcome = updateUseCase.execute(repo, WorkItemId.of(999), BoardColumn.TODO)
        assertIs<UpdateWorkItemColumnUseCase.Outcome.NotFound>(outcome)
    }

    @Test
    fun `GetWorkItemUseCase finds item by id`() {
        val workItems = InMemoryWorkItems()
        val createUseCase = CreateWorkItemUseCase(workItems)
        val getUseCase = GetWorkItemUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        createUseCase.execute(repo, WorkItemTitle.of("Item 1"), WorkItemBody.of("Desc"), WorkItemType.TASK)

        val outcome = getUseCase.execute(repo, WorkItemId.of(1))
        assertIs<GetWorkItemUseCase.Outcome.Success>(outcome)
        assertEquals("Item 1", outcome.workItem.title.value)
    }

    @Test
    fun `AddCommentUseCase records comment on work item`() {
        val workItems = InMemoryWorkItems()
        val useCase = AddCommentUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val outcome = useCase.execute(repo, WorkItemId.of(12), CommentBody.of("Refined spec notes"))
        assertIs<AddCommentUseCase.Outcome.Success>(outcome)
        assertTrue(outcome.commentUrl.contains("#issuecomment-999"))
        assertEquals(1, workItems.comments.size)
        assertEquals(12L to "Refined spec notes", workItems.comments.first())
    }

    @Test
    fun `Json serialization converts DTOs to valid JSON`() {
        val dto = WorkItemDto(12L, "Title", "Body", "feature", "Todo", "https://github.com/owner/repo/issues/12")
        val json = JsonFormat.toJson(dto)
        assertTrue(json.contains("\"id\": 12"))
        assertTrue(json.contains("\"column\": \"Todo\""))

        val err = ErrorDto("Something went wrong")
        val errJson = JsonFormat.toJson(err)
        assertTrue(errJson.contains("\"error\": \"Something went wrong\""))
    }
}
