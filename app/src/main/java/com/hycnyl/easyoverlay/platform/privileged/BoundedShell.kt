package com.hycnyl.easyoverlay.platform.privileged

import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Fixed argv only; callers never pass user text, a shell expression or an arbitrary package. */
internal class BoundedShell {
    data class Result(val exitCode: Int, val output: String, val error: String)
    private val readers = Executors.newFixedThreadPool(2) { runnable -> Thread(runnable, "EasyOverlay-shell-io").apply { isDaemon = true } }
    fun run(vararg arguments: String, timeoutMs: Long = 1600, limit: Int = 128 * 1024): Result {
        require(arguments.isNotEmpty() && arguments[0] in setOf("/system/bin/cmd", "/system/bin/pm", "/system/bin/dumpsys"))
        val process = ProcessBuilder(*arguments).start()
        val stdout = readers.submit<String> { readBounded(process.inputStream, limit) }
        val stderr = readers.submit<String> { readBounded(process.errorStream, 16 * 1024) }
        try {
            check(process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) { "COMMAND_TIMEOUT" }
            return Result(process.exitValue(), stdout.get(200, TimeUnit.MILLISECONDS), stderr.get(200, TimeUnit.MILLISECONDS))
        } finally {
            process.destroyForcibly()
            runCatching { process.inputStream.close() }; runCatching { process.errorStream.close() }; runCatching { process.outputStream.close() }
            stdout.cancel(true); stderr.cancel(true)
        }
    }
    private fun readBounded(input: InputStream, limit: Int): String = input.use {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = it.read(buffer)
            if (size < 0) break
            require(out.size() + size <= limit) { "COMMAND_OUTPUT_LIMIT" }
            out.write(buffer, 0, size)
        }
        out.toString(Charsets.UTF_8.name())
    }
    fun close() { readers.shutdownNow() }
}
