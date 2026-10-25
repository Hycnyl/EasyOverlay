package com.hycnyl.easyoverlay.data

import android.content.Context
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

data class DiagnosticEvent(val at: Long, val code: String, val displayId: Int? = null)

/** Contains only fixed codes and display IDs: no paths, URIs, app names or image names. */
class EventLog(context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "diagnostic-events.json"))
    private val gson = Gson()
    private val writeLock = Mutex()
    private val _events = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val events = _events.asStateFlow()
    init {
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                val previous: List<DiagnosticEvent> = runCatching { file.openRead().use {
                    require(it.available() <= 128 * 1024)
                    gson.fromJson<List<DiagnosticEvent>>(it.reader(), object : TypeToken<List<DiagnosticEvent>>() {}.type)
                } }.getOrDefault(emptyList())
                synchronized(this@EventLog) { _events.value = (previous + _events.value).takeLast(100) }
            }
        }
    }
    @Synchronized fun record(code: String, displayId: Int? = null) {
        require(code.matches("[A-Z0-9_]{1,64}".toRegex()))
        _events.value = (_events.value + DiagnosticEvent(System.currentTimeMillis(), code, displayId)).takeLast(100)
        scope.launch(Dispatchers.IO) {
            writeLock.withLock {
                var stream: java.io.FileOutputStream? = null
                try { stream = file.startWrite(); stream.write(gson.toJson(_events.value).toByteArray()); file.finishWrite(stream) }
                catch (_: Exception) { stream?.let { file.failWrite(it) } }
            }
        }
    }
}
