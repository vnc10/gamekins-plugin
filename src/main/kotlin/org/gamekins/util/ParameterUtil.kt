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

package org.gamekins.util

object ParameterUtil {

    @JvmStatic
    fun getTestName(testsName: HashSet<String>): String? {

        return testsName.filter { !isValidTestFormat(it) }.randomOrNull()

    }

    private fun isValidTestFormat(testName: String): Boolean {
        val regex = Regex("""\((int|String|double|boolean|char|long|float|short|byte|Integer|Long|Float|Double|Boolean|Character|Short|Byte)\)\[(\d|1\d|20)\]$""")
        return regex.containsMatchIn(testName)
    }

    @JvmStatic
    fun getTest(testsName: String, testsCodes: HashMap<String, String>): String? {

        return testsCodes.get(testsName)

    }
}