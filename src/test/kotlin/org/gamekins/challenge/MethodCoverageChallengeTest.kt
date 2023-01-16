/*
 * Copyright 2022 Gamekins contributors
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
import io.kotest.core.spec.style.FeatureSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.gamekins.file.SourceFileDetails
import org.gamekins.util.Constants.Parameters
import org.jsoup.nodes.Document

class MethodCoverageChallengeTest : FeatureSpec({

    val className = "Challenge"
    val path = FilePath(null, "/home/test/workspace")
    val shortFilePath = "src/main/java/org/gamekins/challenge/$className.kt"
    val shortJacocoPath = "**/target/site/jacoco/"
    val shortJacocoCSVPath = "**/target/site/jacoco/csv"
    val mocoJSONPath = "**/target/site/moco/mutation/"
    lateinit var details : SourceFileDetails
    lateinit var challenge : MethodCoverageChallenge
    lateinit var method : JacocoUtil.CoverageMethod
    val coverage = 0.0
    val run = mockkClass(Run::class)
    val parameters = Parameters()
    val listener = TaskListener.NULL
    val branch = "master"
    val methodName = "toString"
    val data = mockkClass(Challenge.ChallengeGenerationData::class)

    beforeContainer {
        parameters.branch = branch
        parameters.workspace = path
        parameters.jacocoResultsPath = shortJacocoPath
        parameters.jacocoCSVPath = shortJacocoCSVPath
        parameters.mocoJSONPath = mocoJSONPath
        mockkStatic(JacocoUtil::class)
        val document = mockkClass(Document::class)
        method = JacocoUtil.CoverageMethod(methodName, 10, 10, "")
        every { JacocoUtil.calculateCurrentFilePath(any(), any()) } returns path
        every { JacocoUtil.getCoverageInPercentageFromJacoco(any(), any()) } returns coverage
        every { JacocoUtil.generateDocument(any()) } returns document
        every { JacocoUtil.calculateCoveredLines(any(), any()) } returns 0
        every { JacocoUtil.getNotFullyCoveredMethodEntries(any()) } returns arrayListOf(method)
        every { JacocoUtil.getMethodEntries(any()) } returns arrayListOf()
        details = SourceFileDetails(parameters, shortFilePath, listener)
        every { data.selectedFile } returns details
        every { data.parameters } returns parameters
        every { data.method } returns method
        challenge = MethodCoverageChallenge(data)
    }

    afterSpec {
        unmockkAll()
    }

    feature("getScore") {
        scenario("Coverage below 0.8")
        {
            challenge.getScore() shouldBe 2
        }

        method = JacocoUtil.CoverageMethod(methodName, 10, 1, "")
        every { JacocoUtil.getNotFullyCoveredMethodEntries(any()) } returns arrayListOf(method)
        every { data.method } returns method
        challenge = MethodCoverageChallenge(data)
        scenario("Coverage above 0.8")
        {
            challenge.getScore() shouldBe 3
        }

    }

    feature("isSolvable") {
        scenario("No MethodFile")
        {
            challenge.isSolvable(parameters, run, listener) shouldBe true
        }

        val pathMock = mockkClass(FilePath::class)
        every { pathMock.exists() } returns true
        every { JacocoUtil.calculateCurrentFilePath(any(), any(), any()) } returns pathMock
        scenario("Method has no Entry in Jacoco File")
        {
            challenge.isSolvable(parameters, run, listener) shouldBe false
        }

        every { JacocoUtil.getMethodEntries(any()) } returns arrayListOf(method)
        scenario("Method has missed lines")
        {
            challenge.isSolvable(parameters, run, listener) shouldBe true
        }

        method = JacocoUtil.CoverageMethod(methodName, 10, 0, "")
        every { JacocoUtil.getMethodEntries(any()) } returns arrayListOf(method)
        scenario("Method is fully covered")
        {
            challenge.isSolvable(parameters, run, listener) shouldBe false
        }
    }

    feature("isSolved") {
        val pathMock = mockkClass(FilePath::class)
        every { pathMock.exists() } returns false
        every { pathMock.remote } returns path.remote
        every { JacocoUtil.getJacocoFileInMultiBranchProject(any(), any(), any(), any()) } returns pathMock
        scenario("File does not exist")
        {
            challenge.isSolved(parameters, run, listener) shouldBe false
        }

        every { pathMock.exists() } returns true
        scenario("Method not in JacocoFile")
        {
            challenge.isSolved(parameters, run, listener) shouldBe false
        }

        every { JacocoUtil.getMethodEntries(any()) } returns arrayListOf(method)
        scenario("All lines missed")
        {
            challenge.isSolved(parameters, run, listener) shouldBe false
        }

        method = JacocoUtil.CoverageMethod(methodName, 10, 0, "")
        every { JacocoUtil.getMethodEntries(any()) } returns arrayListOf(method)
        scenario("Enough lines covered")
        {
            challenge.isSolved(parameters, run, listener) shouldBe true
        }
    }
})