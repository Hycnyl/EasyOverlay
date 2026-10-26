package com.hycnyl.easyoverlay.platform.privileged

/** Conservative parsers for Android's shell text. Unknown/ambiguous formats fail closed. */
internal object PrivilegedSnapshotParsers {
    data class UsageModes(val packageMode: String, val uidMode: String)
    private val mode = Regex("GET_USAGE_STATS:\\s*(allow|ignore|deny|default|foreground)\\b")
    private val display = Regex("^\\s*Display\\s+#(\\d+)\\b")
    private val component = Regex("([A-Za-z][A-Za-z0-9_.]+)/(\\.?[A-Za-z0-9_.$]+)")

    fun usageModes(output: String): UsageModes {
        var packageMode = "default"
        var uidMode = "default"
        var recognized = false
        for (line in output.lineSequence()) {
            val parsed = mode.find(line)?.groupValues?.get(1) ?: continue
            recognized = true
            if (line.trimStart().startsWith("Uid mode:")) uidMode = parsed else packageMode = parsed
        }
        require(recognized || "No operations" in output) { "APP_OPS_UNRECOGNIZED" }
        return UsageModes(packageMode, uidMode)
    }

    fun batteryWhitelist(output: String, ownPackage: String): Pair<Boolean, Boolean> {
        var user = false
        var system = false
        for (line in output.lineSequence()) {
            val fields = line.trim().split(',')
            if (fields.size >= 2 && fields[1] == ownPackage) {
                if (fields[0] == "user") user = true
                if (fields[0] == "system") system = true
            }
        }
        return user to system
    }

    fun defaultDisplayComponent(output: String): String? {
        var currentDisplay: Int? = null
        var hasDisplayHeaders = false
        val scoped = linkedSetOf<String>()
        val unscoped = linkedSetOf<String>()
        for (line in output.lineSequence()) {
            display.find(line)?.let {
                hasDisplayHeaders = true
                currentDisplay = it.groupValues[1].toIntOrNull()
            }
            if ("topResumedActivity=" !in line && "mResumedActivity:" !in line && "mResumedActivity=" !in line) continue
            val value = component.find(line)?.value ?: continue
            if (currentDisplay == 0) scoped += value
            else if (currentDisplay == null) unscoped += value
        }
        // Legacy dumps with no display markers are accepted only when all resumed entries agree.
        return if (hasDisplayHeaders) scoped.singleOrNull() else unscoped.singleOrNull()
    }
}
