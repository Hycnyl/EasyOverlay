package com.hycnyl.easyoverlay.platform.privileged

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Restore points contain only this app's original policy values, never credentials or shell text. */
internal class SelfOptimizationStore(context: Context, name: String) {
    private val ownUid = context.applicationInfo.uid
    private val file = AtomicFile(File(context.filesDir, name))
    suspend fun read(): JSONObject? = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return@withContext null
        val raw = file.openRead().use { input -> input.readNBytes(64 * 1024 + 1) }
        require(raw.size <= 64 * 1024) { "RESTORE_POINT_INVALID" }
        val value = JSONObject(raw.toString(Charsets.UTF_8))
        require(value.getInt("uid") == ownUid && value.getString("packageName") == OptimizationSnapshot.OWN_PACKAGE) { "RESTORE_POINT_UID_CHANGED" }
        value
    }
    suspend fun write(value: JSONObject) = withContext(Dispatchers.IO) {
        require(value.getInt("uid") == ownUid && value.getString("packageName") == OptimizationSnapshot.OWN_PACKAGE)
        var output: java.io.FileOutputStream? = null
        try { output = file.startWrite(); output.write(value.toString().toByteArray()); file.finishWrite(output) }
        catch (error: Exception) { output?.let { file.failWrite(it) }; throw error }
    }
    suspend fun clear() = withContext(Dispatchers.IO) { file.delete() }
}
