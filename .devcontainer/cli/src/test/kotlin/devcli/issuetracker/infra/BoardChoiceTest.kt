package devcli.issuetracker.infra

import devcli.issuetracker.domain.WorkItemType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardChoiceTest {

    private val lanes = Json.parseToJsonElement(
        """{"repo": "acme/app", "project": {"owner": "acme", "number": 3}, "epicProject": {"owner": "acme-org", "number": 4}}"""
    ).jsonObject

    private val workflow = Board("p-workflow", "Workflow")
    private val epics = Board("p-epics", "Epics")
    private val notes = Board("p-notes", "Notes")

    @Test
    fun `lanes json names the epic board for epics and the issue board for the rest`() {
        assertEquals(BoardRef("acme-org", 4), configuredBoard(WorkItemType.EPIC, lanes))
        assertEquals(BoardRef("acme", 3), configuredBoard(WorkItemType.STORY, lanes))
        assertEquals(BoardRef("acme", 3), configuredBoard(WorkItemType.BUG, lanes))
    }

    @Test
    fun `lanes json without a board for the type names none`() {
        val issueBoardOnly = Json.parseToJsonElement("""{"repo": "acme/app", "project": {"owner": "acme", "number": 3}}""").jsonObject
        assertNull(configuredBoard(WorkItemType.EPIC, issueBoardOnly))
    }

    @Test
    fun `without lanes json epics go to the board titled Epic`() {
        assertEquals(epics, boardByTitle(WorkItemType.EPIC, listOf(notes, workflow, epics)))
        assertNull(boardByTitle(WorkItemType.EPIC, listOf(notes, workflow)))
    }

    @Test
    fun `without lanes json other issues go to a Workflow board, else the first board that is not for epics`() {
        assertEquals(workflow, boardByTitle(WorkItemType.TASK, listOf(epics, notes, workflow)))
        assertEquals(notes, boardByTitle(WorkItemType.TASK, listOf(epics, notes)))
        assertNull(boardByTitle(WorkItemType.TASK, listOf(epics)))
    }
}
