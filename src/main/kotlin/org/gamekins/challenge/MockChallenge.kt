package org.gamekins.challenge

import hudson.model.Run
import hudson.model.TaskListener
import org.gamekins.file.FileDetails
import org.gamekins.util.Constants
import org.gamekins.util.ParameterUtil

class MockChallenge(testsName: HashSet<String>, testsCodes: HashMap<String, String>, val details: FileDetails) : Challenge {

    private val testName: String = ParameterUtil.getTestName(testsName).toString()
    private val testCode: String = ParameterUtil.getTest(testName, testsCodes).toString()
    private val created = System.currentTimeMillis()
    private var solved: Long = 0


    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

    override fun getParameters(): Constants.Parameters {
        TODO("Not yet implemented")
    }

    override fun getCreated(): Long {
        TODO("Not yet implemented")
    }

    override fun getName(): String {
        return "Parameters Test"
    }

    override fun getScore(): Int {
        return 4
    }

    override fun getSolved(): Long {
        TODO("Not yet implemented")
    }

    override fun isSolvable(parameters: Constants.Parameters, run: Run<*, *>, listener: TaskListener): Boolean {
        TODO("Not yet implemented")
    }

    override fun isSolved(parameters: Constants.Parameters, run: Run<*, *>, listener: TaskListener): Boolean {
        TODO("Not yet implemented")
    }

    override fun printToXML(reason: String, indentation: String): String? {
        TODO("Not yet implemented")
    }

    override fun setRejectedTime(time: Long) {
        TODO("Not yet implemented")
    }

    override fun toString(): String {
        return ("Write a test with different parameter using the test method" + "<b>" + testName + "</b> in class <b>" + details.fileName
                + "</b> in package <b>" + details.packageName + "</b> (created for branch "
                + details.parameters.branch + ")")
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}