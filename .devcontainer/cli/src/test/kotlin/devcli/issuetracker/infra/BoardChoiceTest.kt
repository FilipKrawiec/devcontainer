package devcli.issuetracker.infra

import devcli.issuetracker.domain.BoardKind
import devcli.issuetracker.domain.WorkItemType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.util.Base64

class BoardChoiceTest {

    private val lanes = Json.parseToJsonElement(
        """{"repo": "acme/app", "project": {"owner": "acme", "number": 3}, "epicProject": {"owner": "acme-org", "number": 4}}"""
    ).jsonObject

    private val workflow = Board("p-workflow", "Workflow")
    private val epics = Board("p-epics", "Epics")
    private val notes = Board("p-notes", "Notes")

    @Test
    fun `lanes json names the epic board for epics and the issue board for the rest`() {
        assertEquals(BoardRef("acme-org", 4), configuredBoard(WorkItemType.EPIC.board, lanes))
        assertEquals(BoardRef("acme", 3), configuredBoard(WorkItemType.STORY.board, lanes))
        assertEquals(BoardRef("acme", 3), configuredBoard(WorkItemType.BUG.board, lanes))
    }

    @Test
    fun `lanes json without a board for the type names none`() {
        val issueBoardOnly = Json.parseToJsonElement("""{"repo": "acme/app", "project": {"owner": "acme", "number": 3}}""").jsonObject
        assertNull(configuredBoard(WorkItemType.EPIC.board, issueBoardOnly))
    }

    @Test
    fun `without lanes json epics go to the board titled Epic`() {
        assertEquals(epics, boardByTitle(BoardKind.EPIC, listOf(notes, workflow, epics)))
        assertNull(boardByTitle(BoardKind.EPIC, listOf(notes, workflow, Board("p-food", "Epicurean recipes"))))
    }

    @Test
    fun `without lanes json other issues go to a Workflow board, else the first board that is not for epics`() {
        assertEquals(workflow, boardByTitle(BoardKind.ISSUE, listOf(epics, notes, workflow)))
        assertEquals(notes, boardByTitle(BoardKind.ISSUE, listOf(epics, notes)))
        assertNull(boardByTitle(BoardKind.ISSUE, listOf(epics)))
    }

    @Test
    fun `lanes json with a malformed board names none`() {
        val malformed = Json.parseToJsonElement(
            """{"project": {"owner": null, "number": 3}, "epicProject": {"owner": "acme", "number": "four"}}"""
        ).jsonObject
        assertNull(configuredBoard(BoardKind.ISSUE, malformed))
        assertNull(configuredBoard(BoardKind.EPIC, malformed))
    }

    @Test
    fun `decodeLanes reads line-wrapped base64 content`() {
        val encoded = Base64.getMimeEncoder(8, "\n".toByteArray()).encodeToString(
            """{"repo": "acme/app", "epicProject": {"owner": "acme", "number": 4}}""".toByteArray()
        )
        val body = """{"name": "lanes.json", "content": "${encoded.replace("\n", "\\n")}"}"""
        assertEquals(BoardRef("acme", 4), configuredBoard(BoardKind.EPIC, decodeLanes(body)!!))
        assertNull(decodeLanes("""{"name": "lanes.json"}"""))
    }
}
