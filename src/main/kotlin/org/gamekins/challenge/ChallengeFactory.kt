/*
 * Copyright 2023 Gamekins contributors
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
import hudson.model.Result
import hudson.model.TaskListener
import hudson.model.User
import org.gamekins.GamePublisherDescriptor
import org.gamekins.GameUserProperty
import org.gamekins.challenge.Challenge.ChallengeGenerationData
import org.gamekins.event.EventHandler
import org.gamekins.event.user.ChallengeGeneratedEvent
import org.gamekins.file.FileDetails
import org.gamekins.file.SourceFileDetails
import org.gamekins.file.TestFileDetails
import org.gamekins.util.*
import org.gamekins.util.Constants.AND_TYPE
import org.gamekins.util.Constants.Parameters
import org.gamekins.util.Constants.TRY_CLASS
import org.gamekins.util.GitUtil.HeadCommitCallable
import org.jsoup.nodes.Document
import java.io.IOException
import kotlin.collections.ArrayList
import kotlin.jvm.Throws
import kotlin.random.Random

/**
 * Factory for generating [Challenge]s.
 *
 * @author Philipp Straubinger
 * @since 0.1
 */
object ChallengeFactory {

    /**
     * Chooses the type of [Challenge] to be generated.
     */
    private fun chooseChallengeType(): Class<out Challenge> {
        val weightList = arrayListOf<Class<out Challenge>>()
        val challengeTypes = GamePublisherDescriptor.challenges
        challengeTypes.forEach { (clazz, weight) ->
            (0 until weight).forEach { _ ->
                weightList.add(clazz)
            }
        }
        return weightList[Random.nextInt(weightList.size)]
    }

    /**
     * Generates all possible challenges for a specific class in the project.
     */
    fun generateAllPossibleChallengesForClass(selectedClass: SourceFileDetails, parameters: Parameters, user: User,
                                              listener: TaskListener = TaskListener.NULL): List<Challenge> {
        val challenges = arrayListOf<Challenge>()
        var challengeGenerationData = ChallengeGenerationData(parameters, user, selectedClass, listener)

        challenges.add(ClassCoverageChallenge(challengeGenerationData))

        JacocoUtil.getLines(FilePath(selectedClass.jacocoSourceFile)).forEach { line ->
            challengeGenerationData = ChallengeGenerationData(parameters, user, selectedClass, listener, line = line)
            challenges.add(LineCoverageChallenge(challengeGenerationData))
        }

        JacocoUtil.getLines(FilePath(selectedClass.jacocoSourceFile), partially = true).forEach { line ->
            challengeGenerationData = ChallengeGenerationData(parameters, user, selectedClass, listener, line = line)
            challenges.add(BranchCoverageChallenge(challengeGenerationData))
        }

        JacocoUtil.getNotFullyCoveredMethodEntries(FilePath(selectedClass.jacocoMethodFile)).forEach { method ->
            challengeGenerationData = ChallengeGenerationData(parameters, user, selectedClass, listener, method = method)
            challenges.add(MethodCoverageChallenge(challengeGenerationData))
        }

        MutationUtil.getAllAliveMutantsOfClass(selectedClass, parameters, listener).forEach { mutant ->
            challenges.add(MutationChallenge(selectedClass, mutant))
        }

        SmellUtil.getSmellsOfFile(selectedClass, listener).forEach { smell ->
            challenges.add(SmellChallenge(selectedClass, smell))
        }

        return challenges
    }

