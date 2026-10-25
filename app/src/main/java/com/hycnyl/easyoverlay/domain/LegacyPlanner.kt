package com.hycnyl.easyoverlay.domain

import com.google.gson.JsonParser
import java.security.MessageDigest

data class LegacyItem(val key: String, val groupName: String, val configName: String,
    val imageReference: String, val packageName: String?, val secondary: Boolean,
    val preferred: Boolean, val groupId: String)
data class LegacyPlan(val groupCount: Int, val items: List<LegacyItem>, val candidates: Set<String>)

/** Only interprets historical JSON. No dependency on the upstream loader/writer. */
object LegacyPlanner {
    fun parse(json: String): LegacyPlan {
        require(json.length <= 8 * 1024 * 1024) { "旧配置文件过大" }
        val root = JsonParser.parseString(json)
        require(root.isJsonArray && root.asJsonArray.size() <= 2000) { "不是可识别的 ImageOverlay 配置列表" }
        val items = mutableListOf<LegacyItem>()
        root.asJsonArray.forEachIndexed { index, element ->
            val group = element.asJsonObject
            fun text(name: String) = group.get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            val groupName = text("groupName").take(256)
            val packageName = text("boundPackageName").takeIf { it.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+".toRegex()) }
            val groupId = text("id").ifBlank { "group-$index-$groupName" }
            val secondary = text("screenType") == "secondary"
            val configs = group.getAsJsonArray("configs") ?: return@forEachIndexed
            require(configs.size() <= 2000 && items.size + configs.size() <= 4000) { "旧配置中的图片数量过多" }
            configs.forEach { configElement ->
                val config = configElement.asJsonObject
                val name = config.get("configName")?.asString.orEmpty().take(256)
                val reference = config.get("imageUri")?.asString.orEmpty()
                require(reference.length <= 8192) { "旧配置图片路径过长" }
                val preferred = config.get("isDefault")?.takeIf { it.isJsonPrimitive }?.asBoolean == true ||
                    (text("defaultConfigName").isNotBlank() && text("defaultConfigName") == name)
                val key = MessageDigest.getInstance("SHA-256").digest(
                    "$groupId\u0000$packageName\u0000$secondary\u0000$name\u0000$reference".toByteArray())
                    .joinToString("") { "%02x".format(it) }
                items += LegacyItem(key, groupName, name, reference, packageName, secondary, preferred, groupId)
            }
        }
        val candidates = mutableSetOf<String>()
        items.filter { it.packageName != null }.groupBy { it.packageName to it.secondary }.values.forEach { sameTarget ->
            val preferred = sameTarget.filter { it.preferred }
            when {
                preferred.size == 1 -> candidates += preferred.single().key
                preferred.isEmpty() && sameTarget.size == 1 -> candidates += sameTarget.single().key
                // Multiple defaults or alternatives remain explicit unresolved assets.
            }
        }
        return LegacyPlan(root.asJsonArray.size(), items.distinctBy { it.key }, candidates)
    }

    /** Resolve old absolute/file references only by matching a path inside the newly granted tree. */
    fun relativeCandidates(reference: String, groupName: String, configName: String): List<String> {
        if (reference.indexOf('\u0000') >= 0) return emptyList()
        val path = runCatching { java.net.URI(reference).path ?: reference }.getOrDefault(reference)
        val parts = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        if (parts.any { it == ".." || it == "." }) return emptyList()
        val result = mutableListOf<String>()
        val marker = parts.indexOfLast { it == "ImageOverlay" }
        if (marker >= 0) result += parts.drop(marker + 1).joinToString("/")
        if (!path.startsWith('/') && !reference.contains("://")) result += parts.joinToString("/")
        if (groupName.isNotBlank() && !groupName.contains('/') && !groupName.contains('\\') && groupName != "..") {
            parts.lastOrNull()?.let { result += "$groupName/$it" }
            if (configName.isNotBlank() && !configName.contains('/') && !configName.contains('\\') && configName != "..") {
                listOf("png", "gif", "jpg", "jpeg", "webp").forEach { result += "$groupName/$configName.$it" }
            }
        }
        return result.filter { it.isNotBlank() }.distinct()
    }
}
