package devcli.issuetracker.infra

import devcli.issuetracker.domain.CommentBody
import devcli.issuetracker.domain.BoardColumn
import devcli.issuetracker.domain.RepositorySlug
import devcli.issuetracker.domain.WorkItem
import devcli.issuetracker.domain.WorkItemBody
import devcli.issuetracker.domain.WorkItemId
import devcli.issuetracker.domain.WorkItemTitle
import devcli.issuetracker.domain.WorkItemType
import devcli.issuetracker.domain.WorkItems
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64

class GitHubGraphQLWorkItems(
    private val client: GitHubGraphQLClient = GitHubGraphQLClient()
) : WorkItems {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun findById(repo: RepositorySlug, id: WorkItemId): WorkItem? {
        val query = """
            query GetIssue(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                issue(number: ${'$'}number) {
                  id
                  number
                  title
                  body
                  url
                  labels(first: 10) {
                    nodes {
                      name
                    }
                  }
                  projectItems(first: 5) {
                    nodes {
                      id
                      project {
                        id
                        title
                      }
                      fieldValues(first: 10) {
                        nodes {
                          ... on ProjectV2ItemFieldSingleSelectValue {
                            name
                            field {
                              ... on ProjectV2FieldCommon {
                                name
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("owner", repo.owner)
            put("repo", repo.name)
            put("number", id.value.toInt())
        }

        val data = client.execute(query, variables)
        val issueJson = data["repository"]?.jsonObject?.get("issue")?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject ?: return null

        val number = issueJson["number"]?.jsonPrimitive?.long ?: id.value
        val title = issueJson["title"]?.jsonPrimitive?.content ?: ""
        val body = issueJson["body"]?.jsonPrimitive?.content ?: ""
        val url = issueJson["url"]?.jsonPrimitive?.content ?: ""

        val labelNodes = issueJson["labels"]?.jsonObject?.get("nodes")?.jsonArray ?: emptyList()
        val typeLabel = labelNodes.firstNotNullOfOrNull { node ->
            val name = node.jsonObject["name"]?.jsonPrimitive?.content ?: ""
            try { WorkItemType.of(name) } catch (_: Exception) { null }
        } ?: WorkItemType.FEATURE

        return WorkItem(
            id = WorkItemId.of(number),
            title = WorkItemTitle.of(title.ifBlank { "Untitled" }),
            body = WorkItemBody.of(body),
            type = typeLabel,
            column = statusColumn(issueJson),
            url = url
        )
    }

    override fun create(repo: RepositorySlug, title: WorkItemTitle, body: WorkItemBody, type: WorkItemType): WorkItem {
        val createUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/issues"
        val payload = buildJsonObject {
            put("title", title.value)
            put("body", body.value)
            put("labels", buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(type.label))
                add(kotlinx.serialization.json.JsonPrimitive("orchestrated"))
            })
        }

        val response = client.executeRest(createUrl, "POST", payload.toString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to create issue on GitHub: HTTP ${response.statusCode()} - ${response.body()}")
        }

        val resJson = json.parseToJsonElement(response.body()).jsonObject
        val issueNumber = resJson["number"]?.jsonPrimitive?.long ?: throw RuntimeException("Issue number missing in response")
        val issueUrl = resJson["html_url"]?.jsonPrimitive?.content ?: ""
        val issueNodeId = resJson["node_id"]?.jsonPrimitive?.content ?: ""

        // Add the issue to its board (epics to the epic board, the rest to the issue board), which files it in Backlog
        val column = try {
            if (addIssueToBoard(repo, type, issueNodeId)) BoardColumn.BACKLOG else null
        } catch (_: Exception) {
            // Non-fatal if project board integration is unavailable
            null
        }

        return WorkItem(
            id = WorkItemId.of(issueNumber),
            title = title,
            body = body,
            type = type,
            column = column,
            url = issueUrl
        )
    }

    override fun updateColumn(repo: RepositorySlug, id: WorkItemId, column: BoardColumn): WorkItem {
        val query = """
            query GetProjectAndItem(${'$'}owner: String!, ${'$'}repo: String!, ${'$'}number: Int!) {
              repository(owner: ${'$'}owner, name: ${'$'}repo) {
                issue(number: ${'$'}number) {
                  id
                  title
                  body
                  url
                  projectItems(first: 5) {
                    nodes {
                      id
                      project {
                        id
                        fields(first: 30) {
                          nodes {
                            ... on ProjectV2SingleSelectField {
                              id
                              name
                              options {
                                id
                                name
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val variables = buildJsonObject {
            put("owner", repo.owner)
            put("repo", repo.name)
            put("number", id.value.toInt())
        }

        val data = client.execute(query, variables)
        val issueJson = data["repository"]?.jsonObject?.get("issue")?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject
            ?: throw NoSuchElementException("Work item #$id not found in $repo")

        val targets = statusTargets(issueJson, column)
        if (targets.isEmpty()) {
            throw IllegalStateException("Work item #$id is on no board whose Status field has a '${column.displayName}' option")
        }
        for (target in targets) {
            setProjectItemFieldValue(target.projectId, target.itemId, target.fieldId, target.optionId)
        }

        return findById(repo, id) ?: WorkItem(
            id = id,
            title = WorkItemTitle.of(issueJson["title"]?.jsonPrimitive?.content ?: "Issue #$id"),
            body = WorkItemBody.of(issueJson["body"]?.jsonPrimitive?.content ?: ""),
            type = WorkItemType.FEATURE,
            column = column,
            url = issueJson["url"]?.jsonPrimitive?.content
        )
    }

    override fun addComment(repo: RepositorySlug, id: WorkItemId, comment: CommentBody): String {
        val commentUrl = "https://api.github.com/repos/${repo.owner}/${repo.name}/issues/${id.value}/comments"
        val payload = buildJsonObject {
            put("body", comment.value)
        }
        val response = client.executeRest(commentUrl, "POST", payload.toString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to add comment: HTTP ${response.statusCode()} - ${response.body()}")
        }
        val resJson = json.parseToJsonElement(response.body()).jsonObject
        return resJson["html_url"]?.jsonPrimitive?.content ?: "Comment posted"
    }

    /** Adds the issue to the board for [type]; false when there is none. */
    private fun addIssueToBoard(repo: RepositorySlug, type: WorkItemType, issueNodeId: String): Boolean {
        val lanes = readLanesConfig(repo)
        val projectId = if (lanes != null) {
            configuredBoard(type, lanes)?.let { projectIdOf(it) }
        } else {
            boardByTitle(type, userBoards(repo.owner))?.id
        } ?: return false

        val mutation = """
            mutation AddItem(${'$'}projectId: ID!, ${'$'}contentId: ID!) {
              addProjectV2ItemById(input: { projectId: ${'$'}projectId, contentId: ${'$'}contentId }) {
                item {
                  id
                }
              }
            }
        """.trimIndent()

        client.execute(mutation, buildJsonObject {
            put("projectId", projectId)
            put("contentId", issueNodeId)
        })
        return true
    }

    /** The repository's `.github/lanes.json`, or null when it has none. */
    private fun readLanesConfig(repo: RepositorySlug): JsonObject? {
        val response = client.executeRest("https://api.github.com/repos/${repo.owner}/${repo.name}/contents/.github/lanes.json")
        if (response.statusCode() == 404) return null
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Failed to read .github/lanes.json: HTTP ${response.statusCode()}")
        }
        val encoded = json.parseToJsonElement(response.body()).jsonObject["content"]?.jsonPrimitive?.content ?: return null
        val text = String(Base64.getMimeDecoder().decode(encoded))
        return json.parseToJsonElement(text).jsonObject
    }

    private fun projectIdOf(board: BoardRef): String? {
        val query = """
            query GetProject(${'$'}login: String!, ${'$'}number: Int!) {
              repositoryOwner(login: ${'$'}login) {
                ... on ProjectV2Owner {
                  projectV2(number: ${'$'}number) {
                    id
                  }
                }
              }
            }
        """.trimIndent()
        val data = client.execute(query, buildJsonObject {
            put("login", board.owner)
            put("number", board.number)
        })
        return data["repositoryOwner"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.jsonObject?.get("projectV2")?.takeIf { it !is kotlinx.serialization.json.JsonNull }
            ?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private fun userBoards(owner: String): List<Board> {
        val query = """
            query GetUserProjects(${'$'}login: String!) {
              user(login: ${'$'}login) {
                projectsV2(first: 20) {
                  nodes {
                    id
                    title
                  }
                }
              }
            }
        """.trimIndent()
        val data = client.execute(query, buildJsonObject { put("login", owner) })
        val nodes = data["user"]?.jsonObject?.get("projectsV2")?.jsonObject?.get("nodes")?.jsonArray ?: return emptyList()
        return nodes.mapNotNull { node ->
            val id = node.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Board(id, node.jsonObject["title"]?.jsonPrimitive?.content ?: "")
        }
    }

    private fun setProjectItemFieldValue(projectId: String, itemId: String, fieldId: String, optionId: String) {
        val mutation = """
            mutation UpdateField(${'$'}projectId: ID!, ${'$'}itemId: ID!, ${'$'}fieldId: ID!, ${'$'}optionId: String!) {
              updateProjectV2ItemFieldValue(input: {
                projectId: ${'$'}projectId,
                itemId: ${'$'}itemId,
                fieldId: ${'$'}fieldId,
                value: { singleSelectOptionId: ${'$'}optionId }
              }) {
                clientMutationId
              }
            }
        """.trimIndent()

        client.execute(mutation, buildJsonObject {
            put("projectId", projectId)
            put("itemId", itemId)
            put("fieldId", fieldId)
            put("optionId", optionId)
        })
    }
}

internal data class Board(val id: String, val title: String)

internal data class BoardRef(val owner: String, val number: Int)

/** The board lanes.json names for [type]: `epicProject` for epics, `project` for every other issue; null when it names none. */
internal fun configuredBoard(type: WorkItemType, lanes: JsonObject): BoardRef? {
    val key = if (type == WorkItemType.EPIC) "epicProject" else "project"
    val board = lanes[key] as? JsonObject ?: return null
    val owner = board["owner"]?.jsonPrimitive?.content ?: return null
    val number = board["number"]?.jsonPrimitive?.intOrNull ?: return null
    return BoardRef(owner, number)
}

/** Without lanes.json: the board titled "Epic" for epics; for the rest a "Workflow" board, else the first board not for epics. */
internal fun boardByTitle(type: WorkItemType, boards: List<Board>): Board? {
    val (epicBoards, issueBoards) = boards.partition { it.title.contains("epic", ignoreCase = true) }
    if (type == WorkItemType.EPIC) return epicBoards.firstOrNull()
    return issueBoards.firstOrNull { it.title.contains("Workflow", ignoreCase = true) } ?: issueBoards.firstOrNull()
}

internal data class StatusTarget(val projectId: String, val itemId: String, val fieldId: String, val optionId: String)

/** The issue's board column, or null when it is on no board or its Status option is not a known column. */
internal fun statusColumn(issueJson: JsonObject): BoardColumn? {
    val projectItems = issueJson["projectItems"]?.jsonObject?.get("nodes")?.jsonArray ?: return null
    for (item in projectItems) {
        val fieldValues = item.jsonObject["fieldValues"]?.jsonObject?.get("nodes")?.jsonArray ?: continue
        for (value in fieldValues) {
            val valueObj = value.jsonObject
            if (valueObj["field"]?.jsonObject?.get("name")?.jsonPrimitive?.content != "Status") continue
            val optionName = valueObj["name"]?.jsonPrimitive?.content ?: continue
            return runCatching { BoardColumn.of(optionName) }.getOrNull()
        }
    }
    return null
}

/** Each board item of the issue whose Status field has an option for [column]. */
internal fun statusTargets(issueJson: JsonObject, column: BoardColumn): List<StatusTarget> {
    val projectItems = issueJson["projectItems"]?.jsonObject?.get("nodes")?.jsonArray ?: return emptyList()
    return projectItems.mapNotNull { item ->
        val itemId = item.jsonObject["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val project = item.jsonObject["project"]?.jsonObject ?: return@mapNotNull null
        val projectId = project["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val fields = project["fields"]?.jsonObject?.get("nodes")?.jsonArray ?: return@mapNotNull null
        val status = fields.map { it.jsonObject }
            .firstOrNull { it["name"]?.jsonPrimitive?.content == "Status" } ?: return@mapNotNull null
        val fieldId = status["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        val option = status["options"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { opt ->
            val name = opt["name"]?.jsonPrimitive?.content ?: ""
            runCatching { BoardColumn.of(name) }.getOrNull() == column
        } ?: return@mapNotNull null
        val optionId = option["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
        StatusTarget(projectId, itemId, fieldId, optionId)
    }
}