    /**
     * Generates a new [BuildChallenge] if the [result] was not [Result.SUCCESS] and returns true.
     */
    @JvmStatic
    fun generateBuildChallenge(
        result: Result?, user: User, property: GameUserProperty,
        parameters: Parameters, listener: TaskListener = TaskListener.NULL
    )
            : Boolean {
        try {
            if (result != null && result != Result.SUCCESS) {
                val lastBuildChallenges = property.getCompletedChallenges(parameters.projectName)
                    .filterIsInstance(BuildChallenge::class.java)
                if (lastBuildChallenges.isNotEmpty()
                    && System.currentTimeMillis() - lastBuildChallenges.last().getSolved() <= 604800000) return false
                val challenge = BuildChallenge(parameters)
                val mapUser: User? = GitUtil.mapUser(
                    parameters.workspace.act(HeadCommitCallable(parameters.remote))
                        .authorIdent, User.getAll()
                )

                if (mapUser == user
                    && !property.getCurrentChallenges(parameters.projectName).contains(challenge)
                ) {
                    property.newChallenge(parameters.projectName, challenge)
                    EventHandler.addEvent(ChallengeGeneratedEvent(parameters.projectName, parameters.branch,
                        property.getUser(), challenge))
                    listener.logger.println("[Gamekins] Generated new BuildChallenge")
                    user.save()
                    return true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace(listener.logger)
        }

        return false
    }

    /**
     * Generates a new [Challenge] for the current [user].
     *
     * With a probability of 10% a new [TestChallenge] is generated to keep the user motivated. Otherwise a class
     * is selected by the Rank Selection algorithm from the pool of [files], where the [user] has changed something
     * in his last commits. It is being attempted five times to generate a [CoverageChallenge]. If this fails or if
     * the list of [files] is empty, a new [DummyChallenge] is generated. The workspace is the folder with the
     * code and execution rights, and the [listener] reports the events to the console output of Jenkins.
     */
    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun generateChallenge(
        user: User, parameters: Parameters, listener: TaskListener, files: ArrayList<FileDetails>,
        cla: FileDetails? = null
    ): Challenge {

        val workList = ArrayList(files)

        var challenge: Challenge?
        var count = 0
        do {
            if (count == 5 || workList.isEmpty()) {
                listener.logger.println("[Gamekins] No Challenge could be built")
                //TODO: Generate TestChallenge
                return DummyChallenge(parameters, Constants.Error.GENERATION)
            }
            count++

            val challengeClass = TestParameterChallenge::class.java
            val selectedFile = cla
                ?: if (challengeClass.superclass == CoverageChallenge::class.java) {
                    val tempList = ArrayList(workList.filterIsInstance<SourceFileDetails>())
                    tempList.removeIf { details: SourceFileDetails -> details.coverage == 1.0 }
                    tempList.removeIf { details: SourceFileDetails -> !details.filesExists() }
                    if (tempList.isEmpty()) {
                        challenge = null
                        continue
                    }
                    selectClass(tempList, initializeRankSelection(tempList))
                } else if (challengeClass == MutationChallenge::class.java) {
                    val tempList = workList.filterIsInstance<SourceFileDetails>()
                    if (tempList.isEmpty()) {
                        challenge = null
                        continue
                    }
                    selectClass(tempList, initializeRankSelection(tempList))
                } else if (challengeClass == TestParameterChallenge::class.java) {
                    val testFileDetailsList = workList.filterIsInstance<TestFileDetails>()
                    val filteredList = testFileDetailsList.filter { it.testCount > 0 }
                    if (filteredList.isEmpty()){
                        challenge = null
                        continue
                    }
                    selectClass(filteredList, initializeRankSelection(filteredList))
                }
                else {
                    selectClass(workList, initializeRankSelection(workList))
                }

            workList.remove(selectedFile)

            val rejectedChallenges = user.getProperty(GameUserProperty::class.java)
                .getRejectedChallenges(parameters.projectName)

            //Remove classes where a ClassCoverageChallenge has been rejected previously
            if (rejectedChallenges.any {
                    it.first is ClassCoverageChallenge
                            && (it.first).details.fileName == selectedFile.fileName
                            && (it.first)
                        .details.packageName == selectedFile.packageName }) {
                listener.logger.println(
                    "[Gamekins] Class ${selectedFile.fileName} in package " +
                            "${selectedFile.packageName} was rejected previously"
                )
                challenge = null
                continue
            }

            val storedChallenges = user.getProperty(GameUserProperty::class.java)
                .getStoredChallenges(parameters.projectName)

            //Remove classes where a ClassCoverageChallenge has been stored
            if (!storedChallenges.none {
                    it is ClassCoverageChallenge
                            && it.details.fileName == selectedFile.fileName
                            && it.details.packageName == selectedFile.packageName }) {
                listener.logger.println(
                    "[Gamekins] Class ${selectedFile.fileName} in package " +
                            "${selectedFile.packageName} is currently stored"
                )
                challenge = null
                continue
            }

            val data = ChallengeGenerationData(parameters, user, selectedFile, listener)

            when {
                challengeClass == TestChallenge::class.java -> {
                    challenge = generateTestChallenge(data, parameters, listener)
                }
                challengeClass.superclass == CoverageChallenge::class.java -> {
                    listener.logger.println(
                        TRY_CLASS + selectedFile.fileName + AND_TYPE
                                + challengeClass
                    )
                    challenge = generateCoverageChallenge(data, challengeClass)
                }
                challengeClass == MutationChallenge::class.java -> {
                    listener.logger.println(
                        TRY_CLASS + selectedFile.fileName + AND_TYPE
                                + challengeClass
                    )
                    challenge = generateMutationChallenge(selectedFile as SourceFileDetails, parameters,
                        listener, user)
                }
                challengeClass == SmellChallenge::class.java -> {
                    listener.logger.println(
                        TRY_CLASS + selectedFile.fileName + AND_TYPE
                                + challengeClass
                    )
                    challenge = generateSmellChallenge(data, listener)
                }
                challengeClass == TestParameterChallenge::class.java -> {
                    listener.logger.println(
                        TRY_CLASS + selectedFile.fileName + AND_TYPE
                                + challengeClass
                    )
                    challenge = generateParameterChallenge(data)
                }
                challengeClass == Challenge::class.java -> challenge = null
                challengeClass == CoverageChallenge::class.java -> challenge = null
                else -> {
                    challenge = generateThirdPartyChallenge(data, challengeClass)
                }
            }

            if (rejectedChallenges.any { it.first == challenge }) {
                listener.logger.println("[Gamekins] Challenge ${challenge?.toEscapedString()} was already " +
                        "rejected previously")
                challenge = null
            }

            if (storedChallenges.any { it == challenge }) {
                listener.logger.println("[Gamekins] Challenge ${challenge?.toEscapedString()} is already stored")
                challenge = null
            }

            if (challenge != null && !challenge.builtCorrectly) {
                listener.logger.println("[Gamekins] Challenge ${challenge.toEscapedString()} was not built correctly")
                challenge = null
            }
        } while (challenge == null)

        return challenge
    }

    /**
     * Generates a new [CoverageChallenge] of type [challengeClass] for the current class with details classDetails
     * and the current branch. The workspace is the folder with the code and execution rights, and the listener
     * reports the events to the console output of Jenkins.
     */
    @Throws(IOException::class, InterruptedException::class)
    private fun generateCoverageChallenge(data: ChallengeGenerationData, challengeClass: Class<out Challenge>)
            : Challenge? {

        if (data.selectedFile !is SourceFileDetails) return null
        val document: Document = try {
            JacocoUtil.generateDocument(
                JacocoUtil.calculateCurrentFilePath(
                    data.parameters.workspace,
                    data.selectedFile.jacocoSourceFile, data.selectedFile.parameters.remote
                )
            )
        } catch (e: Exception) {
            data.listener.logger.println(
                "[Gamekins] Exception with JaCoCoSourceFile "
                        + data.selectedFile.jacocoSourceFile.absolutePath
            )
            e.printStackTrace(data.listener.logger)
            throw e
        }

        return if (JacocoUtil.calculateCoveredLines(document, "pc") > 0
            || JacocoUtil.calculateCoveredLines(document, "nc") > 0
        ) {
            when (challengeClass) {
                ClassCoverageChallenge::class.java -> {
                    challengeClass
                        .getConstructor(ChallengeGenerationData::class.java)
                        .newInstance(data)
                }
                MethodCoverageChallenge::class.java -> {
                    data.method = JacocoUtil.chooseRandomMethod(data.selectedFile, data.parameters.workspace)
                    if (data.method == null) null else challengeClass
                        .getConstructor(ChallengeGenerationData::class.java)
                        .newInstance(data)
                }
                BranchCoverageChallenge::class.java -> {
                    data.line = JacocoUtil.chooseRandomLine(data.selectedFile, data.parameters.workspace,
                        partially = true)
                    if (data.line == null) null else challengeClass
                        .getConstructor(ChallengeGenerationData::class.java)
                        .newInstance(data)
                }
                ExceptionCoverageChallenge::class.java -> {
                    data.line = JacocoUtil.chooseExceptionRandomLine(data.selectedFile, data.parameters.workspace)
                    if (data.line == null) null else challengeClass
                        .getConstructor(ChallengeGenerationData::class.java)
                        .newInstance(data)
                }
                else -> {
                    data.line = JacocoUtil.chooseRandomLine(data.selectedFile, data.parameters.workspace)
                    if (data.line == null) null else challengeClass
                        .getConstructor(ChallengeGenerationData::class.java)
                        .newInstance(data)
                }
            }
        } else null
    }

    /**
     * Generates a new [MutationChallenge] for class [fileDetails]. Assumes that the PIT mutation report
     * is stored in <project-root>/target/pit-reports/mutations.xml.
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER", "unused")
    fun generateMutationChallenge(fileDetails: SourceFileDetails, parameters: Parameters,
        listener: TaskListener, user: User) : MutationChallenge? {

        if (!fileDetails.jacocoSourceFile.exists()) return null

        if (!MutationUtil.executePIT(fileDetails, parameters, listener)) return null

        val mutationReport = FilePath(parameters.workspace.channel,
            parameters.workspace.remote + Constants.Mutation.REPORT_PATH)
        if (!mutationReport.exists()) return null
        val mutants = mutationReport.readToString().split("\n")
            .filter { it.startsWith("<mutation ") }.filter { !it.contains("status='KILLED'") }
        if (mutants.isEmpty()) return null

        val mutant = MutationUtil.MutationData(mutants[Random.nextInt(mutants.size)], parameters)
        if (mutant.status == MutationUtil.MutationStatus.KILLED) return null
        return MutationChallenge(fileDetails, mutant)
    }

    /**
     * Generates new Challenges for a [user] if he has less than [maxChallenges] Challenges after checking the solved
     * and solvable state of his Challenges. Returns the number of generated Challenges for debug output.
     */
    @JvmStatic
    fun generateNewChallenges(
        user: User, property: GameUserProperty, parameters: Parameters, files: ArrayList<FileDetails>,
        listener: TaskListener = TaskListener.NULL, maxChallenges: Int = Constants.Default.CURRENT_CHALLENGES
    ): Int {

        var generated = 0
        if (property.getCurrentChallenges(parameters.projectName).size < maxChallenges) {
            listener.logger.println("[Gamekins] Start generating challenges for user ${user.fullName}")

            val userFiles = ArrayList(files)
            userFiles.removeIf { details: FileDetails ->
                !details.changedByUsers.contains(GitUtil.GameUser(user))
            }

            listener.logger.println("[Gamekins] Found ${userFiles.size} last changed files of user ${user.fullName}")

            for (i in property.getCurrentChallenges(parameters.projectName).size until maxChallenges) {
                if (userFiles.size == 0) {
                    property.newChallenge(parameters.projectName,
                        DummyChallenge(parameters, Constants.NOTHING_DEVELOPED))
                    EventHandler.addEvent(ChallengeGeneratedEvent(parameters.projectName, parameters.branch,
                        property.getUser(), DummyChallenge(parameters, Constants.Error.GENERATION)))
                    break
                }

                generated += generateUniqueChallenge(user, property, parameters, userFiles, listener)
            }
        }

        return generated
    }

    /**
     * Generates a [TestChallenge].
     */
    fun generateTestChallenge(data: ChallengeGenerationData, parameters: Parameters, listener: TaskListener)
    : TestChallenge {

        data.testCount = JUnitUtil.getTestCount(parameters.workspace)
        data.headCommitHash = parameters.workspace.act(HeadCommitCallable(parameters.remote)).name
        listener.logger.println("[Gamekins] Generated new TestChallenge")
        return TestChallenge(data)
    }

    /**
     * Generates a new third party [Challenge]. The values listed below in the method may be null and have to be checked
     * in the initialisation of the [Challenge].
     */
    private fun generateThirdPartyChallenge(data: ChallengeGenerationData, challengeClass: Class<out Challenge>)
            : Challenge? {

        data.testCount = JUnitUtil.getTestCount(data.parameters.workspace)
        data.headCommitHash = data.parameters.workspace.act(HeadCommitCallable(data.parameters.remote)).name
        if (data.selectedFile is SourceFileDetails) {
            data.method = JacocoUtil.chooseRandomMethod(data.selectedFile, data.parameters.workspace)
            data.line = JacocoUtil.chooseRandomLine(data.selectedFile, data.parameters.workspace)
        }

        return challengeClass.getConstructor(ChallengeGenerationData::class.java).newInstance(data)
    }

    /**
     * Tries to generate a new unique [Challenge].
     */
    private fun generateUniqueChallenge(
        user: User, property: GameUserProperty, parameters: Parameters, userFiles: ArrayList<FileDetails>,
        listener: TaskListener
    ): Int {
        var generated = 0
        try {
            //Try to generate a new unique Challenge three times. because it can fail
            var challenge: Challenge
            var isChallengeUnique: Boolean
            var count = 0
            do {
                if (count == 3) {
                    //TODO: Generate TestChallenge
                    challenge = DummyChallenge(parameters, Constants.Error.GENERATION)
                    break
                }
                isChallengeUnique = true

                listener.logger.println("[Gamekins] Started to generate challenge")
                challenge = generateChallenge(user, parameters, listener, userFiles)

                listener.logger.println("[Gamekins] Generated challenge ${challenge.toEscapedString()}")
                if (challenge is DummyChallenge) break

                for (currentChallenge in property.getCurrentChallenges(parameters.projectName)) {
                    if (currentChallenge.toString() == challenge.toString()) {
                        isChallengeUnique = false
                        listener.logger.println("[Gamekins] Challenge is not unique")
                        break
                    }
                }
                count++
            } while (!isChallengeUnique)

            property.newChallenge(parameters.projectName, challenge)
            listener.logger.println("[Gamekins] Added challenge ${challenge.toEscapedString()}")
            EventHandler.addEvent(ChallengeGeneratedEvent(parameters.projectName, parameters.branch,
                property.getUser(), challenge))
            generated++
        } catch (e: Exception) {
            e.printStackTrace(listener.logger)
        }

        return generated
    }

    /**
     * Generates a new [SmellChallenge] according the current [data]. Gets all smells of a file and chooses one of
     * them randomly for generation.
     */
    private fun generateSmellChallenge(data: ChallengeGenerationData, listener: TaskListener): SmellChallenge? {
        val issues = SmellUtil.getSmellsOfFile(data.selectedFile!!, listener)

        if (issues.isEmpty()) return null

        return SmellChallenge(data.selectedFile, issues[Random.nextInt(issues.size)])
    }

    /**
     * Initializes the array with the values for the Rank Selection algorithm.
     */
    private fun initializeRankSelection(workList: List<FileDetails>): DoubleArray {
        val c = 1.5
        val rankValues = DoubleArray(workList.size)
        for (i in workList.indices) {
            rankValues[i] = (2 - c + 2 * (c - 1) * (i / (workList.size - 1).toDouble())) / workList.size.toDouble()
            if (i != 0) rankValues[i] += rankValues[i - 1]
        }
        return rankValues
    }

    /**
     * Select a class of the [workList] with the Rank Selection algorithm ([rankValues]).
     */
    private fun selectClass(workList: List<FileDetails>, rankValues: DoubleArray): FileDetails {
        val probability = Random.nextDouble()
        var selectedClass = workList[workList.size - 1]
        for (i in workList.indices) {
            if (rankValues[i] > probability) {
                selectedClass = workList[i]
                break
            }
        }

        return selectedClass
    }

    private fun generateParameterChallenge(data: ChallengeGenerationData): TestParameterChallenge? {


        val teste = (data.selectedFile as TestFileDetails).testCount

        return null
    }
}
