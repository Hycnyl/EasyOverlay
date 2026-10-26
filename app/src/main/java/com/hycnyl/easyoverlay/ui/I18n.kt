package com.hycnyl.easyoverlay.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.hycnyl.easyoverlay.R
import java.util.Locale

/** Uses Android application resources so callbacks and Compose resolve the same language. */
class Strings(private val context: Context) {
    operator fun invoke(@StringRes id: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

    /** Localizes stable domain/storage messages without Android dependencies in pure domain code. */
    fun message(value: String): String = messageIds(context)[value]?.let { context.getString(it) } ?: value

    fun displayName(id: Int): String = if (id == 0) invoke(R.string.display_main) else invoke(R.string.display_secondary, id)

    fun source(value:String):String=invoke(when(value) {
        "ACCESSIBILITY"->R.string.source_accessibility;"SHIZUKU"->R.string.source_shizuku
        "USAGE"->R.string.source_usage;else->R.string.source_none
    })
    fun problem(value:String):String=when {
        value=="OPTIMIZATION_STATE_CHANGED_REVIEW_AGAIN"->invoke(R.string.operation_state_changed)
        value=="RESTORE_PREVIOUS_OPTIMIZATION_FIRST"->invoke(R.string.restore_previous_first)
        value=="SESSION_INTENT_SAVE_FAILED"->invoke(R.string.error_session_save)
        value=="MONITOR_PERMISSION_MISSING"->invoke(R.string.monitor_need_source)
        value=="WAITING_FOREGROUND_OBSERVATION"->invoke(R.string.monitor_waiting)
        value=="SCREEN_LOCKED"->invoke(R.string.screen_locked)
        value=="USAGE_PERMISSION_MISSING"->invoke(R.string.usage_revoked)
        value=="OVERLAY_PERMISSION_MISSING"->invoke(R.string.overlay_revoked)
        value=="RECOVERY_BACKOFF"->invoke(R.string.recovery_backoff)
        value=="BACKGROUND_START_NOT_ALLOWED" || value=="RECOVERY_OPEN_APP_REQUIRED"->invoke(R.string.recovery_needed_body)
        value.startsWith("SHIZUKU_") || value.startsWith("DHIZUKU_")->invoke(R.string.authorization_unavailable)
        value.startsWith("ACCESSIBILITY_") || value=="USAGE_QUERY_FAILED"->invoke(R.string.backend_restricted)
        value=="NO_RESTORE_POINT" || value=="UID_CHANGED" || value=="DEVICE_OWNER_CHANGED"->invoke(R.string.operation_state_changed)
        else->message(value)
    }

    companion object {
        @Volatile private var messages: Map<String, Int>? = null
        private fun messageIds(context: Context): Map<String, Int> {
            messages?.let { return it }
            return synchronized(this) {
                messages ?: buildMap {
                    listOf(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE).forEach { locale ->
                        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
                        val resources = context.createConfigurationContext(configuration).resources
                        knownMessageIds.forEach { id -> put(resources.getString(id), id) }
                    }
                }.also { messages = it }
            }
        }
        private val knownMessageIds = intArrayOf(
            R.string.app_name,
            R.string.hardware_service_name,
            R.string.accessibility_keybinding_desc,
            R.string.quick_title,
            R.string.quick_subtitle,
            R.string.choose_image,
            R.string.replace_image,
            R.string.image_library,
            R.string.clear_recent,
            R.string.touch_priority,
            R.string.manual_priority_help,
            R.string.retry_show,
            R.string.overlay_limit_help,
            R.string.stop_quick,
            R.string.cancel_start,
            R.string.show_overlay,
            R.string.notification_denied_message,
            R.string.settings_open_failed,
            R.string.request_overlay_message,
            R.string.request_usage_message,
            R.string.advanced_title,
            R.string.diagnostics_title,
            R.string.about_title,
            R.string.back,
            R.string.open_diagnostics,
            R.string.tab_quick,
            R.string.tab_rules,
            R.string.tab_settings,
            R.string.open_repair,
            R.string.rules_title,
            R.string.monitor_on,
            R.string.monitor_offer,
            R.string.rules_intro,
            R.string.manage_monitor,
            R.string.enable_monitor,
            R.string.add_rule,
            R.string.search_apps,
            R.string.no_rules,
            R.string.no_matching_apps,
            R.string.rules_empty_help,
            R.string.auto_enable_rule,
            R.string.remove_rule_title,
            R.string.remove_rule_help,
            R.string.remove,
            R.string.cancel,
            R.string.choose_app,
            R.string.app_search_hint,
            R.string.configured,
            R.string.import_image,
            R.string.choose_from_library,
            R.string.stretch_help,
            R.string.rule_saved_help,
            R.string.secondary_rule_help,
            R.string.save_app_overlay,
            R.string.library_intro,
            R.string.library_empty,
            R.string.choose_this_image,
            R.string.in_use,
            R.string.merge_backup_title,
            R.string.merge_import,
            R.string.settings_title,
            R.string.permissions_monitoring,
            R.string.monitor_session,
            R.string.monitor_running_detail,
            R.string.monitor_paused_detail,
            R.string.monitor_off_detail,
            R.string.session_ended_help,
            R.string.display_over_apps,
            R.string.allowed,
            R.string.overlay_permission_needed,
            R.string.usage_access,
            R.string.usage_allowed_detail,
            R.string.usage_denied_detail,
            R.string.session_notifications,
            R.string.notifications_allowed_detail,
            R.string.notifications_denied_detail,
            R.string.appearance,
            R.string.follow_system,
            R.string.theme_light,
            R.string.theme_dark,
            R.string.dynamic_theme_help,
            R.string.language_title,
            R.string.language_help,
            R.string.language_chinese,
            R.string.language_english,
            R.string.images_backups,
            R.string.private_storage_help,
            R.string.library_unassigned,
            R.string.library_settings_help,
            R.string.export_backup,
            R.string.export_backup_help,
            R.string.import_backup,
            R.string.import_backup_help,
            R.string.more_controls,
            R.string.diagnostics_settings_help,
            R.string.advanced_settings_help,
            R.string.stop_all_sessions,
            R.string.toggle_main,
            R.string.toggle_secondary,
            R.string.toggle_all,
            R.string.toggle_ball,
            R.string.displays,
            R.string.cover_cutout,
            R.string.cutout_help,
            R.string.floating_control,
            R.string.floating_help,
            R.string.show_ball,
            R.string.currently_shown,
            R.string.currently_off,
            R.string.hardware_shortcuts,
            R.string.hardware_help,
            R.string.hardware_permission,
            R.string.optional_permission_denied,
            R.string.not_bound,
            R.string.quick_settings_tile,
            R.string.tile_help,
            R.string.hardware_record_help,
            R.string.waiting_keys,
            R.string.clear_binding,
            R.string.save,
            R.string.current_status,
            R.string.background_restricted_help,
            R.string.overlay_revoked,
            R.string.usage_revoked,
            R.string.screen_locked,
            R.string.background_start_denied,
            R.string.monitor_restricted_help,
            R.string.show_package_here,
            R.string.manual_state_help,
            R.string.permission_check,
            R.string.overlay_permission,
            R.string.not_allowed,
            R.string.usage_optional_needed,
            R.string.notifications,
            R.string.manual_mode,
            R.string.rule_mode,
            R.string.closed,
            R.string.none,
            R.string.unknown,
            R.string.no_record,
            R.string.no_known_error,
            R.string.no_mount_error,
            R.string.export_troubleshoot,
            R.string.diagnostics_privacy,
            R.string.diagnostics_copied,
            R.string.copy_summary,
            R.string.export_diagnostics,
            R.string.stopped_help,
            R.string.about_fork,
            R.string.about_sources,
            R.string.about_libraries,
            R.string.about_validation,
            R.string.preview_failed,
            R.string.preview_empty,
            R.string.supported_images,
            R.string.opacity,
            R.string.touch_safety_title,
            R.string.understood,
            R.string.quick_running,
            R.string.rule_running,
            R.string.image_preparing,
            R.string.overlay_closed,
            R.string.session_state_off,
            R.string.session_state_on,
            R.string.state_paused,
            R.string.operation_failed,
            R.string.apps_unavailable,
            R.string.busy_import_image,
            R.string.busy_select_image,
            R.string.busy_clear_recent,
            R.string.busy_opacity,
            R.string.busy_save_app_rule,
            R.string.choose_image_first,
            R.string.app_overlay_saved,
            R.string.busy_remove_rule,
            R.string.busy_save_rule,
            R.string.busy_remove_asset,
            R.string.busy_theme,
            R.string.busy_display_settings,
            R.string.busy_keys,
            R.string.keys_saved,
            R.string.busy_export_backup,
            R.string.backup_exported,
            R.string.busy_validate_backup,
            R.string.busy_merge_backup,
            R.string.backup_imported,
            R.string.busy_export_diagnostics,
            R.string.diagnostics_write_failed,
            R.string.diagnostics_exported,
            R.string.hardware_limit,
            R.string.hardware_overlap,
            R.string.gif_animated,
            R.string.display_main,
            R.string.diag_secondary,
            R.string.diag_events,
            R.string.yes,
            R.string.no,
            R.string.error_image_size,
            R.string.error_image_format,
            R.string.error_webp_incomplete,
            R.string.error_webp_chunk,
            R.string.error_webp_animated,
            R.string.error_webp_header,
            R.string.error_webp_animated_short,
            R.string.error_webp_length,
            R.string.error_image_dimensions,
            R.string.error_picker_access,
            R.string.error_image_permission,
            R.string.error_image_open,
            R.string.error_image_bytes,
            R.string.error_image_too_large,
            R.string.error_index_large,
            R.string.error_index_empty,
            R.string.error_index_read,
            R.string.error_asset_path,
            R.string.error_save,
            R.string.error_asset_directory,
            R.string.error_asset_save,
            R.string.error_asset_in_use,
            R.string.error_backup_image_save,
            R.string.backup_conflict,
            R.string.error_backup_write,
            R.string.error_backup_missing_asset,
            R.string.error_backup_staging,
            R.string.error_backup_read,
            R.string.error_backup_entries,
            R.string.error_backup_paths,
            R.string.error_backup_file_size,
            R.string.error_backup_total_size,
            R.string.error_backup_manifest,
            R.string.error_backup_format,
            R.string.error_backup_asset_list,
            R.string.error_backup_integrity,
            R.string.error_schema,
            R.string.error_library_count,
            R.string.error_duplicate_asset,
            R.string.error_duplicate_rule,
            R.string.error_duplicate_quick,
            R.string.error_asset_id,
            R.string.error_asset_type,
            R.string.error_asset_dimensions,
            R.string.error_asset_checksum,
            R.string.error_package,
            R.string.error_rule_reference,
            R.string.error_quick_reference,
            R.string.error_unassigned_reference,
            R.string.error_overlay_prepare,
            R.string.error_display_missing,
            R.string.error_overlay_permission,
            R.string.error_service_background,
            R.string.error_service_permission,
            R.string.error_service_start
        )
    }
}

@Composable fun rememberStrings(): Strings {
    val context = LocalContext.current
    val locales = LocalConfiguration.current.locales.toLanguageTags()
    return remember(context, locales) { Strings(context) }
}

enum class AppLanguage(val tag: String) { SYSTEM(""), SIMPLIFIED_CHINESE("zh-CN"), ENGLISH("en") }

object AppLanguages {
    fun current(context: Context): AppLanguage = when (context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()) {
        "zh-CN" -> AppLanguage.SIMPLIFIED_CHINESE
        "en" -> AppLanguage.ENGLISH
        else -> AppLanguage.SYSTEM
    }
    fun select(context: Context, language: AppLanguage) {
        val manager = context.getSystemService(LocaleManager::class.java)
        val locales = LocaleList.forLanguageTags(language.tag)
        if (manager.applicationLocales != locales) manager.applicationLocales = locales
    }
}
