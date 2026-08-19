package devcli.service

import devcli.model.WorkspaceRef
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

data class FetchSummary(
    val relativePath: String,
    val success: Boolean,
    val errorMessage: String? = null
)

enum class GetAction { CLONED, FETCHED }

data class GetSummary(
    val relativePath: String,
    val action: GetAction,
    val success: Boolean,
    val errorMessage: String? = null
)

data class WorkspaceDetail(
    val relativePath: String,
    val headRef: String,
    val staleness: String,
    val isDirty: Boolean = false
)

data class InitSummary(
    val relativePath: String,
    val targetDirectory: String,
    val gitInitialized: Boolean,
    val commitCreated: Boolean
)

class WorkspaceService(
    private val projectsRoot: File = File("/projects")
) {
    private fun ensureProjectsRootWritable() {
        check(projectsRoot.exists() || projectsRoot.mkdirs()) {
            "Failed to create projects directory ${projectsRoot.path}"
        }
        check(projectsRoot.isDirectory && projectsRoot.canWrite()) {
            "Projects directory ${projectsRoot.path} is not writable; fix the volume ownership before cloning repositories"
        }
    }

    private fun resolveWorkspacePath(relativePath: String): File {
        val pathInput = relativePath.trim()
        require(pathInput.isNotBlank()) { "Repository path must not be blank" }

        val untrustedPath = Path.of(pathInput)
        require(!untrustedPath.isAbsolute && untrustedPath.none { it.toString() == "." || it.toString() == ".." }) {
            "Repository path must be relative and cannot contain traversal segments"
        }

        val rootPath = projectsRoot.toPath().toAbsolutePath().normalize()
        val targetPath = rootPath.resolve(untrustedPath).normalize()
        require(targetPath.startsWith(rootPath) && targetPath != rootPath) {
            "Repository path must remain inside ${projectsRoot.path}"
        }

        val existingAncestor = generateSequence(targetPath) { it.parent }
            .firstOrNull { Files.exists(it) }
            ?: throw IllegalStateException("Unable to resolve an existing parent for $targetPath")

        val realRootPath = rootPath.toRealPath()
        require(existingAncestor.toRealPath().startsWith(realRootPath)) {
            "Repository path resolves outside ${projectsRoot.path}"
        }

        return targetPath.toFile()
    }

    fun cloneWorkspace(repoUrl: String): Result<String> {
        return try {
            val ref = WorkspaceRef.fromRemote(repoUrl)
            ensureProjectsRootWritable()
            val targetDir = resolveWorkspacePath(ref.relativePath)

            if (targetDir.exists() && File(targetDir, ".git").exists()) {
                return Result.failure(IllegalStateException("Repository already exists at ${targetDir.path}"))
            }

            val parentDir = targetDir.parentFile
            if (parentDir != null && !parentDir.exists()) {
                val created = parentDir.mkdirs()
                if (!created && !parentDir.exists()) {
                    return Result.failure(IllegalStateException("Failed to create leading directory ${parentDir.path}. Please check permissions on /projects."))
                }
            }

            println("Cloning ${ref.remoteUrl} into ${targetDir.path}...")
            val process = ProcessBuilder("git", "clone", ref.remoteUrl, targetDir.path)
                .inheritIO()
                .start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(targetDir.path)
            } else {
                Result.failure(RuntimeException("git clone failed with exit code $exitCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun listWorkspaces(): List<String> {
        if (!projectsRoot.exists()) return emptyList()

        return projectsRoot.walkTopDown()
            .maxDepth(6)
            .filter { it.isDirectory && it.name == ".git" }
            .map { gitDir -> gitDir.parentFile.relativeTo(projectsRoot).path }
            .sorted()
            .toList()
    }

    fun findMatchingWorkspaces(target: String): List<String> {
        val cleanTarget = target.trim().removePrefix("/projects").trim('/')
        if (cleanTarget.isBlank()) return listWorkspaces()

        val allWorkspaces = listWorkspaces()
        return allWorkspaces.filter { workspace ->
            workspace == cleanTarget || workspace.endsWith("/$cleanTarget")
        }
    }

    fun fetchWorkspaces(
        targetRepoPath: String? = null,
        prune: Boolean = false,
        tags: Boolean = false
    ): Result<List<FetchSummary>> {
        if (!projectsRoot.exists()) {
            return Result.failure(IllegalStateException("Projects directory ${projectsRoot.path} does not exist"))
        }

        val workspacesToFetch: List<String> = if (!targetRepoPath.isNullOrBlank()) {
            val matched = findMatchingWorkspaces(targetRepoPath)
            if (matched.isEmpty()) {
                return Result.failure(IllegalArgumentException("Repository '$targetRepoPath' not found in ${projectsRoot.path}"))
            }
            matched
        } else {
            listWorkspaces()
        }

        if (workspacesToFetch.isEmpty()) {
            return Result.success(emptyList())
        }

        val total = workspacesToFetch.size
        println("FETCHING GIT REMOTES IN ${projectsRoot.path}:")
        println("--------------------------------------------------")

        val results = mutableListOf<FetchSummary>()
        workspacesToFetch.forEachIndexed { index, relPath ->
            val repoDir = File(projectsRoot, relPath)
            println("[${index + 1}/$total] Fetching remotes for $relPath...")

            val command = mutableListOf("git", "fetch", "--all")
            if (prune) command.add("--prune")
            if (tags) command.add("--tags")

            try {
                val process = ProcessBuilder(command)
                    .directory(repoDir)
                    .inheritIO()
                    .start()

                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    println("Successfully fetched remotes for $relPath\n")
                    results.add(FetchSummary(relativePath = relPath, success = true))
                } else {
                    System.err.println("error: git fetch failed for $relPath with exit code $exitCode\n")
                    results.add(FetchSummary(relativePath = relPath, success = false, errorMessage = "git fetch failed with exit code $exitCode"))
                }
            } catch (e: Exception) {
                System.err.println("error: failed to execute git fetch for $relPath: ${e.message}\n")
                results.add(FetchSummary(relativePath = relPath, success = false, errorMessage = e.message))
            }
        }

        return Result.success(results)
    }

    fun getWorkspace(repoRef: String? = null): Result<List<GetSummary>> {
        if (!projectsRoot.exists()) {
            return Result.failure(IllegalStateException("Projects directory ${projectsRoot.path} does not exist"))
        }

        if (repoRef.isNullOrBlank()) {
            val workspaces = listWorkspaces()
            if (workspaces.isEmpty()) {
                return Result.success(emptyList())
            }
            return fetchWorkspaces().map { fetchSummaries ->
                fetchSummaries.map { fs ->
                    GetSummary(
                        relativePath = fs.relativePath,
                        action = GetAction.FETCHED,
                        success = fs.success,
                        errorMessage = fs.errorMessage
                    )
                }
            }
        }

        val matches = findMatchingWorkspaces(repoRef)
        if (matches.isNotEmpty()) {
            val results = mutableListOf<GetSummary>()
            for (relPath in matches) {
                val fetchRes = fetchWorkspaces(relPath)
                fetchRes.onSuccess { summaries ->
                    summaries.forEach { s ->
                        results.add(GetSummary(relativePath = s.relativePath, action = GetAction.FETCHED, success = s.success, errorMessage = s.errorMessage))
                    }
                }.onFailure { err ->
                    results.add(GetSummary(relativePath = relPath, action = GetAction.FETCHED, success = false, errorMessage = err.message))
                }
            }
            return Result.success(results)
        }

        val ref = try {
            WorkspaceRef.fromRemote(repoRef)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val targetDir = File(ref.targetDirectoryPath)
        if (targetDir.exists() && File(targetDir, ".git").exists()) {
            val fetchRes = fetchWorkspaces(ref.relativePath)
            return fetchRes.map { summaries ->
                summaries.map { s ->
                    GetSummary(relativePath = s.relativePath, action = GetAction.FETCHED, success = s.success, errorMessage = s.errorMessage)
                }
            }
        }

        val cloneRes = cloneWorkspace(repoRef)
        return if (cloneRes.isSuccess) {
            Result.success(
                listOf(
                    GetSummary(
                        relativePath = ref.relativePath,
                        action = GetAction.CLONED,
                        success = true
                    )
                )
            )
        } else {
            Result.failure(cloneRes.exceptionOrNull() ?: RuntimeException("git clone failed for $repoRef"))
        }
    }

    fun getWorkspaceDetail(relPath: String): WorkspaceDetail {
        val repoDir = File(projectsRoot, relPath)
        if (!repoDir.exists() || !File(repoDir, ".git").exists()) {
            return WorkspaceDetail(relativePath = relPath, headRef = "invalid", staleness = "No git repo")
        }

        return try {
            val process = ProcessBuilder("git", "status", "--porcelain=v2", "--branch")
                .directory(repoDir)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            var branchHead = ""
            var branchOid = ""
            var branchUpstream = ""
            var ahead = 0
            var behind = 0
            var hasUpstream = false
            var isDirty = false

            output.lineSequence().forEach { line ->
                when {
                    line.startsWith("# branch.head ") -> {
                        branchHead = line.removePrefix("# branch.head ").trim()
                    }
                    line.startsWith("# branch.oid ") -> {
                        branchOid = line.removePrefix("# branch.oid ").trim()
                    }
                    line.startsWith("# branch.upstream ") -> {
                        branchUpstream = line.removePrefix("# branch.upstream ").trim()
                        if (branchUpstream.isNotBlank() && branchUpstream != "(detached)") {
                            hasUpstream = true
                        }
                    }
                    line.startsWith("# branch.ab ") -> {
                        val abStr = line.removePrefix("# branch.ab ").trim()
                        val parts = abStr.split(" ")
                        if (parts.size >= 2) {
                            ahead = parts[0].removePrefix("+").toIntOrNull() ?: 0
                            behind = parts[1].removePrefix("-").toIntOrNull() ?: 0
                        }
                    }
                    line.isNotBlank() && !line.startsWith("#") -> {
                        isDirty = true
                    }
                }
            }

            val headStr = if (branchHead.isNotBlank() && branchHead != "(detached)") {
                branchHead
            } else if (branchOid.isNotBlank() && branchOid != "(initial)") {
                branchOid.take(7)
            } else {
                "HEAD"
            }

            val formattedHead = if (isDirty) "$headStr*" else headStr

            val stalenessStr = when {
                !hasUpstream -> "No upstream"
                ahead == 0 && behind == 0 -> "Up to date"
                ahead > 0 && behind == 0 -> "Ahead $ahead"
                ahead == 0 && behind > 0 -> "Behind $behind"
                else -> "Diverged (+$ahead/-$behind)"
            }

            WorkspaceDetail(
                relativePath = relPath,
                headRef = formattedHead,
                staleness = stalenessStr,
                isDirty = isDirty
            )
        } catch (e: Exception) {
            WorkspaceDetail(relativePath = relPath, headRef = "error", staleness = e.message ?: "error")
        }
    }

    fun listWorkspaceDetails(): List<WorkspaceDetail> {
        val workspaces = listWorkspaces()
        return workspaces.map { getWorkspaceDetail(it) }
    }

    private fun cleanupEmptyParents(dir: File) {
        var current = dir.parentFile
        val rootPath = projectsRoot.toPath().toAbsolutePath().normalize()
        while (current != null) {
            val currentPath = current.toPath().toAbsolutePath().normalize()
            if (currentPath == rootPath || !currentPath.startsWith(rootPath)) {
                break
            }
            val contents = current.list()
            if (contents != null && contents.isEmpty()) {
                if (!current.delete()) break
                current = current.parentFile
            } else {
                break
            }
        }
    }

    fun removeWorkspace(relativeRepoPath: String): Result<String> {
        return try {
            val matching = findMatchingWorkspaces(relativeRepoPath)
            val resolvedRelPath = when {
                matching.size == 1 -> matching.first()
                matching.size > 1 -> return Result.failure(
                    IllegalArgumentException("Ambiguous repository reference '$relativeRepoPath'. Matches: ${matching.joinToString(", ")}")
                )
                else -> try {
                    WorkspaceRef.fromRemote(relativeRepoPath).relativePath
                } catch (_: Exception) {
                    relativeRepoPath.trim().trim('/')
                }
            }

            val targetDir = resolveWorkspacePath(resolvedRelPath)
            if (!targetDir.exists()) {
                return Result.failure(IllegalArgumentException("Repository not found at ${targetDir.path}"))
            }
            if (!File(targetDir, ".git").exists()) {
                return Result.failure(IllegalArgumentException("Refusing to remove ${targetDir.path}: it is not a Git worktree"))
            }
            if (!targetDir.deleteRecursively()) {
                return Result.failure(IllegalStateException("Failed to remove repository ${targetDir.path}"))
            }
            cleanupEmptyParents(targetDir)
            Result.success(targetDir.path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun initWorkspace(
        repoRef: String,
        description: String? = null,
        initGit: Boolean = true,
        commit: Boolean = true
    ): Result<InitSummary> {
        return try {
            ensureProjectsRootWritable()
            val relativePath = try {
                WorkspaceRef.fromRemote(repoRef).relativePath
            } catch (_: Exception) {
                repoRef.trim().trim('/')
            }

            val targetDir = resolveWorkspacePath(relativePath)
            if (targetDir.exists() && File(targetDir, ".git").exists()) {
                return Result.failure(IllegalStateException("Repository already exists at ${targetDir.path}"))
            }
            if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
                return Result.failure(IllegalStateException("Target directory ${targetDir.path} is not empty"))
            }

            if (!targetDir.exists() && !targetDir.mkdirs()) {
                return Result.failure(IllegalStateException("Failed to create directory ${targetDir.path}"))
            }

            // Create baseline directory structure
            val componentsDir = File(targetDir, "components")
            componentsDir.mkdirs()
            File(componentsDir, ".gitkeep").createNewFile()

            val deployDir = File(targetDir, "deploy")
            deployDir.mkdirs()
            File(deployDir, ".gitkeep").createNewFile()

            val projectName = relativePath.substringAfterLast('/')

            // Generate .gitignore
            val gitignoreContent = """
                # OS & Editor
                .DS_Store
                Thumbs.db
                .idea/
                .vscode/
                *.swp
                *~

                # Logs & Temp
                *.log
                tmp/
                temp/

                # Build & Environment
                .env
                .env.local
                dist/
                build/
                out/
            """.trimIndent()
            File(targetDir, ".gitignore").writeText(gitignoreContent + "\n")

            // Generate justfile
            val justfileContent = """
                set dotenv-load := true

                # List available recipes
                default:
                    @just --list

                # Run unit tests across all components
                unit comp="":
                    #!/usr/bin/env bash
                    if [ -n "{{comp}}" ]; then \
                        if [ -f "components/{{comp}}/justfile" ]; then \
                            (cd "components/{{comp}}" && just unit); \
                        fi; \
                    else \
                        for d in components/*/; do \
                            [ -d "${'$'}d" ] || continue; \
                            if [ -f "${'$'}d/justfile" ]; then \
                                echo "Running unit tests in ${'$'}d..."; \
                                (cd "${'$'}d" && just unit); \
                            fi; \
                        done; \
                    fi

                # Full Verification Gate
                verify:
                    @just unit
                    @echo "=== Verification PASS ==="
            """.trimIndent()
            File(targetDir, "justfile").writeText(justfileContent + "\n")

            // Generate AGENTS.md
            val agentsContent = """
                ---
                active_skills:
                  - ddd
                  - hexagonal-architecture
                  - tdd
                  - vcs
                  - deliver

                build_tools:
                  just:
                    build_script: justfile
                    lifecycle_tasks:
                      unit: just unit
                      verify: just verify
                ---

                # Repository Rules

                ## Architecture Invariants
                - Application services and domains live inside `components/<name>/`.
                - Always run `just verify` before publishing a task for review.
            """.trimIndent()
            File(targetDir, "AGENTS.md").writeText(agentsContent + "\n")

            // Generate README.md
            val desc = if (!description.isNullOrBlank()) description.trim() else "Monorepo workspace for $projectName."
            val readmeContent = """
                # $projectName

                $desc

                ## Structure

                - `components/` - Application components, domain modules, and services.
                - `deploy/` - Deployment configurations and environment manifests.
                - `justfile` - Task runner commands.
                - `AGENTS.md` - AI assistant guidelines and rules.

                ## Quick Start

                Ensure [`just`](https://github.com/casey/just) is installed.

                ```bash
                just --list   # List all available recipes
                just verify   # Run full project verification
                ```
            """.trimIndent()
            File(targetDir, "README.md").writeText(readmeContent + "\n")

            var gitInitSuccess = false
            var commitSuccess = false

            if (initGit) {
                val initProcess = ProcessBuilder("git", "init", "-b", "main")
                    .directory(targetDir)
                    .start()
                val initExit = initProcess.waitFor()
                if (initExit == 0) {
                    gitInitSuccess = true
                } else {
                    // Fallback to git init without -b main
                    val fallbackProcess = ProcessBuilder("git", "init")
                        .directory(targetDir)
                        .start()
                    if (fallbackProcess.waitFor() == 0) {
                        gitInitSuccess = true
                    }
                }

                if (gitInitSuccess && commit) {
                    ProcessBuilder("git", "add", ".")
                        .directory(targetDir)
                        .start()
                        .waitFor()

                    // Check if git user name/email are configured
                    val hasUserName = try {
                        val checkProcess = ProcessBuilder("git", "config", "user.name")
                            .directory(targetDir)
                            .start()
                        val out = checkProcess.inputStream.bufferedReader().readText().trim()
                        checkProcess.waitFor() == 0 && out.isNotBlank()
                    } catch (_: Exception) {
                        false
                    }

                    val commitCmd = if (hasUserName) {
                        listOf("git", "commit", "-m", "feat: initial project scaffold")
                    } else {
                        listOf(
                            "git",
                            "-c", "user.name=dev-cli",
                            "-c", "user.email=dev-cli@workspace.local",
                            "commit",
                            "-m", "feat: initial project scaffold"
                        )
                    }

                    val commitProcess = ProcessBuilder(commitCmd)
                        .directory(targetDir)
                        .start()
                    if (commitProcess.waitFor() == 0) {
                        commitSuccess = true
                    }
                }
            }

            Result.success(
                InitSummary(
                    relativePath = relativePath,
                    targetDirectory = targetDir.path,
                    gitInitialized = gitInitSuccess,
                    commitCreated = commitSuccess
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

