package org.rsmod.content.other.auric

import org.rsmod.api.type.refs.loc.LocReferences

typealias auric_wilderness_locs = AuricWildernessLocs

object AuricWildernessLocs : LocReferences() {
    val ditch = find("ditch_wilderness_cover")
    val ditchMembers = find("ditch_wilderness_cover_members")
}
