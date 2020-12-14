/*
 * Copyright 2020 Gamekins contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 * http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gamekins.challenge

import hudson.FilePath
import hudson.model.Run
import hudson.model.TaskListener
import org.gamekins.util.JacocoUtil
import org.gamekins.util.JacocoUtil.ClassDetails
import org.jsoup.nodes.Element
import kotlin.math.abs

/**
 * Specific [Challenge] to motivate the user to cover a random line of a specific class.
 *
 * @author Philipp Straubinger
 * @since 1.0
 */
class LineCoverageChallenge(classDetails: ClassDetails, branch: String, workspace: FilePath, line: Element)
    : CoverageChallenge(classDetails, branch, workspace) {

    private val coverageType: String = line.attr("class")
    private val currentCoveredBranches: Int
    private val lineContent: String = line.text()
    private val lineNumber: Int = line.attr("id").substring(1).toInt()
    private val maxCoveredBranches: Int

    init {
        val split = line.attr("title").split(" ".toRegex())
        when {
            split.isEmpty() || (split.size == 1 && split[0].isBlank()) -> {
                currentCoveredBranches = 0
                maxCoveredBranches = 1
            }
            line.attr("class").startsWith("pc") -> {
                currentCoveredBranches = split[2].toInt() - split[0].toInt()
                maxCoveredBranches = split[2].toInt()
            }
            else -> {
                currentCoveredBranches = 0
                maxCoveredBranches = split[1].toInt()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is LineCoverageChallenge) return false
        return other.classDetails.packageName == this.classDetails.packageName
                && other.classDetails.className == this.classDetails.className
                && other.lineNumber == this.lineNumber
                && other.lineContent == this.lineContent
                && other.coverageType == this.coverageType
    }

    override fun getName(): String {
        return "LineCoverageChallenge"
    }

    override fun getScore(): Int {
        return if (coverage >= 0.8 || coverageType == "pc") 3 else 2
    }

    override fun getToolTipText(): String {
        return "Line content: ${lineContent.trim()}"
    }

    override fun hashCode(): Int {
        var result = coverageType.hashCode()
        result = 31 * result + currentCoveredBranches
        result = 31 * result + lineContent.hashCode()
        result = 31 * result + lineNumber
        result = 31 * result + maxCoveredBranches
        return result
    }

    /**
     * Checks whether the [LineCoverageChallenge] is solvable if the [run] was in the [branch] (taken from
     * [constants]), where it has been generated. The line must not be covered and still be in the class.
     * The [workspace] is the folder with the code and execution rights, and the [listener] reports the events to the
     * console output of Jenkins.
     */
    override fun isSolvable(constants: HashMap<String, String>, run: Run<*, *>, listener: TaskListener,
                            workspace: FilePath): Boolean {
        if (branch != constants["branch"]) return true

        val jacocoSourceFile = JacocoUtil.calculateCurrentFilePath(workspace, classDetails.jacocoSourceFile,
                classDetails.workspace)
        val jacocoCSVFile = JacocoUtil.calculateCurrentFilePath(workspace, classDetails.jacocoCSVFile,
                classDetails.workspace)
        if (!jacocoSourceFile.exists() || !jacocoCSVFile.exists()) return true

        val document = JacocoUtil.generateDocument(jacocoSourceFile, jacocoCSVFile, listener) ?: return false

        val elements = document.select("span." + "pc")
        elements.addAll(document.select("span." + "nc"))
        for (element in elements) {
            if (element.text() == lineContent) {
                return true
            }
        }

        return false
    }

    /**
     * The [LineCoverageChallenge] is solved if the line, according to the [classDetails] JaCoCo files, is fully
     * covered or partially covered (only if it was uncovered during generation). The [workspace] is the folder with
     * the code and execution rights, and the [listener] reports the events to the console output of Jenkins.
     */
    override fun isSolved(constants: HashMap<String, String>, run: Run<*, *>, listener: TaskListener,
                          workspace: FilePath): Boolean {
        val jacocoSourceFile = JacocoUtil.getJacocoFileInMultiBranchProject(run, constants,
                JacocoUtil.calculateCurrentFilePath(workspace, classDetails.jacocoSourceFile,
                        classDetails.workspace), branch)
        val jacocoCSVFile = JacocoUtil.getJacocoFileInMultiBranchProject(run, constants,
                JacocoUtil.calculateCurrentFilePath(workspace, classDetails.jacocoCSVFile,
                        classDetails.workspace), branch)

        val document = JacocoUtil.generateDocument(jacocoSourceFile, jacocoCSVFile, listener) ?: return false

        val elements = document.select("span." + "fc")
        elements.addAll(document.select("span." + "pc"))
        for (element in elements) {
            if (element.text() == lineContent && element.attr("id").substring(1).toInt() == lineNumber) {
                return setSolved(elements[0], jacocoCSVFile)
            }
        }

        elements.addAll(document.select("span." + "nc"))
        elements.removeIf { it.text() != lineContent }

        if (elements.isNotEmpty()) {
            if (elements.size == 1 && elements[0].attr("class") != "nc") {
                return setSolved(elements[0], jacocoCSVFile)
            } else {
                val nearestElement = elements.minByOrNull { abs(lineNumber - it.attr("id").substring(1).toInt()) }
                if (nearestElement != null && nearestElement.attr("class") != "nc") {
                    return setSolved(elements[0], jacocoCSVFile)
                }
            }
        }

        return false
    }

    override fun isToolTip(): Boolean {
        return true
    }

    /**
     * Checks whether the line [element] has more covered branches than during creation and sets the time and
     * coverage if solved.
     */
    private fun setSolved(element: Element, jacocoCSVFile: FilePath): Boolean {
        val title = element.attr("title").split(" ".toRegex())[0]
        if (title == "All"
                || (maxCoveredBranches > 1 && maxCoveredBranches - title.toInt() <= currentCoveredBranches)) {
            return false
        }
        super.setSolved(System.currentTimeMillis())
        solvedCoverage = JacocoUtil.getCoverageInPercentageFromJacoco(classDetails.className, jacocoCSVFile)
        return true
    }

    override fun toString(): String {
        val prefix =
                if (maxCoveredBranches > 1) "Write a test to cover more branches (currently $currentCoveredBranches " +
                        "of $maxCoveredBranches covered) of line "
                else "Write a test to fully cover line "
        return (prefix + lineNumber + " in class " + classDetails.className
                + " in package " + classDetails.packageName + " (created for branch " + branch + ")")
    }
}