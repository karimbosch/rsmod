package org.rsmod.content.other.auric

import org.rsmod.api.type.builders.map.npc.MapNpcSpawnBuilder

object AuricStarterNpcSpawns : MapNpcSpawnBuilder() {
    override fun onPackMapTask() {
        resourceFile<AuricAlphaBasicsScript>("npcs.toml")
    }
}
