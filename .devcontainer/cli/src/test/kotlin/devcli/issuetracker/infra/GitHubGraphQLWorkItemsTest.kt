package devcli.issuetracker.infra

import com.sun.net.httpserver.HttpServer
import devcli.issuetracker.domain.BoardColumn
import devcli.issuetracker.domain.RepositorySlug
import devcli.issuetracker.domain.WorkItemBody
import devcli.issuetracker.domain.WorkItemTitle
import devcli.issuetracker.domain.WorkItemType
import java.net.InetSocketAddress
import java.net.URI
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Drives the adapter against a local stand-in for the GitHub REST and GraphQL APIs. */
class GitHubGraphQLWorkItemsTest {

    private val lanes = """{"repo": "acme/app", "project": {"owner": "acme", "number": 3}, "epicProject": {"owner": "acme", "number": 4}}"""
    private val graphqlBodies = mutableListOf<String>()
    private var lanesStatus = 200
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/repos/acme/app/contents/.github/lanes.json") { exchange ->
            val body = if (lanesStatus == 200) """{"content": "${Base64.getEncoder().encodeToString(lanes.toByteArray())}"}""" else "{}"
            exchange.respond(lanesStatus, body)
        }
        createContext("/repos/acme/app/issues") { exchange ->
            exchange.respond(201, """{"number": 7, "html_url": "https://github.com/acme/app/issues/7", "node_id": "I_7"}""")
        }
        createContext("/graphql") { exchange ->
            val request = exchange.requestBody.readBytes().decodeToString()
            graphqlBodies += request
            val number = Regex("\"number\":(\\d+)").find(request)?.groupValues?.get(1)
            val body = if (request.contains("issue(number")) {
                """{"data": {"repository": {"issue": {"id": "I_7"}}}}"""
            } else if (request.contains("projectV2(number")) {
                """{"data": {"repositoryOwner": {"projectV2": {"id": "P_$number"}}}}"""
            } else {
                """{"data": {"addProjectV2ItemById": {"item": {"id": "ITEM"}}}}"""
            }
            exchange.respond(200, body)
        }
        start()
    }

    private val base = "http://127.0.0.1:${server.address.port}"
    private val workItems = GitHubGraphQLWorkItems(
        GitHubGraphQLClient(tokenProvider = { "token" }, endpoint = URI.create("$base/graphql"), restBase = base)
    )
    private val repo = RepositorySlug.of("acme/app")

    @AfterTest
    fun stop() = server.stop(0)

    private fun create(type: WorkItemType) = workItems.create(repo, WorkItemTitle.of("Title"), WorkItemBody.of(""), type)

    @Test
    fun `an epic is added to the board lanes json names as epicProject`() {
        val epic = create(WorkItemType.EPIC)

        assertEquals(BoardColumn.BACKLOG, workItems.addToBoard(repo, epic))
        assertTrue(graphqlBodies.any { it.contains("\"projectId\":\"P_4\"") && it.contains("\"contentId\":\"I_7\"") })
    }

    @Test
    fun `a story is added to the board lanes json names as project`() {
        val story = create(WorkItemType.STORY)

        assertEquals(BoardColumn.BACKLOG, workItems.addToBoard(repo, story))
        assertTrue(graphqlBodies.any { it.contains("\"projectId\":\"P_3\"") })
    }

    @Test
    fun `a failed lanes json read is reported, not swallowed`() {
        lanesStatus = 500
        val story = create(WorkItemType.STORY)

        val error = assertFailsWith<RuntimeException> { workItems.addToBoard(repo, story) }
        assertEquals("Failed to read .github/lanes.json: HTTP 500", error.message)
    }
}

private fun com.sun.net.httpserver.HttpExchange.respond(status: Int, body: String) {
    val bytes = body.toByteArray()
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
