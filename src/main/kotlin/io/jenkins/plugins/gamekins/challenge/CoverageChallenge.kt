package io.jenkins.plugins.gamekins.challenge

import hudson.FilePath
import io.jenkins.plugins.gamekins.util.JacocoUtil.ClassDetails
import io.jenkins.plugins.gamekins.util.JacocoUtil.calculateCoveredLines
import io.jenkins.plugins.gamekins.util.JacocoUtil.calculateCurrentFilePath
import io.jenkins.plugins.gamekins.util.JacocoUtil.generateDocument

/**
 * Abstract class to generate basic information about the class used for generating a [CoverageChallenge].
 *
 * @author Philipp Straubinger
 * @since 1.0
 */
abstract class CoverageChallenge(val classDetails: ClassDetails, val branch: String, workspace: FilePath?)
    : Challenge {

    protected val coverage: Double
    protected val fullyCoveredLines: Int
    protected val notCoveredLines: Int
    protected val partiallyCoveredLines: Int
    protected var solvedCoverage = 0.0
    private val created = System.currentTimeMillis()
    private var solved: Long = 0

    /**
     * Calculates the number of fully, partially and not covered lines, and the coverage of the class itself.
     */
    init {
        val document = generateDocument(calculateCurrentFilePath(workspace!!,
                classDetails.jacocoSourceFile, classDetails.workspace))
        fullyCoveredLines = calculateCoveredLines(document, "fc")
        partiallyCoveredLines = calculateCoveredLines(document, "pc")
        notCoveredLines = calculateCoveredLines(document, "nc")
        coverage = classDetails.coverage
    }

    override fun getConstants(): HashMap<String, String> {
        return classDetails.constants
    }

    override fun getCreated(): Long {
        return created
    }

    /**
     * Returns the name of the class of the current [CoverageChallenge].
     */
    abstract fun getName(): String

    override fun getSolved(): Long {
        return solved
    }

    override fun printToXML(reason: String, indentation: String): String {
        var print = (indentation + "<" + getName() + " created=\"" + created + "\" solved=\"" + solved
                + "\" class=\"" + classDetails.className + "\" coverage=\"" + coverage
                + "\" coverageAtSolved=\"" + solvedCoverage)
        if (reason.isNotEmpty()) {
            print += "\" reason=\"$reason"
        }
        print += "\"/>"
        return print
    }

    /**
     * Needed because of automatically generated getter and setter in Kotlin.
     */
    fun setSolved(newSolved: Long) {
        solved = newSolved
    }
}
