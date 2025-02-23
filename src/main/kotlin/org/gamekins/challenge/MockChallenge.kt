package org.gamekins.challenge

import hudson.model.Run
import hudson.model.TaskListener
import hudson.model.User
import org.gamekins.challenge.Challenge.ChallengeGenerationData
import org.gamekins.file.FileDetails
import org.gamekins.file.SourceFileDetails
import org.gamekins.util.Constants
import org.gamekins.util.GitUtil
import org.gamekins.util.JacocoUtil
import org.gamekins.util.ParameterUtil

class MockChallenge(data: ChallengeGenerationData) :
    CoverageChallenge(data.selectedFile as SourceFileDetails, data.parameters.workspace) {

    private val lines = data.method!!.lines
    private val methodName = data.method!!.methodName
    private val missedLines = data.method!!.missedLines
    private val firstLineID = data.method!!.firstLineID
    private val user: User = data.user


    init {
        codeSnippet = createCodeSnippet(details, firstLineID, data.parameters.workspace)
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is MockChallenge) return false
        return other.details.packageName == this.details.packageName
                && other.details.fileName == this.details.fileName
                && other.methodName == this.methodName
    }

    override fun getSnippet(): String {
        return codeSnippet.ifEmpty { "Code snippet is not available" }
    }

    override fun getName(): String {
        return "Mock Coverage"
    }

    override fun getScore(): Int {
        return if ((lines - missedLines) / lines.toDouble() > 0.8) 4 else 4
    }

    override fun hashCode(): Int {
        var result = lines
        result = 31 * result + methodName.hashCode()
        result = 31 * result + missedLines
        return result
    }

    /**
     * Checks whether the [MethodCoverageChallenge] is solvable if the [run] was in the branch (taken from
     * [parameters]), where it has been generated. There must be uncovered or not fully covered lines left in the
     * method. The workspace is the folder with the code and execution rights, and the [listener] reports the events
     * to the console output of Jenkins.
     */
    override fun isSolvable(parameters: Constants.Parameters, run: Run<*, *>, listener: TaskListener): Boolean {
        if (details.parameters.branch != parameters.branch) return true
        if (!details.update(parameters).filesExists()) return false

        val jacocoMethodFile = JacocoUtil.calculateCurrentFilePath(
            parameters.workspace,
            details.jacocoMethodFile, details.parameters.remote
        )
        try {
            if (!jacocoMethodFile.exists()) {
                listener.logger.println(
                    "[Gamekins] JaCoCo method file "
                            + jacocoMethodFile.remote + Constants.EXISTS + jacocoMethodFile.exists()
                )
                return true
            }

            val methods = JacocoUtil.getMethodEntries(jacocoMethodFile)
            for (method in methods) {
                if (method.methodName == methodName) {
                    return method.missedLines > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace(listener.logger)
            return false
        }

        return false
    }

    /**
     * The [MethodCoverageChallenge] is solved if the number of missed lines, according to the [details] JaCoCo
     * files, is less than during generation. The workspace is the folder with the code and execution rights, and
     * the [listener] reports the events to the console output of Jenkins.
     */
    override fun isSolved(parameters: Constants.Parameters, run: Run<*, *>, listener: TaskListener): Boolean {
        val jacocoMethodFile = JacocoUtil.getJacocoFileInMultiBranchProject(
            run, parameters,
            JacocoUtil.calculateCurrentFilePath(
                parameters.workspace, details.jacocoMethodFile,
                details.parameters.remote
            ), details.parameters.branch
        )
        val jacocoCSVFile = JacocoUtil.getJacocoFileInMultiBranchProject(
            run, parameters,
            JacocoUtil.calculateCurrentFilePath(
                parameters.workspace, details.jacocoCSVFile,
                details.parameters.remote
            ), details.parameters.branch
        )

        val lastChangedFilesOfUser = GitUtil.getLastChangedTestsOfUser(
            details.parameters.branch, parameters, listener, GitUtil.GameUser(user),
            GitUtil.mapUsersToGameUsers(User.getAll())
        )

        if (lastChangedFilesOfUser.isEmpty()) return false

        val mockitoWords = listOf("when", "any", "thenReturn", "verify")

        try {
            if (!jacocoMethodFile.exists() || !jacocoCSVFile.exists()) {
                listener.logger.println(
                    "[Gamekins] JaCoCo method file " + jacocoMethodFile.remote
                            + Constants.EXISTS + jacocoMethodFile.exists()
                )
                listener.logger.println(
                    "[Gamekins] JaCoCo csv file " + jacocoCSVFile.remote
                            + Constants.EXISTS + jacocoCSVFile.exists()
                )
                return false
            }

            val methods = JacocoUtil.getMethodEntries(jacocoMethodFile)
            for (method in methods) {
                val regex = Regex("""(\w+)\s*\(""")
                val match = regex.find(methodName)
                val methodNameRegex = match?.groups?.get(1)?.value
                if (method.methodName == methodName) {
                    if (method.missedLines < missedLines) {
                        if (methodNameRegex != null) {
                            if (lastChangedFilesOfUser.get(0).codeByTest.values.filter { methodNameRegex.trim() in it }
                                    .isNotEmpty()) {
                                val keyWithMethodName = lastChangedFilesOfUser.get(0).codeByTest.entries.firstOrNull {
                                    it.value.contains(methodNameRegex)
                                }?.key
                                if (mockitoWords.any { word ->
                                        lastChangedFilesOfUser.get(0).codeByTest.get(
                                            keyWithMethodName
                                        )?.contains(word) == true
                                    }) {
                                    super.setSolved(System.currentTimeMillis())
                                    solvedCoverage = JacocoUtil.getCoverageInPercentageFromJacoco(
                                        details.fileName, jacocoCSVFile
                                    )
                                    return true
                                }
                            }
                        }
                    }
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace(listener.logger)
        }

        return false
    }

    override fun toString(): String {
        return ("Write a test using mock <b>" + methodName + "</b> in class <b>"
                + details.fileName + "</b> in package <b>" + details.packageName
                + "</b> (created for branch " + details.parameters.branch + ")")
    }
}