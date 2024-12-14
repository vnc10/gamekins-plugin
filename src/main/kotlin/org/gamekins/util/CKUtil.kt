package org.gamekins.util

import com.github.mauricioaniche.ck.CK
import com.github.mauricioaniche.ck.CKClassResult
import com.github.mauricioaniche.ck.CKNotifier

object CKUtil {
    fun getCKMetricsResult(dir: String?): Map<String, CKClassResult> {
        val map: MutableMap<String, CKClassResult> = HashMap()
        CK().calculate(dir, object : CKNotifier {
            override fun notify(result: CKClassResult) {
                map[result.className] = result
            }

            override fun notifyError(sourceFilePath: String, e: Exception) {
                System.err.println("Error in $sourceFilePath")
                e.printStackTrace(System.err)
            }
        })
        return map
    }
}