package org.rsmod.content.other.auric

import org.rsmod.api.config.refs.content
import org.rsmod.api.type.editors.npc.NpcEditor
import org.rsmod.api.type.refs.npc.NpcReferences

typealias auric_hub_npcs = AuricHubNpcs

object AuricHubNpcs : NpcReferences() {
    val banker = find("deadman_banker_blue_south")
    val bankerTutor = find("aide_tutor_banker")
    val shopkeeper = find("generalshopkeeper1")
    val shopAssistant = find("generalassistant1")
    val emblemTrader = find("emblem_trader")
    val wildernessCapeSeller = find("wilderness_capeseller_1")
}

internal object AuricHubNpcEditor : NpcEditor() {
    init {
        edit(auric_hub_npcs.banker) {
            contentGroup = content.banker
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_hub_npcs.bankerTutor) {
            contentGroup = content.banker_tutor
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_hub_npcs.shopkeeper) {
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_hub_npcs.shopAssistant) {
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_hub_npcs.emblemTrader) {
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_hub_npcs.wildernessCapeSeller) {
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_ge_npcs.clerk1) {
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_ge_npcs.clerk2) {
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_ge_npcs.clerk3) {
            respawnDir = south
            wanderRange = 0
        }

        edit(auric_ge_npcs.clerk4) {
            respawnDir = south
            wanderRange = 0
        }
    }
}
