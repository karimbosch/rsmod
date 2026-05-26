package org.rsmod.content.other.auric

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.config.refs.BaseInvs
import org.rsmod.annotations.InternalApi
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.objs
import org.rsmod.api.config.refs.stats
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.shops.Shops
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.type.obj.ObjType
import org.rsmod.game.type.obj.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AuricAlphaBasicsScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val playerList: PlayerList,
    private val observability: AuricObservability,
    private val shops: Shops,
) : PluginScript() {
    private val logger = InlineLogger()

    override fun ScriptContext.startup() {
        onPlayerLogin {
            player.applyAuricRankAppearance()
            observability.record(
                player,
                "SESSION",
                "LOGIN",
                "display='${player.displayName}' account=${player.accountId} modlevel=${player.modLevel.internalName}",
            )
        }
        onPlayerLogout {
            observability.record(
                player,
                "SESSION",
                "LOGOUT",
                "manual=${player.manualLogout} disconnected=${player.clientDisconnected.get()} shutdown=${player.pendingShutdown}",
            )
        }

        Bone.entries.forEach { bone -> onOpHeld1(bone.obj) { buryBones(it.type, it.slot) } }

        onOpLoc1(auric_wilderness_locs.ditch) { crossWildernessDitch(it.loc.coords) }
        onOpLoc1(auric_wilderness_locs.ditchMembers) { crossWildernessDitch(it.loc.coords) }

        AuricAltar.entries.forEach { altar -> onOpLoc1(altar.loc) { rechargePrayer() } }
        AuricAltar.entries.forEach { altar ->
            Bone.entries.forEach { bone ->
                onOpLocU(altar.loc, bone.obj) { offerBonesOnAltar(it.objType, it.invSlot) }
            }
        }

        onOpNpc1(auric_hub_npcs.shopkeeper) { player.openAuricGeneralStore(it.npc) }
        onOpNpc3(auric_hub_npcs.shopkeeper) { player.openAuricGeneralStore(it.npc) }
        onOpNpc1(auric_hub_npcs.shopAssistant) { player.openAuricGeneralStore(it.npc) }
        onOpNpc3(auric_hub_npcs.shopAssistant) { player.openAuricGeneralStore(it.npc) }

        onCommand("commands") {
            desc = "Show useful Auric alpha commands"
            cheat(::showCommands)
        }

        onCommand("help") {
            desc = "Show useful Auric alpha commands"
            cheat(::showCommands)
        }

        onCommand("home") {
            desc = "Teleport to Auric home"
            cheat(::homeTeleport)
        }

        onCommand("players") {
            desc = "Show online players"
            cheat(::showPlayers)
        }

        onCommand("rankcheck") {
            desc = "Show your active Auric staff rank"
            cheat(::showRank)
        }

        onCommand("rank") {
            desc = "Show your active Auric staff rank"
            cheat(::showRank)
        }

        onCommand("discord") {
            desc = "Show Auric community information"
            cheat(::showDiscord)
        }

        onCommand("rules") {
            desc = "Show alpha rules"
            cheat(::showRules)
        }

        onCommand("bug") {
            desc = "Show bug report instructions"
            cheat(::showBugReport)
        }

        onCommand("stuck") {
            desc = "Teleport to Auric home if stuck"
            cheat(::homeTeleport)
        }

        onCommand("edge") {
            desc = "Teleport to Edgeville hub"
            cheat(::homeTeleport)
        }

        onCommand("pos") {
            desc = "Show your current alpha test coordinates"
            cheat(::showPosition)
        }

        onCommand("ditch") {
            desc = "Cross the alpha Wilderness ditch test line"
            cheat(::crossWildernessDitch)
        }

        onCommand("trace") {
            desc = "Dump your latest Auric observability events"
            modLevel = modlevels.developer
            cheat(::showOwnTrace)
        }

        onCommand("traceglobal") {
            desc = "Dump recent global Auric observability events to the server log"
            modLevel = modlevels.developer
            cheat(::showGlobalTrace)
        }

        onCommand("traceclear") {
            desc = "Clear your Auric observability trace"
            modLevel = modlevels.developer
            cheat(::clearOwnTrace)
        }

        onCommand("traceon") {
            desc = "Enable Auric observability tracing"
            modLevel = modlevels.developer
            cheat(::enableTrace)
        }

        onCommand("traceoff") {
            desc = "Disable Auric observability tracing"
            modLevel = modlevels.developer
            cheat(::disableTrace)
        }

        onCommand("traceverbose") {
            desc = "Toggle verbose Auric observability tracing"
            modLevel = modlevels.developer
            cheat(::toggleVerboseTrace)
        }
    }

    private suspend fun ProtectedAccess.buryBones(type: ObjType, slot: Int) {
        val bone = Bone.entries.firstOrNull { type.isType(it.obj) } ?: return
        val delete = invDel(player.inv, type, slot = slot)
        if (!delete.success) {
            return
        }
        mes("You bury the ${bone.messageName}.")
        statAdvance(stats.prayer, bone.xp)
    }

    private fun showCommands(cheat: Cheat) =
        with(cheat.player) {
            mes("Auric Alpha commands:")
            mes("::home, ::players, ::stuck, ::discord, ::rules, ::bug")
            mes("Testing helpers: ::pos, ::ditch")
        }

    @OptIn(InternalApi::class)
    private fun showPlayers(cheat: Cheat) =
        with(cheat.player) {
            val visible = playerList.filter { !it.hidden }.toList()
            val names = visible.map { it.displayName ?: it.username }.sorted()
            mes("Players online: ${names.size}")
            if (names.isNotEmpty()) {
                mes(names.joinToString(", "))
            }
        }

    private fun showRank(cheat: Cheat) =
        with(cheat.player) {
            if (!modLevel.hasAccessTo(modlevels.support)) {
                mes("You do not have permission to use that command.")
                return
            }
            val rankName = modLevel.internalName?.displayRankName() ?: "Player"
            val inherited = inheritedRankNames()
            mes("Rank: $rankName")
            mes("Permission Level: ${modLevel.accessFlags}")
            mes("Client Code: ${modLevel.clientCode}")
            mes("Inherits: ${inherited.ifEmpty { "None" }}")
        }

    private fun showDiscord(cheat: Cheat) =
        cheat.player.mes("Auric Discord: ask the host for the current trusted-alpha invite.")

    private fun showRules(cheat: Cheat) =
        with(cheat.player) {
            mes("Auric Alpha rules: report bugs, do not abuse exploits, and respect other testers.")
            mes("This is a trusted test. Progress may reset while core systems are built.")
        }

    private fun showBugReport(cheat: Cheat) =
        with(cheat.player) {
            mes("Bug reports: send what happened, your username, and what you clicked or typed.")
            mes("If the client crashes, send the Auric diagnostics zip.")
        }

    private fun showOwnTrace(cheat: Cheat) =
        with(cheat.player) {
            val entries = observability.playerEntries(characterId, limit = 10)
            mes("Auric trace entries: ${entries.size}. Latest entries were also written to the server log.")
            entries.takeLast(5).forEach { mes(it.take(240)) }
            logger.info {
                "[AURIC_TRACE][COMMAND_DUMP_PLAYER] requestedBy='$username' character=$characterId entries=" +
                    entries.joinToString(separator = " || ")
            }
        }

    private fun showGlobalTrace(cheat: Cheat) =
        with(cheat.player) {
            val entries = observability.globalEntries(limit = 60)
            mes("Global Auric trace entries: ${entries.size}. Full dump written to the server log.")
            logger.info {
                "[AURIC_TRACE][COMMAND_DUMP_GLOBAL] requestedBy='$username' count=${entries.size} entries=" +
                    entries.joinToString(separator = " || ")
            }
        }

    private fun clearOwnTrace(cheat: Cheat) =
        with(cheat.player) {
            observability.clearPlayer(characterId)
            observability.record(this, "OBSERVABILITY", "CLEAR_PLAYER_TRACE", "requestedBy='$username'")
            mes("Your Auric trace history was cleared.")
        }

    private fun enableTrace(cheat: Cheat) {
        observability.setTracingEnabled(true)
        observability.record(cheat.player, "OBSERVABILITY", "ENABLE", "requestedBy='${cheat.player.username}'")
        cheat.player.mes("Auric observability tracing enabled.")
    }

    private fun disableTrace(cheat: Cheat) {
        observability.record(cheat.player, "OBSERVABILITY", "DISABLE", "requestedBy='${cheat.player.username}'")
        observability.setTracingEnabled(false)
        cheat.player.mes("Auric observability tracing disabled.")
    }

    private fun toggleVerboseTrace(cheat: Cheat) {
        val next = !observability.verbose
        observability.setVerboseTracing(next)
        observability.record(cheat.player, "OBSERVABILITY", "VERBOSE", "value=$next requestedBy='${cheat.player.username}'")
        cheat.player.mes("Auric verbose tracing: ${if (next) "enabled" else "disabled"}.")
    }

    private fun showPosition(cheat: Cheat) =
        with(cheat.player) {
            if (!modLevel.hasAccessTo(modlevels.developer)) {
                mes("You do not have permission to use that command.")
                return
            }
            mes("Position: ${coords.x}, ${coords.z}, level ${coords.level}")
        }

    private fun Player.inheritedRankNames(): String {
        val ranks =
            listOf(
                modlevels.support to "Support",
                modlevels.moderator to "Moderator",
                modlevels.admin to "Administrator",
                modlevels.developer to "Developer",
                modlevels.coOwner to "Co-Owner",
                modlevels.owner to "Owner",
            )
        return ranks
            .filter { (rank, _) -> modLevel.hasAccessTo(rank) && modLevel.id != rank.id }
            .joinToString(", ") { (_, name) -> name }
    }

    private fun Player.applyAuricRankAppearance() {
        val prefix =
            when (modLevel.internalName) {
                "owner" -> "<col=ff1f1f>[Owner]</col> "
                "co_owner" -> "<col=b300ff>[Co-Owner]</col> "
                "developer" -> "<col=00ffff>[Dev]</col> "
                "admin" -> "<col=ffffff>[Admin]</col> "
                "moderator" -> "<col=00ff00>[Mod]</col> "
                else -> null
            }
        appearance.namePrefix = prefix
    }

    private fun String.displayRankName(): String =
        when (this) {
            "admin" -> "Administrator"
            "co_owner" -> "Co-Owner"
            else -> replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun homeTeleport(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                telejump(HOME)
                mes("You teleport to Auric home in Edgeville.")
            }
        }

    private fun crossWildernessDitch(cheat: Cheat) =
        with(cheat) {
            protectedAccess.launch(player) {
                crossWildernessDitch(DITCH_COMMAND_COORDS)
            }
        }

    private suspend fun ProtectedAccess.crossWildernessDitch(ditchCoords: CoordGrid) {
        val dest =
            if (player.coords.z <= ditchCoords.z) {
                player.coords.copy(z = ditchCoords.z + DITCH_CROSS_DISTANCE)
            } else {
                player.coords.copy(z = ditchCoords.z - DITCH_CROSS_DISTANCE)
            }
        telejump(dest)
        mes("You cross the Wilderness ditch.")
    }

    private suspend fun ProtectedAccess.rechargePrayer() {
        if (stat(stats.prayer) >= statBase(stats.prayer)) {
            mes("You already have full Prayer points.")
            return
        }
        statRestore(stats.prayer)
        mes("You recharge your Prayer points.")
    }

    private suspend fun ProtectedAccess.offerBonesOnAltar(type: ObjType, slot: Int) {
        val bone = Bone.entries.firstOrNull { type.isType(it.obj) } ?: return
        val delete = invDel(player.inv, type, slot = slot)
        if (!delete.success) {
            return
        }
        mes("You offer the ${bone.messageName} at the altar.")
        statAdvance(stats.prayer, bone.xp * ALTAR_PRAYER_XP_MULTIPLIER)
    }

    private fun Player.openAuricGeneralStore(npc: org.rsmod.game.entity.Npc) {
        shops.open(this, npc, "Auric General Store", BaseInvs.generalshop1)
    }

    private enum class Bone(val obj: ObjType, val xp: Double, val messageName: String) {
        Regular(objs.bones, 4.5, "bones"),
        Bat(auric_prayer_objs.batBones, 5.3, "bat bones"),
        Big(auric_prayer_objs.bigBones, 15.0, "big bones"),
    }

    private enum class AuricAltar(val loc: org.rsmod.game.type.loc.LocType) {
        Regular(auric_prayer_locs.altar),
        Guthix(auric_prayer_locs.guthixAltar),
        Chaos(auric_prayer_locs.chaosAltar),
        Monks(auric_prayer_locs.monksAltar),
        CaveTemple(auric_prayer_locs.caveTempleAltar),
        WildyHub(auric_prayer_locs.wildyHubAltar),
    }

    private companion object {
        private const val DITCH_Z = 3520
        private const val DITCH_CROSS_DISTANCE = 2
        private const val ALTAR_PRAYER_XP_MULTIPLIER = 2.0
        private val HOME = CoordGrid(0, 48, 54, 15, 40)
        private val DITCH_COMMAND_COORDS = CoordGrid(HOME.x, DITCH_Z, HOME.level)
    }
}
