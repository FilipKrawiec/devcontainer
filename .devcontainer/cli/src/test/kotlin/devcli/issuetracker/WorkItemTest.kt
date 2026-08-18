package devcli.issuetracker

import devcli.issuetracker.api.CommentResponseDto
import devcli.issuetracker.api.ErrorDto
import devcli.issuetracker.api.JsonFormat
import devcli.issuetracker.api.WorkItemDto
import devcli.issuetracker.app.AddCommentUseCase
import devcli.issuetracker.app.CreateWorkItemUseCase
import devcli.issuetracker.app.GetWorkItemUseCase
import devcli.issuetracker.app.UpdateWorkItemPhaseUseCase
import devcli.issuetracker.domain.CommentBody
import devcli.issuetracker.domain.DeliveryPhase
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

    override fun findById(repo: RepositorySlug, id: WorkItemId): WorkItem? = items[id.value]

    override fun create(
        repo: RepositorySlug,
        title: WorkItemTitle,
        body: WorkItemBody,
        type: WorkItemType
    ): WorkItem {
        val id = WorkItemId.of(sequence++)
        val item = WorkItem(id, title, body, type, DeliveryPhase.DEFINE, "https://github.com/${repo.value}/issues/${id.value}")
        items[id.value] = item
        return item
    }

    override fun updatePhase(
        repo: RepositorySlug,
        id: WorkItemId,
        phase: DeliveryPhase
    ): WorkItem {
        val existing = items[id.value] ?: throw NoSuchElementException("WorkItem #${id.value} not found")
        val updated = WorkItem(existing.id, existing.title, existing.body, existing.type, phase, existing.url)
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
        assertFailsWith<IllegalArgumentException> { WorkItemType.of("unknown") }
    }

    @Test
    fun `DeliveryPhase parses normalized names`() {
        assertEquals(DeliveryPhase.DEFINE, DeliveryPhase.of("01 Define"))
        assertEquals(DeliveryPhase.SPEC, DeliveryPhase.of("02-spec"))
        assertEquals(DeliveryPhase.PLAN, DeliveryPhase.of("plan"))
        assertEquals(DeliveryPhase.EXECUTE, DeliveryPhase.of("04 execute"))
        assertEquals(DeliveryPhase.REVIEW, DeliveryPhase.of("review"))
        assertEquals(DeliveryPhase.SHIP, DeliveryPhase.of("ship"))
        assertEquals(DeliveryPhase.IMPROVE, DeliveryPhase.of("improve"))
        assertFailsWith<IllegalArgumentException> { DeliveryPhase.of("invalid-phase") }
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
    fun `CreateWorkItemUseCase creates item on backlog in Define phase`() {
        val workItems = InMemoryWorkItems()
        val useCase = CreateWorkItemUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val outcome = useCase.execute(repo, WorkItemTitle.of("Test Issue"), WorkItemBody.of("Body text"), WorkItemType.FEATURE)
        assertIs<CreateWorkItemUseCase.Outcome.Success>(outcome)
        assertEquals(1L, outcome.workItem.id.value)
        assertEquals("Test Issue", outcome.workItem.title.value)
        assertEquals(DeliveryPhase.DEFINE, outcome.workItem.phase)
    }

    @Test
    fun `UpdateWorkItemPhaseUseCase advances phase successfully`() {
        val workItems = InMemoryWorkItems()
        val createUseCase = CreateWorkItemUseCase(workItems)
        val updateUseCase = UpdateWorkItemPhaseUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val created = (createUseCase.execute(repo, WorkItemTitle.of("Test"), WorkItemBody.of(""), WorkItemType.BUG) as CreateWorkItemUseCase.Outcome.Success).workItem

        val outcome = updateUseCase.execute(repo, created.id, DeliveryPhase.EXECUTE)
        assertIs<UpdateWorkItemPhaseUseCase.Outcome.Success>(outcome)
        assertEquals(DeliveryPhase.EXECUTE, outcome.workItem.phase)
    }

    @Test
    fun `UpdateWorkItemPhaseUseCase returns NotFound for missing item`() {
        val workItems = InMemoryWorkItems()
        val updateUseCase = UpdateWorkItemPhaseUseCase(workItems)
        val repo = RepositorySlug.of("FilipKrawiec/devcontainer")

        val outcome = updateUseCase.execute(repo, WorkItemId.of(999), DeliveryPhase.SPEC)
        assertIs<UpdateWorkItemPhaseUseCase.Outcome.NotFound>(outcome)
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
        val dto = WorkItemDto(12L, "Title", "Body", "feature", "02 Spec", "https://github.com/owner/repo/issues/12")
        val json = JsonFormat.toJson(dto)
        assertTrue(json.contains("\"id\": 12"))
        assertTrue(json.contains("\"phase\": \"02 Spec\""))

        val err = ErrorDto("Something went wrong")
        val errJson = JsonFormat.toJson(err)
        assertTrue(errJson.contains("\"error\": \"Something went wrong\""))
    }
}
