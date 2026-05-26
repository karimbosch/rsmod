package org.rsmod.content.other.auric

import org.rsmod.api.type.refs.comp.ComponentReferences
import org.rsmod.api.type.refs.interf.InterfaceReferences
import org.rsmod.api.type.refs.inv.InvReferences
import org.rsmod.api.type.refs.npc.NpcReferences
import org.rsmod.api.type.refs.varbit.VarBitReferences
import org.rsmod.api.type.refs.varp.VarpReferences

typealias auric_ge_components = AuricGrandExchangeComponents
typealias auric_ge_invs = AuricGrandExchangeInvs
typealias auric_ge_interfaces = AuricGrandExchangeInterfaces
typealias auric_ge_npcs = AuricGrandExchangeNpcs
typealias auric_ge_varbits = AuricGrandExchangeVarBits
typealias auric_ge_varps = AuricGrandExchangeVarps

object AuricGrandExchangeNpcs : NpcReferences() {
    val clerk1 = find("ge_clerk_1")
    val clerk2 = find("ge_clerk_2")
    val clerk3 = find("ge_clerk_3")
    val clerk4 = find("ge_clerk_4")
}

object AuricGrandExchangeInterfaces : InterfaceReferences() {
    val offers = find("ge_offers")
    val offersSide = find("ge_offers_side")
    val collect = find("ge_collect")
}

object AuricGrandExchangeInvs : InvReferences() {
    val offer0 = find("ge_offer_0")
    val offer1 = find("ge_offer_1")
    val offer2 = find("ge_offer_2")
    val offer3 = find("ge_offer_3")
    val offer4 = find("ge_offer_4")
    val offer5 = find("ge_offer_5")
    val offer6 = find("ge_offer_6")
    val offer7 = find("ge_offer_7")
}

object AuricGrandExchangeComponents : ComponentReferences() {
    val collectAll = find("ge_offers:collectall")
    val history = find("ge_offers:history")
    val back = find("ge_offers:back")
    val index0 = find("ge_offers:index_0")
    val index1 = find("ge_offers:index_1")
    val index2 = find("ge_offers:index_2")
    val index3 = find("ge_offers:index_3")
    val index4 = find("ge_offers:index_4")
    val index5 = find("ge_offers:index_5")
    val index6 = find("ge_offers:index_6")
    val index7 = find("ge_offers:index_7")
    val details = find("ge_offers:details")
    val detailsDesc = find("ge_offers:details_desc")
    val detailsMarketPrice = find("ge_offers:details_marketprice")
    val detailsFee = find("ge_offers:details_fee")
    val detailsObj = find("ge_offers:com_19")
    val detailsProgressLeft = find("ge_offers:com_20")
    val detailsProgressRight = find("ge_offers:com_21")
    val detailsProgressBar = find("ge_offers:com_22")
    val detailsStatus = find("ge_offers:details_status")
    val detailsCollect = find("ge_offers:details_collect")
    val detailsModify = find("ge_offers:details_modify")
    val setupDesc = find("ge_offers:setup_desc")
    val setupMarketPrice = find("ge_offers:setup_marketprice")
    val setupFee = find("ge_offers:setup_fee")
    val setupConfirm = find("ge_offers:setup_confirm")
    val setupObj = find("ge_offers:com_31")
    val sideItems = find("ge_offers_side:items")
    val collectInv = find("ge_collect:collect_inv")
    val collectBank = find("ge_collect:collect_bank")
}

object AuricGrandExchangeVarBits : VarBitReferences() {
    val newOfferQuantity = find("ge_newoffer_quantity")
    val newOfferType = find("ge_newoffer_type")
    val newOfferPrice = find("ge_newoffer_price")
    val selectedSlot = find("ge_selectedslot")
}

object AuricGrandExchangeVarps : VarpReferences() {
    val lastOfferItem = find("ge_last_offer_item")
    val lastOfferQuantity = find("ge_last_offer_quantity")
    val lastOfferPrice = find("ge_last_offer_price")
    val lastOfferType = find("ge_last_offer_type")
    val itemSinkObj0 = find("ge_itemsink_obj_0")
    val itemSinkPrice0 = find("ge_itemsink_price_0")
    val itemSinkObj1 = find("ge_itemsink_obj_1")
    val itemSinkPrice1 = find("ge_itemsink_price_1")
    val itemSinkObj2 = find("ge_itemsink_obj_2")
    val itemSinkPrice2 = find("ge_itemsink_price_2")
    val itemSinkObj3 = find("ge_itemsink_obj_3")
    val itemSinkPrice3 = find("ge_itemsink_price_3")
    val itemSinkObj4 = find("ge_itemsink_obj_4")
    val itemSinkPrice4 = find("ge_itemsink_price_4")
    val itemSinkObj5 = find("ge_itemsink_obj_5")
    val itemSinkPrice5 = find("ge_itemsink_price_5")
    val itemSinkObj6 = find("ge_itemsink_obj_6")
    val itemSinkPrice6 = find("ge_itemsink_price_6")
    val itemSinkObj7 = find("ge_itemsink_obj_7")
    val itemSinkPrice7 = find("ge_itemsink_price_7")
}
