package com.hycnyl.easyoverlay.domain

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface OverlayCommand {
    data class StartQuick(val displayId: Int) : OverlayCommand
    data class StopQuick(val displayId: Int) : OverlayCommand
    data class Toggle(val displayId: Int) : OverlayCommand
    data object ToggleAll : OverlayCommand
    data class SetAuto(val enabled: Boolean) : OverlayCommand
    data class Observe(val packageName: String?, val at: Long, val problem: String?) : OverlayCommand
    data class Displays(val ids: Set<Int>) : OverlayCommand
    data class Refresh(val retry: Boolean = true) : OverlayCommand
    data class SetBall(val enabled: Boolean) : OverlayCommand
    data object ToggleBall : OverlayCommand
    data object StopAll : OverlayCommand
    data class Prepared(val target: OverlayTarget, val generation: Long, val image: PreparedOverlay) : OverlayCommand
    data class Failed(val target: OverlayTarget, val generation: Long) : OverlayCommand
}

/** The only writer of desired and mounted session state. All transitions run through this actor. */
class OverlayCoordinator(
    private val scope: CoroutineScope,
    private val library: StateFlow<LibraryState>,
    private val engine: OverlayEngine,
    private val state: MutableStateFlow<RuntimeState>,
    private val ownPackage: String,
    private val maxAlpha: () -> Float,
    private val overlayAllowed: () -> Boolean,
    private val usageAllowed: () -> Boolean,
    private val monitor: (Boolean) -> Unit,
    private val floating: (Boolean) -> Boolean,
    private val log: (String, Int?) -> Unit,
    private val onIdle: () -> Unit
) {
    private val commands = Channel<OverlayCommand>(Channel.UNLIMITED)
    private val manual = mutableMapOf<Int, OverlayTarget>()
    private val suppressed = mutableSetOf<Pair<Int, String>>()
    private var displays = setOf(0)
    private val pending = mutableMapOf<Int, Pair<OverlayTarget, Long>>()
    private val jobs = mutableMapOf<Int, Job>()
    private val failed = mutableMapOf<Int, OverlayTarget>()
    private val mountedAt = mutableMapOf<Int, Long>()
    private var generation = 0L
    private var auto = false
    private var foreground: String? = null
    private var observedAt = 0L
    private var monitorProblem: String? = null
    private var ball = false
    private var closed = false

    init {
        state.value = RuntimeState(serviceRunning = true)
        scope.launch { for (command in commands) {
            try { handle(command) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { log("COMMAND_FAILED", null); publish() }
        } }
    }

    fun offer(command: OverlayCommand) {
        if (closed || !commands.trySend(command).isSuccess) (command as? OverlayCommand.Prepared)?.image?.close()
    }

    private fun handle(command: OverlayCommand) {
        when (command) {
            is OverlayCommand.StartQuick -> startQuick(command.displayId)
            is OverlayCommand.StopQuick -> { manual.remove(command.displayId); failed.remove(command.displayId) }
            is OverlayCommand.Toggle -> toggle(command.displayId)
            OverlayCommand.ToggleAll -> {
                if (engine.targets().isNotEmpty() || manual.isNotEmpty()) {
                    displays.forEach { id -> manual.remove(id); foreground?.let { suppressed += id to it } }
                } else displays.forEach { startQuick(it) }
            }
            is OverlayCommand.SetAuto -> {
                if (command.enabled && (!usageAllowed() || !overlayAllowed())) {
                    monitorProblem = if (!usageAllowed()) "USAGE_PERMISSION_MISSING" else "OVERLAY_PERMISSION_MISSING"
                    log(monitorProblem!!, null)
                } else {
                    auto = command.enabled; foreground = null; observedAt = 0; monitorProblem = null
                    suppressed.clear(); monitor(auto); log(if (auto) "MONITOR_STARTED" else "MONITOR_STOPPED", null)
                }
            }
            is OverlayCommand.Observe -> {
                if (!auto) return
                if (foreground != command.packageName) { suppressed.clear(); log("FOREGROUND_CHANGED", null) }
                if (monitorProblem != command.problem && command.problem != null) log(command.problem, null)
                foreground = command.packageName; observedAt = command.at; monitorProblem = command.problem
            }
            is OverlayCommand.Displays -> {
                displays = command.ids
                manual.keys.retainAll(displays)
                runCatching { engine.resize() }.onFailure { log("DISPLAY_RESIZE_FAILED", null) }
            }
            is OverlayCommand.Refresh -> {
                if (command.retry) failed.clear()
                manual.keys.toList().forEach { display ->
                    val selected = library.value.quickSelections.firstOrNull { it.displayId == display }
                    if (selected == null) manual.remove(display)
                    else manual[display] = OverlayTarget(display, OverlayOwner.QUICK_USE, selected.assetId, selected.opacityPercent)
                }
            }
            is OverlayCommand.SetBall -> { ball = floating(command.enabled); log(if (ball) "BALL_SHOWN" else "BALL_HIDDEN", null) }
            OverlayCommand.ToggleBall -> { ball = floating(!ball) }
            OverlayCommand.StopAll -> {
                auto = false; monitor(false); manual.clear(); suppressed.clear(); foreground = null; monitorProblem = null
                ball = floating(false); failed.clear(); log("USER_STOP_ALL", null)
            }
            is OverlayCommand.Prepared -> {
                val expected = pending[command.target.displayId]
                if (expected != (command.target to command.generation)) {
                    command.image.close(); log("STALE_IMAGE_DISCARDED", command.target.displayId); return
                }
                pending.remove(command.target.displayId); jobs.remove(command.target.displayId)
                try {
                    engine.mount(command.target, command.image)
                    mountedAt[command.target.displayId] = System.currentTimeMillis()
                    failed.remove(command.target.displayId)
                    log("WINDOW_MOUNTED", command.target.displayId)
                } catch (_: Exception) {
                    command.image.close(); failed[command.target.displayId] = command.target
                    log("WINDOW_MOUNT_FAILED", command.target.displayId)
                }
            }
            is OverlayCommand.Failed -> {
                if (pending[command.target.displayId] == (command.target to command.generation)) {
                    pending.remove(command.target.displayId); jobs.remove(command.target.displayId)
                    failed[command.target.displayId] = command.target
                    log("IMAGE_PREPARE_FAILED", command.target.displayId)
                }
            }
        }
        reconcile()
    }

    private fun startQuick(display: Int) {
        if (display !in displays || !overlayAllowed()) { log("OVERLAY_PERMISSION_OR_DISPLAY_MISSING", display); return }
        val quick = library.value.quickSelections.firstOrNull { it.displayId == display }
        if (quick == null) { log("NO_QUICK_IMAGE", display); return }
        manual[display] = OverlayTarget(display, OverlayOwner.QUICK_USE, quick.assetId, quick.opacityPercent)
        failed.remove(display)
    }
    private fun toggle(display: Int) {
        if (manual.containsKey(display) || engine.targets()[display] != null) {
            manual.remove(display); failed.remove(display)
            foreground?.let { suppressed += display to it }
        } else { foreground?.let { suppressed.remove(display to it) }; startQuick(display) }
    }

    private fun reconcile() {
        val permitted = overlayAllowed()
        if (!permitted) {
            if (manual.isNotEmpty() || engine.targets().isNotEmpty()) log("OVERLAY_PERMISSION_MISSING", null)
            manual.clear(); ball = floating(false)
        }
        val desired = if (permitted) RuleResolver.resolve(displays, manual, library.value.rules, foreground,
            auto && usageAllowed() && monitorProblem == null, ownPackage, maxAlpha(), suppressed) else emptyMap()
        val existing = engine.targets()
        (existing.keys + pending.keys).toSet().filterNot { it in desired }.forEach { display ->
            jobs.remove(display)?.cancel(); pending.remove(display); failed.remove(display)
            try { engine.remove(display); mountedAt.remove(display) }
            catch (_: Exception) { log("WINDOW_REMOVE_FAILED", display) }
        }
        desired.forEach { (display, target) ->
            if (existing[display] == target) {
                jobs.remove(display)?.cancel(); pending.remove(display)
            } else if (pending[display]?.first != target && failed[display] != target) {
                jobs.remove(display)?.cancel()
                val token = ++generation
                pending[display] = target to token
                log("IMAGE_PREPARING", display)
                jobs[display] = scope.launch {
                    try {
                        val asset = library.value.assets.firstOrNull { it.id == target.assetId } ?: error("missing asset")
                        val image = engine.prepare(asset, display)
                        offer(OverlayCommand.Prepared(target, token, image))
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { offer(OverlayCommand.Failed(target, token)) }
                }
            }
        }
        publish()
        if (!auto && manual.isEmpty() && !ball && pending.isEmpty() && engine.targets().isEmpty()) onIdle()
    }

    private fun publish() {
        val actual = engine.targets()
        state.value = RuntimeState(true, auto, foreground, observedAt,
            actual.mapValues { (display, target) -> ActiveOverlaySession(target, mountedAt[display] ?: 0) },
            pending.keys.toSet(), failed.mapValues { "图片准备或窗口挂载失败，已尽量保留原图。请检查图片、悬浮窗权限和目标屏幕后重试。" },
            monitorProblem, ball)
    }

    fun close() {
        if (closed) return
        closed = true; monitor(false); floating(false)
        jobs.values.forEach { it.cancel() }; jobs.clear(); pending.clear()
        commands.close()
        while (true) { val command = commands.tryReceive().getOrNull() ?: break; (command as? OverlayCommand.Prepared)?.image?.close() }
        engine.close(); state.value = RuntimeState()
    }
}
