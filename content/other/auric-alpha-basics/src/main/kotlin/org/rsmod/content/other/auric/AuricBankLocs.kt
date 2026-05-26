package org.rsmod.content.other.auric

import org.rsmod.api.type.refs.loc.LocReferences

typealias auric_bank_locs = AuricBankLocs

object AuricBankLocs : LocReferences() {
    val bankBooth = find("bankbooth")
    val bankBoothEndLeft = find("bankbooth_end_left")
    val bankBoothEndRight = find("bankbooth_end_right")
    val bankBoothDeadman = find("bankbooth_deadman")
    val aideBankBooth = find("aide_bankbooth")
    val newbieBankBooth = find("newbiebankbooth")
    val faiVarrockBankBooth = find("fai_varrock_bankbooth")
    val faiFaladorBankBooth = find("fai_falador_bankbooth")
    val thBankChest = find("thbankchest")
    val castleWarsBankChest = find("castlewars_bankchest")
    val championsBankChest = find("champions_bankchest")
    val diaryGuildBankChest = find("diary_guild_bankchest")
    val wcGuildBankChest = find("wcguild_bankchest")
    val wintertodtBankChest = find("wint_bankchest")
    val brimstoneBankChest = find("brimstone_bankchest")
    val soulWarsBankChest = find("soul_wars_bankchest")
    val magicTrainingBankChest = find("magictraining_bankchest")
}
