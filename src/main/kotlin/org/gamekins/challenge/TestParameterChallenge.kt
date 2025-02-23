package org.gamekins.challenge

import hudson.model.Run
import hudson.model.TaskListener
import hudson.model.User
import org.apache.commons.text.similarity.CosineSimilarity
import org.gamekins.file.FileDetails
import org.gamekins.util.Constants
import org.gamekins.util.Constants.Parameters
import org.gamekins.util.GitUtil
import org.gamekins.util.JUnitUtil
import org.gamekins.util.ParameterUtil
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject

class TestParameterChallenge(
    testsName: HashSet<String>,
    testsCodes: HashMap<String, String>,
    data: Challenge.ChallengeGenerationData,
    val details: FileDetails
) : Challenge {

    private val testsName = testsName
    private val testsCodes = testsCodes
    private val testNameToChallenge: String = ParameterUtil.getTestName(testsName).toString()
    private val testCodeToChallenge: String = ParameterUtil.getTest(testNameToChallenge, testsCodes).toString()
    private var currentCommit: String = data.headCommitHash!!
    private var testCount: Int = data.testCount!!
    private val user: User = data.user
    private var parameters: Parameters = data.parameters
    private val created = System.currentTimeMillis()
    private var solved: Long = 0
    private var testCountSolved = 0

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is TestParameterChallenge) return false
        return true
    }

    override fun getParameters(): Constants.Parameters {
        return parameters
    }

    override fun getCreated(): Long {
        return created
    }

    override fun getName(): String {
        return "Parameters Test"
    }

    override fun getScore(): Int {
        return 3
    }

    override fun getSolved(): Long {
        return solved
    }

    override fun isSolvable(parameters: Constants.Parameters, run: Run<*, *>, listener: TaskListener): Boolean {
        if (run.parent.parent is WorkflowMultiBranchProject) {
            for (workflowJob in (run.parent.parent as WorkflowMultiBranchProject).items) {
                if (workflowJob.name == this.parameters.branch) return true
            }
        } else {
            return true
        }
        return false
    }

    override fun isSolved(parameters: Constants.Parameters, run: Run<*, *>, listener: TaskListener): Boolean {
        try {
            val testCountSolved = JUnitUtil.getTestCount(parameters.workspace, run)
            if (testCountSolved <= testCount) {
                return false
            }
            val lastChangedFilesOfUser = GitUtil.getLastChangedTestsOfUser(
                currentCommit, parameters, listener, GitUtil.GameUser(user),
                GitUtil.mapUsersToGameUsers(User.getAll())
            )
            if (lastChangedFilesOfUser.isNotEmpty()) {
                val newTestName = lastChangedFilesOfUser.get(0).testNames - testsName
                val differenceString = newTestName.toList()

                for (testName in differenceString) {
                    val newTestCode = lastChangedFilesOfUser.get(0).codeByTest[testName]
                    val similarity = CosineSimilarity()
                    val vector1 = toVector(testCodeToChallenge)
                    val vector2 = newTestCode?.let { toVector(it) }
                    val score = similarity.cosineSimilarity(vector1, vector2)
                    if (score >= 0.9) {
                        solved = System.currentTimeMillis()
                        this.testCountSolved = testCountSolved
                        return true
                    }
                }
                return false
            }
        } catch (e: Exception) {
            e.printStackTrace(listener.logger)
        }
        return false
    }

    override fun printToXML(reason: String, indentation: String): String? {
        var print = (indentation + "<TestParameterChallenge created=\"" + created + "\" solved=\"" + solved
                + "\" tests=\"" + testCount + "\" testsAtSolved=\"" + testCountSolved)
        if (reason.isNotEmpty()) {
            print += "\" reason=\"$reason"
        }
        print += "\"/>"
        return print
    }

    override fun setRejectedTime(time: Long) {
        solved = time
    }

    override fun toString(): String {
        return ("Write a test with different parameter using the test method" + " " + "<b>" + testNameToChallenge + "</b> in class <b>" + details.fileName
                + "</b> in package <b>" + details.packageName + "</b> (created for branch "
                + details.parameters.branch + ")")
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    fun toVector(text: String): Map<CharSequence, Int> {
        return text.groupingBy { it.toString() }
            .eachCount()
    }
}