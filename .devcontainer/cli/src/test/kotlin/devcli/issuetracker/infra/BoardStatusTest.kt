package devcli.issuetracker.infra

import devcli.issuetracker.domain.BoardColumn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardStatusTest {

    private fun issueWithStatus(option: String?): JsonObject {
        val fieldValues = if (option == null) "[]" else
            """[{"name": "$option", "field": {"name": "Status"}}]"""
        return Json.parseToJsonElement(
            """{"projectItems": {"nodes": [{"fieldValues": {"nodes": $fieldValues}}]}}"""
        ).jsonObject
    }

    private val issueOnBoard = Json.parseToJsonElement(
        """
        {"projectItems": {"nodes": [{
          "id": "item-1",
          "project": {"id": "project-1", "fields": {"nodes": [
            {"id": "field-title", "name": "Title"},
            {"id": "field-status", "name": "Status", "options": [
              {"id": "opt-backlog", "name": "Backlog"},
              {"id": "opt-todo", "name": "Todo"},
              {"id": "opt-progress", "name": "In progress"},
              {"id": "opt-review", "name": "Review"},
              {"id": "opt-done", "name": "Done"}
            ]}
          ]}}
        }]}}
        """
    ).jsonObject

    @Test
    fun `statusColumn reads each board column`() {
        assertEquals(BoardColumn.BACKLOG, statusColumn(issueWithStatus("Backlog")))
        assertEquals(BoardColumn.TODO, statusColumn(issueWithStatus("Todo")))
        assertEquals(BoardColumn.IN_PROGRESS, statusColumn(issueWithStatus("In progress")))
        assertEquals(BoardColumn.REVIEW, statusColumn(issueWithStatus("Review")))
        assertEquals(BoardColumn.DONE, statusColumn(issueWithStatus("Done")))
    }

    @Test
    fun `statusColumn is null for an unknown option or an item without Status`() {
        assertNull(statusColumn(issueWithStatus("04 Execute")))
        assertNull(statusColumn(issueWithStatus(null)))
        assertNull(statusColumn(Json.parseToJsonElement("""{"projectItems": {"nodes": []}}""").jsonObject))
    }

    @Test
    fun `statusTargets picks the matching Status option`() {
        assertEquals(
            listOf(StatusTarget("project-1", "item-1", "field-status", "opt-progress")),
            statusTargets(issueOnBoard, BoardColumn.IN_PROGRESS)
        )
    }

    @Test
    fun `statusTargets is empty when the board lacks the column or the issue is not on a board`() {
        val legacyBoard = Json.parseToJsonElement(
            issueOnBoard.toString().replace("\"Review\"", "\"05 Review\"")
        ).jsonObject
        assertTrue(statusTargets(legacyBoard, BoardColumn.REVIEW).isEmpty())
        assertTrue(statusTargets(Json.parseToJsonElement("""{"projectItems": {"nodes": []}}""").jsonObject, BoardColumn.TODO).isEmpty())
    }
}
