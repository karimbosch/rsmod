package org.rsmod.content.other.auric

import org.rsmod.api.type.refs.obj.ObjReferences

typealias auric_skilling_objs = AuricSkillingObjs

object AuricSkillingObjs : ObjReferences() {
    val copperOre = find("copper_ore")
    val tinOre = find("tin_ore")
    val bronzeBar = find("bronze_bar")
    val bronzeDagger = find("bronze_dagger")
    val rawShrimp = find("raw_shrimp")
    val shrimp = find("shrimp")
    val rawChicken = find("raw_chicken")
    val cookedChicken = find("cooked_chicken")
    val rawBeef = find("raw_beef")
    val cookedMeat = find("cooked_meat")
}
