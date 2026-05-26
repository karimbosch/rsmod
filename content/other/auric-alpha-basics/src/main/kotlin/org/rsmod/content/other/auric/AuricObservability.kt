package org.rsmod.content.other.auric

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.game.entity.Player

class AuricObservability
@Inject
constructor() {
    var enabled: Boolean = true
        get() = synchronized(lock) { field }
        private set(value) {
            field = value
        }

    var verbose: Boolean = false
        get() = synchronized(lock) { field }
        private set(value) {
            field = value
        }

    fun record(player: Player?, domain: String, event: String, detail: String) {
        val entry =
            synchronized(lock) {
                if (!enabled) {
                    return
                }
                Entry(
                    time = System.currentTimeMillis(),
                    playerName = player?.username ?: "system",
                    characterId = player?.characterId ?: -1,
                    coords = player?.coords?.toString() ?: "-",
                    domain = domain,
                    event = event,
                    detail = detail,
                ).also {
                    push(globalHistory, it, MaxGlobalEntries)
                    if (player != null) {
                        push(playerHistory.getOrPut(player.characterId) { ArrayDeque() }, it, MaxPlayerEntries)
                    }
                }
            }
        logger.info { entry.format(prefix = "[AURIC_TRACE]") }
    }

    fun playerEntries(characterId: Int, limit: Int = 25): List<String> =
        synchronized(lock) {
            playerHistory[characterId].orEmpty().takeLast(limit.coerceIn(1, MaxPlayerEntries)).map { it.format() }
        }

    fun globalEntries(limit: Int = 50): List<String> =
        synchronized(lock) { globalHistory.takeLast(limit.coerceIn(1, MaxGlobalEntries)).map { it.format() } }

    fun clearPlayer(characterId: Int) {
        synchronized(lock) { playerHistory.remove(characterId) }
    }

    fun clearAll() {
        synchronized(lock) {
            playerHistory.clear()
            globalHistory.clear()
        }
    }

    fun setTracingEnabled(value: Boolean) {
        synchronized(lock) { enabled = value }
        logger.info { "[AURIC_TRACE][CONFIG] enabled=$value verbose=$verbose" }
    }

    fun setVerboseTracing(value: Boolean) {
        synchronized(lock) { verbose = value }
        logger.info { "[AURIC_TRACE][CONFIG] enabled=$enabled verbose=$value" }
    }

    private fun push(target: ArrayDeque<Entry>, entry: Entry, max: Int) {
        target += entry
        while (target.size > max) {
            target.removeFirst()
        }
    }

    private data class Entry(
        val time: Long,
        val playerName: String,
        val characterId: Int,
        val coords: String,
        val domain: String,
        val event: String,
        val detail: String,
    ) {
        fun format(prefix: String = ""): String {
            val marker = if (prefix.isBlank()) "" else "$prefix "
            return "${marker}t=$time domain=$domain event=$event player='$playerName' character=$characterId coords=$coords $detail"
        }
    }

    private companion object {
        private val logger = InlineLogger()
        private val lock = Any()
        private val playerHistory = mutableMapOf<Int, ArrayDeque<Entry>>()
        private val globalHistory = ArrayDeque<Entry>()
        private const val MaxPlayerEntries = 300
        private const val MaxGlobalEntries = 1000
    }
}
