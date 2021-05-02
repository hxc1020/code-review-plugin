package red.hxc.plugin

import com.github.salomonbrys.kotson.jsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.vcs.log.impl.HashImpl
import git4idea.checkin.GitCheckinEnvironment
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.config.GitVersionSpecialty
import org.jetbrains.annotations.NonNls
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.util.*

class MyGitUtil(val project: Project) {
    init {
        testGit("hao.lin")
    }

    fun testGit(author: String) {
        val testData = jsonObject(
            "name" to "test",
            "age" to 123,
            "date" to LocalDateTime.now().toString()
        ).toString()
        val root =
            LocalFileSystem.getInstance().findFileByIoFile(File("/Users/haolin/IdeaProjects/private/gitDB")) ?: return
        val git = Git.getInstance()
        val hash = GitLineHandler(project, root, GitCommand.HASH_OBJECT).apply {
            setSilent(true)
            addParameters("-w", "--stdin")
            setInputProcessor(GitHandlerInputProcessorUtil.redirectStream(ByteArrayInputStream(testData.toByteArray())))
            endOptions()
        }.let {
            val output: String = git.runCommand(it).getOutputOrThrow()
            HashImpl.build(output.trim { data -> data <= ' ' })
        }
        val mode = "100644"
        val path = "fileName"
        GitLineHandler(project, root, GitCommand.UPDATE_INDEX).apply {
            addParameters("--add")
            if (GitVersionSpecialty.CACHEINFO_SUPPORTS_SINGLE_PARAMETER_FORM.existsIn(project)) {
                addParameters("--cacheinfo", mode + "," + hash.asString() + "," + path)
            } else {
                addParameters("--cacheinfo", mode, hash.asString(), path)
            }
            endOptions()
        }.let {
            git.runCommandWithoutCollectingOutput(it).throwOnError()
        }

        GitLineHandler(project, root, GitCommand.COMMIT).apply {
            addParameters("--message", "$author create card")
            addParameters("--author=$author")
            addParameters("--date", GitCheckinEnvironment.COMMIT_DATE_FORMAT.format(Date()))
            setStdoutSuppressed(false)
            endOptions()
        }.let {
            git.runCommand(it).throwOnError()
        }

        GitLineHandler(project, root, GitCommand.PUSH).let {
            val myCommandLine = GeneralCommandLine()
                .withWorkDirectory(VfsUtil.virtualToIoFile(root))
                .withExePath(it.executable.exePath)
                .withCharset(StandardCharsets.UTF_8)
            myCommandLine.addParameter(GitCommand.PUSH.name())
            addParameters(
                listOf(
                    "--porcelain",
                    "origin",
                    "master",
                    "--set-upstream"
                ), myCommandLine
            )
            ExecUtil.execAndReadLine(myCommandLine)
        }

        val result = GitLineHandler(project, root, GitCommand.SHOW).apply {
            addParameters("master:$path")
        }.let {
            git.runCommand(it)
        }
        println(result)
    }

    private fun escapeParameterIfNeeded(@NonNls parameter: String): String? {
        return if (escapeNeeded(parameter)) {
            parameter.replace("\\^".toRegex(), "^^^^")
        } else parameter
    }

    private fun escapeNeeded(@NonNls parameter: String): Boolean {
        return SystemInfo.isWindows && parameter.contains("^")
    }

    private fun addParameters(parameters: List<String?>, commandLine: GeneralCommandLine) {
        for (parameter in parameters) {
            escapeParameterIfNeeded(parameter!!)?.let { commandLine.addParameter(it) }
        }
    }
}

