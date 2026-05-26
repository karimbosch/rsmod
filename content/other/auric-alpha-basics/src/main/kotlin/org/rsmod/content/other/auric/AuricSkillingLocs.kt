package org.rsmod.content.other.auric

import org.rsmod.api.type.refs.loc.LocReferences

typealias auric_skilling_locs = AuricSkillingLocs

object AuricSkillingLocs : LocReferences() {
    val furnace = find("furnace")
    val anvil = find("anvil")
    val range = find("range")
}
