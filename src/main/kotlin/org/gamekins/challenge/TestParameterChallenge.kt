package org.gamekins.challenge

import hudson.model.Run
import hudson.model.TaskListener
import org.gamekins.util.Constants

class TestParameterChallenge() : Challenge {
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
        TODO("Not yet implemented")
    }

    override fun getScore(): Int {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}