package org.rsmod.content.other.auric

import org.rsmod.api.type.refs.loc.LocReferences

typealias auric_prayer_locs = AuricPrayerLocs

object AuricPrayerLocs : LocReferences() {
    val altar = find("altar")
    val guthixAltar = find("guthix_altar")
    val chaosAltar = find("chaosaltar")
    val monksAltar = find("monks_altar")
    val caveTempleAltar = find("cave_temple_altar")
    val wildyHubAltar = find("wildy_hub_altar")
}
