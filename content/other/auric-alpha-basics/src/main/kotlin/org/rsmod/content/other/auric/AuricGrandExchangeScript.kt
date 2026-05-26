package org.rsmod.content.other.auric

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import net.rsprot.protocol.game.outgoing.interfaces.IfSetObject
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStockMarketSlot
import org.rsmod.api.config.refs.modlevels
import org.rsmod.api.config.refs.objs
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc2
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Npc
import org.rsmod.game.type.obj.ObjTypeList
import org.rsmod.game.type.obj.UnpackedObjType
import org.rsmod.game.type.inv.InvType
import org.rsmod.game.type.interf.IfButtonOp
import org.rsmod.game.type.interf.IfEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class AuricGrandExchangeScript
@Inject
constructor(
    private val repository: AuricGrandExchangeRepository,
    private val objTypes: ObjTypeList,
    private val protectedAccess: ProtectedAccessLauncher,
    private val observability: AuricObservability,
) : PluginScript() {
    private val logger = InlineLogger()
    private val sessions = mutableMapOf<Int, ExchangeSession>()
    private val traces = mutableMapOf<Int, MutableList<String>>()

    override fun ScriptContext.startup() {
        onOpNpc1(auric_ge_npcs.clerk1) { openExchange(it.npc) }
        onOpNpc1(auric_ge_npcs.clerk2) { openExchange(it.npc) }
        onOpNpc1(auric_ge_npcs.clerk3) { openExchange(it.npc) }
        onOpNpc1(auric_ge_npcs.clerk4) { openExchange(it.npc) }

        onOpNpc2(auric_ge_npcs.clerk1) { openExchange(it.npc) }
        onOpNpc2(auric_ge_npcs.clerk2) { openExchange(it.npc) }
        onOpNpc2(auric_ge_npcs.clerk3) { openExchange(it.npc) }
        onOpNpc2(auric_ge_npcs.clerk4) { openExchange(it.npc) }

        onOpNpc3(auric_ge_npcs.clerk1) { openExchange(it.npc) }
        onOpNpc3(auric_ge_npcs.clerk2) { openExchange(it.npc) }
        onOpNpc3(auric_ge_npcs.clerk3) { openExchange(it.npc) }
        onOpNpc3(auric_ge_npcs.clerk4) { openExchange(it.npc) }

        onOpNpc4(auric_ge_npcs.clerk1) { openExchange(it.npc) }
        onOpNpc4(auric_ge_npcs.clerk2) { openExchange(it.npc) }
        onOpNpc4(auric_ge_npcs.clerk3) { openExchange(it.npc) }
        onOpNpc4(auric_ge_npcs.clerk4) { openExchange(it.npc) }

        onCommand("ge") {
            desc = "Open the Auric Grand Exchange"
            cheat(::commandOpenExchange)
        }
        onCommand("geselftest") {
            desc = "Run a live Auric Grand Exchange database correctness test"
            modLevel = modlevels.developer
            cheat(::commandSelfTest)
        }
        onCommand("getrace") {
            desc = "Dump the latest Auric Grand Exchange interaction trace for this player"
            modLevel = modlevels.developer
            cheat(::commandTrace)
        }

        indexComponents.forEachIndexed { slot, component ->
            onIfModalButton(component) { selectOfferSlot(slot, it.comsub, it.op) }
        }
        onIfModalButton(auric_ge_components.sideItems) { selectSellInventorySlot(it.comsub) }
        onIfModalButton(auric_ge_components.collectAll) { collectAll(toBank = false) }
        onIfModalButton(auric_ge_components.back) { openExchangeInterface() }
        onIfModalButton(auric_ge_components.setupConfirm) { confirmPendingOffer() }
        onIfModalButton(auric_ge_components.details) { routeDetailPanelButton(it.comsub, it.op) }
        onIfModalButton(auric_ge_components.detailsCollect) { routeDetailCollectButton(it.comsub, it.op) }
        onIfModalButton(auric_ge_components.detailsModify) { routeDetailModifyButton(it.comsub, it.op) }
    }

    private suspend fun ProtectedAccess.openExchange(npc: Npc) {
        faceSquare(npc.coords)
        openExchangeInterface()
    }

    private fun commandOpenExchange(cheat: Cheat) {
        logger.info { "[GE_ACTION][COMMAND_OPEN] player='${cheat.player.username}' character=${cheat.player.characterId}" }
        protectedAccess.launch(cheat.player) { openExchangeInterface() }
    }

    private fun commandSelfTest(cheat: Cheat) {
        protectedAccess.launch(cheat.player) {
            val result =
                repository.runSelfTest(
                    realmId = DefaultRealmId,
                    accountId = player.accountId,
                    characterId = player.characterId,
                    coinsObj = objs.coins.id,
                )
            mes(if (result.passed) "GE self-test PASSED." else "GE self-test FAILED.")
            for (detail in result.details.take(6)) {
                mes(detail)
            }
            logger.info {
                "GE self-test result: player='${player.username}' passed=${result.passed} details=${result.details}"
            }
        }
    }

    private fun commandTrace(cheat: Cheat) {
        val history = traces[cheat.player.characterId].orEmpty()
        logger.info {
            "[GE_TRACE][DUMP] player='${cheat.player.username}' character=${cheat.player.characterId} count=${history.size} " +
                history.joinToString(separator = " || ")
        }
        protectedAccess.launch(cheat.player) {
            mes("GE trace entries: ${history.size}. Full trace was written to the server log.")
            for (entry in history.takeLast(5)) {
                mes(entry.take(240))
            }
        }
    }

    private suspend fun ProtectedAccess.openExchangeInterface() {
        logger.info {
            "[GE_ACTION][OPEN] ${tracePlayer()} members=${player.members} modlevel=${player.modLevel.internalName} " +
                "offers=${repository.listOffers(player.characterId).size} collections=${repository.listCollections(player.characterId).size}"
        }
        sessions[player.characterId] = ExchangeSession()
        invTransmit(inv)
        ifOpenMainSidePair(main = auric_ge_interfaces.offers, side = auric_ge_interfaces.offersSide)
        interfaceInvInit(
            inv = inv,
            target = auric_ge_components.sideItems,
            objRowCount = 4,
            objColCount = 7,
            op1 = "Offer<col=ff9040>",
            op5 = "Examine<col=ff9040>",
        )
        wireInterfaceEvents()
        drawIndexPanel()
        mes("Grand Exchange opened. Click an empty slot to create an offer, or an active slot to manage it.")
    }

    private fun ProtectedAccess.wireInterfaceEvents() {
        logger.info { "[GE_ACTION][WIRE_EVENTS] ${tracePlayer()} invRange=${inv.indices}" }
        for (component in indexComponents) {
            ifSetEvents(component, 0..64, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5)
        }
        ifSetEvents(auric_ge_components.collectAll, 0..64, IfEvent.Op1)
        ifSetEvents(auric_ge_components.back, 0..64, IfEvent.Op1)
        ifSetEvents(auric_ge_components.setupConfirm, 0..64, IfEvent.Op1)
        ifSetEvents(auric_ge_components.details, -1..64, IfEvent.Op1, IfEvent.Op2, IfEvent.Op3, IfEvent.Op4, IfEvent.Op5)
        ifSetEvents(auric_ge_components.detailsCollect, -1..64, IfEvent.Op1, IfEvent.Op2)
        ifSetEvents(
            auric_ge_components.detailsModify,
            -1..64,
            IfEvent.Op1,
            IfEvent.Op2,
            IfEvent.Op3,
            IfEvent.Op4,
            IfEvent.Op5,
        )
        ifSetEvents(auric_ge_components.sideItems, inv.indices, IfEvent.Op1, IfEvent.Op5, IfEvent.Op10)
    }

    private suspend fun ProtectedAccess.drawIndexPanel() {
        clearOfferVars()
        val offers = repository.listOffers(player.characterId)
        val collections = repository.listCollections(player.characterId)
        logger.info {
            "[GE_STATE][DRAW_INDEX] ${tracePlayer()} active=${offers.count { it.isActive }} " +
                "offers=${offers.joinToString(prefix = "[", postfix = "]") { it.trace() }} " +
                "collections=${collections.joinToString(prefix = "[", postfix = "]") { it.trace() }}"
        }
        syncNativeStockMarketSlots(offers)
        val activeCount = offers.count { it.isActive }
        val completedCount = offers.count { it.status == "completed" }
        val collectionSummary =
            if (collections.isEmpty()) {
                "No completed items waiting."
            } else {
                collections.joinToString("<br>") { collection ->
                    "${collection.count} x ${objName(collection.obj)} ready to collect"
                }
            }
        ifSetText(
            auric_ge_components.detailsDesc,
            "Grand Exchange status",
        )
        ifSetText(auric_ge_components.detailsMarketPrice, "Open: $activeCount | Complete: $completedCount")
        ifSetText(auric_ge_components.collectAll, "Collect items")
        ifSetText(auric_ge_components.detailsModify, "")
        ifSetHide(auric_ge_components.detailsModify, true)
        ifSetText(auric_ge_components.detailsStatus, collectionSummary)
        ifSetText(
            auric_ge_components.setupDesc,
            "Click the green arrow on an empty slot to buy.<br>" +
                "Click the orange arrow on an empty slot to sell.<br>" +
                "Click an active or completed slot to manage it.",
        )
        ifSetText(auric_ge_components.setupMarketPrice, "")
        ifSetText(auric_ge_components.setupFee, "")
        clearNativeSetupObject()
    }

    private fun ProtectedAccess.syncNativeStockMarketSlots(offers: List<AuricGrandExchangeRepository.Offer>) {
        val bySlot = offers.associateBy { it.slot }
        nativeOfferInvs.forEachIndexed { slot, invType ->
            val offerInv = inv(invType)
            invClear(offerInv)
            val offer = bySlot[slot]
            if (offer != null) {
                val visibleCount = (offer.requestedCount - offer.completedCount).coerceAtLeast(1)
                val objType = objTypes.types[offer.obj]
                if (objType != null) {
                    invAdd(offerInv, objType, count = visibleCount, slot = 0, strict = false)
                }
                val completedGold = offer.completedCount.coerceAtLeast(0) * offer.priceEach.coerceAtLeast(0)
                syncNativeDetailItemVars(slot, offer.obj, offer.priceEach)
                player.client.write(
                    UpdateStockMarketSlot(
                        slot = slot,
                        update =
                            UpdateStockMarketSlot.SetStockMarketSlot(
                                status = offer.stockMarketStatus,
                                obj = offer.obj,
                                price = offer.priceEach,
                                count = offer.requestedCount,
                                completedCount = offer.completedCount,
                                completedGold = completedGold,
                            ),
                    )
                )
                logger.info {
                    "[GE_STATE][NATIVE_STOCK_SLOT] ${tracePlayer()} slot=$slot inv=${invType.internalName} " +
                        "status=${offer.stockMarketStatus} obj=${offer.obj} count=$visibleCount offer=${offer.trace()}"
                }
                traceNativeGeState("after-stock-slot-$slot", slot, offer.obj)
            } else {
                syncNativeDetailItemVars(slot, -1, 0)
                player.client.write(UpdateStockMarketSlot(slot, UpdateStockMarketSlot.ResetStockMarketSlot))
                logger.info { "[GE_STATE][NATIVE_STOCK_SLOT_EMPTY] ${tracePlayer()} slot=$slot inv=${invType.internalName}" }
                traceNativeGeState("after-stock-slot-empty-$slot", slot, -1)
            }
            invTransmit(offerInv)
        }
    }

    private fun ProtectedAccess.syncNativeDetailItemVars(slot: Int, obj: Int, price: Int) {
        val objVarp = nativeDetailObjVarps.getOrNull(slot) ?: return
        val priceVarp = nativeDetailPriceVarps.getOrNull(slot) ?: return
        vars[objVarp] = obj
        vars[priceVarp] = price
        logger.info {
            "[GE_STATE][NATIVE_DETAIL_VAR_SYNC] ${tracePlayer()} slot=$slot expectedObj=$obj " +
                "expectedName='${objName(obj)}' price=$price objVarp=${objVarp.internalName} priceVarp=${priceVarp.internalName}"
        }
    }

    private suspend fun ProtectedAccess.selectOfferSlot(slot: Int, sub: Int, op: IfButtonOp) {
        val offer = repository.listOffers(player.characterId).firstOrNull { it.slot == slot }
        traceGe(
            "SELECT_SLOT",
            "slot=$slot sub=$sub op=$op state=${offer?.trace() ?: "empty"} session=${sessions[player.characterId]}",
        )
        if (offer == null) {
            beginNewOffer(slot, sub, op)
        } else {
            handleExistingOfferSlot(offer, sub, op)
        }
    }

    private suspend fun ProtectedAccess.handleExistingOfferSlot(
        offer: AuricGrandExchangeRepository.Offer,
        sub: Int,
        op: IfButtonOp,
    ) {
        val collections = repository.listCollections(player.characterId).filter { it.offerId == offer.id }
        traceGe(
            "HANDLE_SLOT",
            "offer=${offer.trace()} sub=$sub op=$op active=${offer.isActive} collections=${collections.joinToString(prefix = "[", postfix = "]") { it.trace() }}",
        )
        when {
            op == IfButtonOp.Op2 && offer.isActive -> cancelOffer(offer)
            op == IfButtonOp.Op3 && offer.isActive -> {
                traceGe("MODIFY_DISABLED", "offer=${offer.trace()} sub=$sub op=$op")
                mes("Grand Exchange offer modification is disabled for alpha. Cancel and recreate the offer instead.")
            }
            op == IfButtonOp.Op4 && offer.isActive -> {
                traceGe("MODIFY_DISABLED", "offer=${offer.trace()} sub=$sub op=$op")
                mes("Grand Exchange offer modification is disabled for alpha. Cancel and recreate the offer instead.")
            }
            !offer.isActive && collections.isNotEmpty() && (op == IfButtonOp.Op1 || op == IfButtonOp.Op2) -> {
                traceGe("SLOT_COLLECT_CLICK", "offer=${offer.trace()} sub=$sub op=$op")
                collectCollections(collections, toBank = false)
                openExchangeInterface()
            }
            else -> showOfferDetails(offer)
        }
    }

    private suspend fun ProtectedAccess.beginNewOffer(slot: Int, sub: Int, op: IfButtonOp) {
        logger.info { "[GE_ACTION][BEGIN_NEW] ${tracePlayer()} slot=$slot sub=$sub op=$op" }
        resetPendingOfferState(slot)
        val buy =
            when (op) {
                // Native GE empty slots send different sub-components for the buy and sell boxes.
                // The exact component ids are more reliable than op names here.
                IfButtonOp.Op1 if sub == 3 -> true
                IfButtonOp.Op1 if sub == 4 -> false
                IfButtonOp.Op2 -> false
                else -> choice2("Create a buy offer.", true, "Create a sell offer.", false)
            }
        sessions[player.characterId] = ExchangeSession(selectedSlot = slot, pendingType = if (buy) OfferType.Buy else OfferType.Sell)
        logger.info {
            "[GE_STATE][PENDING_TYPE] ${tracePlayer()} members=${player.members} slot=$slot " +
                "type=${if (buy) OfferType.Buy else OfferType.Sell}"
        }
        setNativeSelectedSlot(slot)
        resetNativeSetupPanel(slot, if (buy) OfferType.Buy else OfferType.Sell)
        vars[auric_ge_varbits.newOfferType] = if (buy) 0 else 1
        traceNativeGeState("before-new-offer-selection", slot, -1)
        if (buy) {
            val obj = objDialog("What would you like to buy?", stockMarketRestriction = true, showLastSearched = true)
            logger.info { "[GE_ACTION][BUY_ITEM_SELECTED] ${tracePlayer()} slot=$slot obj=${obj.id} name='${obj.name}'" }
            preparePendingOffer(slot, OfferType.Buy, obj)
        } else {
            ifSetText(auric_ge_components.setupDesc, "Click an item in your inventory to sell it.")
            mes("Click an item in your inventory to sell it.")
        }
    }

    private suspend fun ProtectedAccess.selectSellInventorySlot(slot: Int) {
        val session = sessions[player.characterId] ?: return
        logger.info { "[GE_ACTION][SELL_INV_CLICK] ${tracePlayer()} invSlot=$slot invObj=${inv[slot]} session=$session" }
        if (session.pendingType != OfferType.Sell) {
            logger.info { "[GE_FAIL][SELL_INV_NO_PENDING_SELL] ${tracePlayer()} invSlot=$slot session=$session" }
            mes("Select an empty Grand Exchange slot first.")
            return
        }
        val obj =
            inv[slot]
                ?: run {
                    logger.info { "[GE_FAIL][SELL_INV_EMPTY_SLOT] ${tracePlayer()} invSlot=$slot session=$session" }
                    return
                }
        if (obj.id == objs.coins.id) {
            logger.info { "[GE_FAIL][SELL_COINS] ${tracePlayer()} invSlot=$slot obj=${obj.id}" }
            mes("Coins cannot be sold on the Grand Exchange.")
            return
        }
        val type =
            objTypes.types[obj.id]
                ?: run {
                    logger.info { "[GE_FAIL][SELL_UNKNOWN_OBJ] ${tracePlayer()} invSlot=$slot obj=${obj.id}" }
                    return
                }
        preparePendingOffer(session.selectedSlot, OfferType.Sell, type)
    }

    private suspend fun ProtectedAccess.preparePendingOffer(slot: Int, type: OfferType, obj: UnpackedObjType) {
        logger.info { "[GE_ACTION][PREPARE_PENDING] ${tracePlayer()} slot=$slot type=$type obj=${obj.id} name='${obj.name}'" }
        if (!obj.canExchange()) {
            logger.info {
                "[GE_FAIL][UNEXCHANGEABLE] ${tracePlayer()} slot=$slot type=$type obj=${obj.id} " +
                    "tradeable=${obj.tradeable} stockmarket=${obj.stockmarket}"
            }
            mes("That item cannot be traded on the Grand Exchange.")
            openExchangeInterface()
            return
        }
        val maxQuantity = if (type == OfferType.Sell) invTotal(inv, obj) else Int.MAX_VALUE
        if (type == OfferType.Sell && maxQuantity <= 0) {
            logger.info { "[GE_FAIL][SELL_NONE_OWNED] ${tracePlayer()} slot=$slot obj=${obj.id} maxQuantity=$maxQuantity" }
            mes("You do not have any ${obj.name} to sell.")
            return
        }
        val quantity = minOf(countDialog("Enter quantity:"), maxQuantity)
        val price = countDialog("Enter price per item:")
        logger.info {
            "[GE_INPUT][QUANTITY_PRICE] ${tracePlayer()} slot=$slot type=$type obj=${obj.id} " +
                "quantity=$quantity price=$price maxQuantity=$maxQuantity"
        }
        if (!validateOfferInput(obj, quantity, price)) {
            logger.info { "[GE_FAIL][INVALID_INPUT] ${tracePlayer()} slot=$slot type=$type obj=${obj.id} quantity=$quantity price=$price" }
            return
        }
        sessions[player.characterId] =
            ExchangeSession(
                selectedSlot = slot,
                pendingType = type,
                pendingObj = obj.id,
                pendingQuantity = quantity,
                pendingPrice = price,
            )
        setNativeSelectedSlot(slot)
        vars[auric_ge_varbits.newOfferType] = if (type == OfferType.Buy) 0 else 1
        vars[auric_ge_varbits.newOfferQuantity] = quantity
        vars[auric_ge_varbits.newOfferPrice] = price
        vars[auric_ge_varps.lastOfferItem] = obj.id
        vars[auric_ge_varps.lastOfferQuantity] = quantity
        vars[auric_ge_varps.lastOfferPrice] = price
        vars[auric_ge_varps.lastOfferType] = if (type == OfferType.Buy) 0 else 1
        traceNativeGeState("before-setup-sync", slot, obj.id)
        syncNativeSetupOffer(obj)
        traceNativeGeState("after-setup-sync", slot, obj.id)
        ifSetText(
            auric_ge_components.setupDesc,
            "${type.label} ${obj.name}<br>Quantity: $quantity<br>Price: $price gp each<br>Total: ${quantity.toLong() * price.toLong()} gp",
        )
        ifSetText(auric_ge_components.setupMarketPrice, "Ready to confirm.")
        ifSetText(auric_ge_components.setupFee, "")
        val total = quantity.toLong() * price.toLong()
        val confirmed =
            choice2(
                "${type.label} ${obj.name} x $quantity for $price gp each. Total: $total gp.",
                true,
                "Cancel this offer.",
                false,
            )
        if (confirmed) {
            logger.info { "[GE_ACTION][CONFIRM_DIALOG_ACCEPTED] ${tracePlayer()} session=${sessions[player.characterId]}" }
            confirmPendingOffer()
        } else {
            logger.info { "[GE_ACTION][CONFIRM_DIALOG_CANCELLED] ${tracePlayer()} session=${sessions[player.characterId]}" }
            mes("Grand Exchange offer cancelled.")
            openExchangeInterface()
        }
    }

    private fun ProtectedAccess.syncNativeSetupOffer(obj: UnpackedObjType) {
        /*
         * The setup-offer panel is drawn by native GE clientscripts from both ge_last_offer_*
         * and the selected slot's item-sink vars. Keep both sources synchronized before the
         * redraw; otherwise the native setup item renderer can fall back to stale defaults.
         */
        val slot = vars[auric_ge_varbits.selectedSlot] - 1
        if (slot in 0 until AuricGrandExchangeRepository.MaxOfferSlots) {
            syncNativeDetailItemVars(slot, obj.id, vars[auric_ge_varbits.newOfferPrice])
        }
        runNativeGePanelRefresh()
        ifSetObj(auric_ge_components.setupObj, obj, zoom = 1)
        traceNativeGeState("after-setup-obj-pin", slot, obj.id)
    }

    private fun ProtectedAccess.resetNativeSetupPanel(slot: Int, type: OfferType) {
        resetPendingOfferState(slot)
        val text =
            if (type == OfferType.Sell) {
                "Choose an item from your inventory to sell."
            } else {
                "Choose an item to buy."
            }
        ifSetText(auric_ge_components.setupDesc, text)
        ifSetText(auric_ge_components.setupMarketPrice, "")
        ifSetText(auric_ge_components.setupFee, "")
        clearNativeSetupObject()
        traceNativeGeState("after-setup-reset", slot, -1)
    }

    private fun ProtectedAccess.clearNativeSetupObject() {
        player.client.write(IfSetObject(auric_ge_components.setupObj.packed, -1, 1))
        player.client.write(IfSetObject(auric_ge_components.detailsObj.packed, -1, 1))
    }

    private suspend fun ProtectedAccess.confirmPendingOffer() {
        val session =
            sessions[player.characterId]
                ?: run {
                    logger.info { "[GE_FAIL][CONFIRM_NO_SESSION] ${tracePlayer()}" }
                    return
                }
        logger.info { "[GE_ACTION][CONFIRM_PENDING] ${tracePlayer()} session=$session" }
        val type = session.pendingType ?: run {
            logger.info { "[GE_FAIL][CONFIRM_NO_PENDING_TYPE] ${tracePlayer()} session=$session" }
            mes("No Grand Exchange offer is being created.")
            return
        }
        val obj = objTypes.types[session.pendingObj] ?: run {
            logger.info { "[GE_FAIL][CONFIRM_UNKNOWN_OBJ] ${tracePlayer()} session=$session" }
            mes("That item is no longer available.")
            return
        }
        val quantity = session.pendingQuantity
        val price = session.pendingPrice
        if (!validateOfferInput(obj, quantity, price)) {
            return
        }
        if (repository.countActiveOffers(player.characterId) >= AuricGrandExchangeRepository.MaxOfferSlots) {
            logger.info { "[GE_FAIL][MAX_OFFERS] ${tracePlayer()} session=$session" }
            mes("You already have the maximum number of active Grand Exchange offers.")
            return
        }
        if (type == OfferType.Buy) {
            submitBuyOffer(session.selectedSlot, obj, quantity, price)
        } else {
            submitSellOffer(session.selectedSlot, obj, quantity, price)
        }
        openExchangeInterface()
    }

    private suspend fun ProtectedAccess.submitBuyOffer(slot: Int, obj: UnpackedObjType, quantity: Int, price: Int) {
        val total = quantity.toLong() * price.toLong()
        logger.info {
            "[GE_ACTION][SUBMIT_BUY] ${tracePlayer()} slot=$slot obj=${obj.id} quantity=$quantity price=$price " +
                "total=$total coins=${invCoinTotal()}"
        }
        if (invCoinTotal() < total.toInt()) {
            logger.info { "[GE_FAIL][BUY_NOT_ENOUGH_COINS_PRECHECK] ${tracePlayer()} required=$total coins=${invCoinTotal()}" }
            mes("You do not have enough coins to cover that offer.")
            return
        }
        if (!invTakeFee(total.toInt())) {
            logger.info { "[GE_FAIL][BUY_TAKE_COINS_FAILED] ${tracePlayer()} required=$total coins=${invCoinTotal()}" }
            mes("You do not have enough coins to cover that offer.")
            return
        }
        logger.info { "[GE_STATE][BUY_COINS_TAKEN] ${tracePlayer()} taken=$total remainingCoins=${invCoinTotal()}" }
        val result =
            runCatching {
                    repository.createBuyOffer(
                        realmId = DefaultRealmId,
                        accountId = player.accountId,
                        characterId = player.characterId,
                        slot = slot,
                        obj = obj.id,
                        priceEach = price,
                        count = quantity,
                        coinsObj = objs.coins.id,
                    )
                }
                .getOrElse {
                    logger.error(it) { "GE buy failed: player='${player.username}' obj=${obj.id} quantity=$quantity price=$price" }
                    invAdd(inv, objs.coins, total.toInt(), strict = false)
                    logger.info { "[GE_FAIL][BUY_DB_FAILED_REFUNDED] ${tracePlayer()} slot=$slot obj=${obj.id} total=$total" }
                    mes("The Grand Exchange could not create that offer. Your coins were returned.")
                    return
                }
        logger.info { "[GE_RESULT][BUY_CREATED] ${tracePlayer()} offer=${result.offerId} slot=${result.slot} matched=${result.matchedCount}" }
        mes("Buy offer created: ${obj.name} x $quantity at $price gp.")
        if (result.matchedCount > 0) {
            mes("Matched ${result.matchedCount} immediately. Click the offer or Collect All to claim it.")
        }
    }

    private suspend fun ProtectedAccess.submitSellOffer(slot: Int, obj: UnpackedObjType, quantity: Int, price: Int) {
        logger.info {
            "[GE_ACTION][SUBMIT_SELL] ${tracePlayer()} slot=$slot obj=${obj.id} quantity=$quantity price=$price " +
                "owned=${invTotal(inv, obj)}"
        }
        if (invTotal(inv, obj) < quantity) {
            logger.info { "[GE_FAIL][SELL_NOT_ENOUGH_ITEMS_PRECHECK] ${tracePlayer()} obj=${obj.id} quantity=$quantity owned=${invTotal(inv, obj)}" }
            mes("You do not have enough ${obj.name} to sell.")
            return
        }
        val removed = invDel(inv, obj, count = quantity)
        if (!removed.success) {
            logger.info { "[GE_FAIL][SELL_REMOVE_ITEMS_FAILED] ${tracePlayer()} obj=${obj.id} quantity=$quantity result=$removed" }
            mes("The Grand Exchange could not take those items from your inventory.")
            return
        }
        logger.info { "[GE_STATE][SELL_ITEMS_TAKEN] ${tracePlayer()} obj=${obj.id} quantity=$quantity remaining=${invTotal(inv, obj)}" }
        val result =
            runCatching {
                    repository.createSellOffer(
                        realmId = DefaultRealmId,
                        accountId = player.accountId,
                        characterId = player.characterId,
                        slot = slot,
                        obj = obj.id,
                        priceEach = price,
                        count = quantity,
                        coinsObj = objs.coins.id,
                    )
                }
                .getOrElse {
                    logger.error(it) { "GE sell failed: player='${player.username}' obj=${obj.id} quantity=$quantity price=$price" }
                    invAdd(inv, obj, quantity, strict = false)
                    logger.info { "[GE_FAIL][SELL_DB_FAILED_REFUNDED] ${tracePlayer()} slot=$slot obj=${obj.id} quantity=$quantity" }
                    mes("The Grand Exchange could not create that offer. Your items were returned.")
                    return
                }
        logger.info { "[GE_RESULT][SELL_CREATED] ${tracePlayer()} offer=${result.offerId} slot=${result.slot} matched=${result.matchedCount}" }
        mes("Sell offer created: ${obj.name} x $quantity at $price gp.")
        if (result.matchedCount > 0) {
            mes("Matched ${result.matchedCount} immediately. Click the offer or Collect All to claim it.")
        }
    }

    private suspend fun ProtectedAccess.showOfferDetails(offer: AuricGrandExchangeRepository.Offer) {
        sessions[player.characterId] = ExchangeSession(selectedSlot = offer.slot, selectedOfferId = offer.id)
        val offers = repository.listOffers(player.characterId)
        syncNativeStockMarketSlots(offers)
        setNativeSelectedSlot(offer.slot)
        vars[auric_ge_varbits.newOfferQuantity] = offer.requestedCount
        vars[auric_ge_varbits.newOfferPrice] = offer.priceEach
        vars[auric_ge_varbits.newOfferType] = if (offer.type == "buy") 0 else 1
        vars[auric_ge_varps.lastOfferItem] = offer.obj
        vars[auric_ge_varps.lastOfferQuantity] = offer.requestedCount
        vars[auric_ge_varps.lastOfferPrice] = offer.priceEach
        vars[auric_ge_varps.lastOfferType] = if (offer.type == "buy") 0 else 1
        val collections = repository.listCollections(player.characterId).filter { it.offerId == offer.id }
        val objType = objTypes.types[offer.obj]
        logger.info {
            "[GE_ACTION][SHOW_DETAILS] ${tracePlayer()} offer=${offer.trace()} " +
                "collections=${collections.joinToString(prefix = "[", postfix = "]") { it.trace() }}"
        }
        traceNativeGeState("before-details-panel", offer.slot, offer.obj)
        runNativeOfferStatusPanel()
        traceNativeGeState("after-details-panel", offer.slot, offer.obj)
        val collectionText =
            if (collections.isEmpty()) {
                "Nothing ready to collect.<br>Use the bottom button to cancel active offers."
            } else {
                collections.joinToString("<br>") { "${it.count} x ${objName(it.obj)} ready. Click the collect box." }
            }
        if (objType != null) {
            ifSetObj(auric_ge_components.detailsObj, objType, zoom = 1)
        }
        ifSetText(auric_ge_components.detailsDesc, "${offer.type.replaceFirstChar(Char::uppercaseChar)} offer: ${objName(offer.obj)}")
        ifSetText(auric_ge_components.detailsMarketPrice, "Progress: ${offer.completedCount}/${offer.requestedCount} @ ${offer.priceEach} gp")
        ifSetText(auric_ge_components.detailsFee, "${offer.stateWord}: ${offer.completedCount}/${offer.requestedCount}")
        ifSetText(auric_ge_components.detailsModify, "")
        ifSetHide(auric_ge_components.detailsModify, true)
        ifSetText(auric_ge_components.detailsCollect, if (collections.isEmpty()) "No items" else "Collect items")
        ifSetText(auric_ge_components.detailsStatus, "${offer.statusLabel}<br>$collectionText")
        mes("${formatOfferLine(offer)} ${if (collections.isEmpty()) "" else "Collection ready. Click this slot again or use Collect All."}")
    }

    private fun ProtectedAccess.runNativeOfferStatusPanel() {
        runNativeGePanelRefresh()
    }

    private fun ProtectedAccess.runNativeGePanelRefresh() {
        // Native ge_offers_switchpanel args are the same component groups supplied by ge_offers_init.
        val nativeSlot = vars[auric_ge_varbits.selectedSlot] - 1
        traceNativeGeState("before-clientscript-804", nativeSlot, vars[auric_ge_varps.lastOfferItem])
        runClientScript(804, 1, 14, 3, 4, 6, 10, 13)
        traceNativeGeState("after-clientscript-804", nativeSlot, vars[auric_ge_varps.lastOfferItem])
    }

    private suspend fun ProtectedAccess.routeDetailPanelButton(sub: Int, op: IfButtonOp) {
        logger.info { "[GE_ACTION][DETAIL_PARENT_BUTTON] ${tracePlayer()} sub=$sub op=$op session=${sessions[player.characterId]}" }
        when (sub) {
            24 -> routeDetailCollectButton(sub, op)
            25 -> routeDetailModifyButton(sub, op)
            else -> mes("Use the visible Collect items or Cancel offer button.")
        }
    }

    private suspend fun ProtectedAccess.routeDetailCollectButton(sub: Int, op: IfButtonOp) {
        logger.info { "[GE_ACTION][DETAIL_COLLECT_BUTTON] ${tracePlayer()} sub=$sub op=$op session=${sessions[player.characterId]}" }
        collectSelectedOffer(toBank = op == IfButtonOp.Op2)
    }

    private suspend fun ProtectedAccess.routeDetailModifyButton(sub: Int, op: IfButtonOp) {
        traceGe("DETAIL_MODIFY_DISABLED", "sub=$sub op=$op session=${sessions[player.characterId]}")
        mes("That native button is disabled during alpha. Right-click the offer slot and choose Abort Offer.")
    }

    private suspend fun ProtectedAccess.collectSelectedOffer(toBank: Boolean) {
        val offerId = sessions[player.characterId]?.selectedOfferId ?: run {
            logger.info { "[GE_FAIL][COLLECT_NO_SELECTED_OFFER] ${tracePlayer()} session=${sessions[player.characterId]} toBank=$toBank" }
            mes("Select an offer first.")
            return
        }
        logger.info { "[GE_ACTION][COLLECT_SELECTED] ${tracePlayer()} offer=$offerId toBank=$toBank" }
        collectCollections(repository.listCollections(player.characterId).filter { it.offerId == offerId }, toBank)
        openExchangeInterface()
    }

    private suspend fun ProtectedAccess.collectAll(toBank: Boolean) {
        val collections = repository.listCollections(player.characterId)
        logger.info {
            "[GE_ACTION][COLLECT_ALL] ${tracePlayer()} toBank=$toBank " +
                "collections=${collections.joinToString(prefix = "[", postfix = "]") { it.trace() }}"
        }
        collectCollections(collections, toBank)
        openExchangeInterface()
    }

    private suspend fun ProtectedAccess.cancelSelectedOffer(sub: Int, op: IfButtonOp) {
        logger.info {
            "[GE_ACTION][CANCEL_BUTTON] ${tracePlayer()} sub=$sub op=$op session=${sessions[player.characterId]}"
        }
        val offerId = sessions[player.characterId]?.selectedOfferId ?: run {
            logger.info { "[GE_FAIL][CANCEL_NO_SELECTED_OFFER] ${tracePlayer()} session=${sessions[player.characterId]}" }
            mes("Select an offer first.")
            return
        }
        val offer = repository.listOffers(player.characterId).firstOrNull { it.id == offerId } ?: run {
            logger.info { "[GE_FAIL][CANCEL_OFFER_NOT_FOUND] ${tracePlayer()} offer=$offerId" }
            mes("That offer is no longer available.")
            openExchangeInterface()
            return
        }
        cancelOffer(offer)
    }

    private suspend fun ProtectedAccess.cancelOffer(offer: AuricGrandExchangeRepository.Offer) {
        if (!offer.isActive) {
            logger.info { "[GE_FAIL][CANCEL_NOT_ACTIVE] ${tracePlayer()} offer=${offer.trace()}" }
            mes("That offer is already ${offer.status}.")
            return
        }
        logger.info { "[GE_ACTION][CANCEL] ${tracePlayer()} offer=${offer.trace()}" }
        if (repository.cancelOffer(player.characterId, offer.id, objs.coins.id)) {
            logger.info { "[GE_RESULT][CANCELLED] ${tracePlayer()} offer=${offer.id}" }
            mes("Offer cancelled. Remaining escrow was moved to your collection box.")
        } else {
            logger.info { "[GE_FAIL][CANCEL_REPOSITORY_FALSE] ${tracePlayer()} offer=${offer.id}" }
            mes("That offer could not be cancelled.")
        }
        openExchangeInterface()
    }

    private suspend fun ProtectedAccess.collectCollections(
        collections: List<AuricGrandExchangeRepository.Collection>,
        toBank: Boolean,
    ) {
        traceGe(
            "COLLECTING",
            "requestedToBank=$toBank collections=${collections.joinToString(prefix = "[", postfix = "]") { it.trace() }}",
        )
        if (collections.isEmpty()) {
            logger.info { "[GE_FAIL][COLLECT_EMPTY] ${tracePlayer()} toBank=$toBank" }
            mes("You have nothing to collect from the Grand Exchange.")
            return
        }
        for (collection in collections) {
            logger.info { "[GE_ACTION][COLLECT_ENTRY] ${tracePlayer()} collection=${collection.trace()} toBank=$toBank" }
            val type =
                objTypes.types[collection.obj]
                    ?: run {
                        logger.info { "[GE_FAIL][COLLECT_UNKNOWN_OBJ] ${tracePlayer()} collection=${collection.trace()}" }
                        continue
                    }
            val target = if (toBank) bank else inv
            var collectedToBank = toBank
            var add = invAdd(target, type, collection.count, strict = true)
            if (!add.success && !toBank) {
                traceGe(
                    "COLLECT_INVENTORY_FULL_FALLBACK_BANK",
                    "collection=${collection.trace()} invResult=$add",
                )
                val bankAdd = invAdd(bank, type, collection.count, strict = true)
                if (bankAdd.success) {
                    add = bankAdd
                    collectedToBank = true
                }
            }
            if (!add.success) {
                logger.info { "[GE_FAIL][COLLECT_TARGET_FULL] ${tracePlayer()} collection=${collection.trace()} toBank=$toBank result=$add" }
                mes(if (toBank) "Your bank is too full to collect everything." else "Your inventory is too full to collect everything.")
                traceGe(
                    "COLLECTION_BLOCKED",
                    "collection=${collection.trace()} requestedToBank=$toBank result=$add",
                )
                return
            }
            if (!repository.deleteCollection(player.characterId, collection.id)) {
                invDel(if (collectedToBank) bank else inv, type, count = collection.count, strict = false)
                logger.info {
                    "[GE_FAIL][COLLECT_DELETE_FAILED_ROLLBACK] ${tracePlayer()} collection=${collection.trace()} " +
                        "requestedToBank=$toBank actualToBank=$collectedToBank"
                }
                mes("That Grand Exchange collection is no longer available.")
                traceGe(
                    "COLLECTION_DELETE_FAILED",
                    "collection=${collection.trace()} requestedToBank=$toBank actualToBank=$collectedToBank",
                )
                return
            }
            traceGe(
                "COLLECTED",
                "collection=${collection.trace()} requestedToBank=$toBank actualToBank=$collectedToBank",
            )
            logger.info {
                "[GE_RESULT][COLLECTED] ${tracePlayer()} collection=${collection.trace()} " +
                    "requestedToBank=$toBank actualToBank=$collectedToBank"
            }
            mes("Collected ${collection.count} x ${type.name}${if (collectedToBank) " to your bank" else ""}.")
        }
    }

    private fun ProtectedAccess.clearOfferVars() {
        resetPendingOfferState()
        vars[auric_ge_varbits.selectedSlot] = 0
    }

    private fun ProtectedAccess.resetPendingOfferState(slot: Int? = null) {
        vars[auric_ge_varbits.newOfferQuantity] = 0
        vars[auric_ge_varbits.newOfferType] = 0
        vars[auric_ge_varbits.newOfferPrice] = 0
        vars[auric_ge_varps.lastOfferItem] = -1
        vars[auric_ge_varps.lastOfferQuantity] = 0
        vars[auric_ge_varps.lastOfferPrice] = 0
        vars[auric_ge_varps.lastOfferType] = 0
        slot?.let { syncNativeDetailItemVars(it, -1, 0) }
    }

    private fun ProtectedAccess.setNativeSelectedSlot(slot: Int) {
        /*
         * The native GE clientscripts store selected slot as 1-based and subtract 1 before reading
         * the per-slot item-sink vars. Auric repository/session slots remain 0-based.
         */
        vars[auric_ge_varbits.selectedSlot] = slot + 1
    }

    private fun ProtectedAccess.validateOfferInput(obj: UnpackedObjType, count: Int, price: Int): Boolean {
        if (!obj.canExchange()) {
            logger.info {
                "[GE_FAIL][VALIDATE_UNEXCHANGEABLE] ${tracePlayer()} obj=${obj.id} tradeable=${obj.tradeable} " +
                    "stockmarket=${obj.stockmarket} count=$count price=$price"
            }
            mes("That item cannot be traded on the Grand Exchange.")
            return false
        }
        if (count <= 0 || price <= 0) {
            logger.info { "[GE_FAIL][VALIDATE_NON_POSITIVE] ${tracePlayer()} obj=${obj.id} count=$count price=$price" }
            mes("Quantity and price must both be greater than zero.")
            return false
        }
        if (count.toLong() * price.toLong() > Int.MAX_VALUE) {
            logger.info { "[GE_FAIL][VALIDATE_TOO_LARGE] ${tracePlayer()} obj=${obj.id} count=$count price=$price" }
            mes("That offer is too large.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.tracePlayer(): String =
        "player='${player.username}' account=${player.accountId} character=${player.characterId} " +
            "members=${player.members} modlevel=${player.modLevel.internalName}"

    private fun ProtectedAccess.traceGe(event: String, detail: String) {
        val characterId = player.characterId
        val history = traces.getOrPut(characterId) { mutableListOf() }
        val entry =
            "event=$event session=${sessions[characterId]} selectedSlot=${vars[auric_ge_varbits.selectedSlot]} " +
                "newOffer={qty=${vars[auric_ge_varbits.newOfferQuantity]},price=${vars[auric_ge_varbits.newOfferPrice]},type=${vars[auric_ge_varbits.newOfferType]}} " +
                "lastOffer={obj=${vars[auric_ge_varps.lastOfferItem]},qty=${vars[auric_ge_varps.lastOfferQuantity]},price=${vars[auric_ge_varps.lastOfferPrice]},type=${vars[auric_ge_varps.lastOfferType]}} " +
                detail
        history += entry
        if (history.size > 80) {
            history.removeAt(0)
        }
        logger.info { "[GE_TRACE][EVENT] ${tracePlayer()} $entry" }
        observability.record(player, "GE", event, entry)
    }

    private fun ProtectedAccess.traceNativeGeState(context: String, slot: Int, expectedObj: Int) {
        val selectedSlot = vars[auric_ge_varbits.selectedSlot]
        val nativeZeroBasedSlot = selectedSlot - 1
        val newType = vars[auric_ge_varbits.newOfferType]
        val newQuantity = vars[auric_ge_varbits.newOfferQuantity]
        val newPrice = vars[auric_ge_varbits.newOfferPrice]
        val lastObj = vars[auric_ge_varps.lastOfferItem]
        val lastQuantity = vars[auric_ge_varps.lastOfferQuantity]
        val lastPrice = vars[auric_ge_varps.lastOfferPrice]
        val lastType = vars[auric_ge_varps.lastOfferType]
        val sinkObj = nativeDetailObjVarps.getOrNull(slot)?.let { vars[it] }
        val sinkPrice = nativeDetailPriceVarps.getOrNull(slot)?.let { vars[it] }
        logger.info {
            "[GE_TRACE][NATIVE_STATE] context=$context ${tracePlayer()} slot=$slot selectedSlot=$selectedSlot " +
                "nativeZeroBasedSlot=$nativeZeroBasedSlot " +
                "expectedObj=$expectedObj expectedName='${objName(expectedObj)}' " +
                "lastOffer={obj=$lastObj,name='${objName(lastObj)}',qty=$lastQuantity,price=$lastPrice,type=$lastType} " +
                "newOffer={qty=$newQuantity,price=$newPrice,type=$newType} " +
                "itemSink={obj=$sinkObj,name='${objName(sinkObj ?: -1)}',price=$sinkPrice} " +
                "session=${sessions[player.characterId]}"
        }
    }

    private fun formatOfferLine(offer: AuricGrandExchangeRepository.Offer): String {
        val action = offer.type.replaceFirstChar(Char::uppercaseChar)
        return "Slot ${offer.slot + 1}: $action ${objName(offer.obj)} ${offer.completedCount}/${offer.requestedCount} @ ${offer.priceEach} gp (${offer.status})"
    }

    private val AuricGrandExchangeRepository.Offer.statusLabel: String
        get() =
            when (status) {
                "completed" -> "Completed - collect your items or coins."
                "partial" -> "Partially complete - collect ready items or wait for more."
                "open" -> "Active - waiting for a matching offer."
                "cancelled" -> "Cancelled - collect returned escrow."
                else -> "Status: $status"
            }

    private val AuricGrandExchangeRepository.Offer.stateWord: String
        get() =
            when (status) {
                "completed" -> "Completed"
                "partial" -> "Partial"
                "open" -> "Active"
                "cancelled" -> "Cancelled"
                else -> status.replaceFirstChar(Char::uppercaseChar)
            }

    private val AuricGrandExchangeRepository.Offer.stockMarketStatus: Int
        get() {
            val lowerStatus = if (isActive) StockMarketStable else StockMarketFinished
            val sellBit = if (type == "sell") StockMarketSellBit else 0
            return sellBit or lowerStatus
        }

    private fun objName(obj: Int): String = objTypes.types[obj]?.name ?: "item $obj"

    private fun UnpackedObjType.canExchange(): Boolean = tradeable || stockmarket

    private val AuricGrandExchangeRepository.Offer.isActive: Boolean
        get() = status == "open" || status == "partial"

    private fun AuricGrandExchangeRepository.Offer.trace(): String =
        "Offer(id=$id,slot=$slot,type=$type,obj=$obj,price=$priceEach,requested=$requestedCount,completed=$completedCount,status=$status,deposited=$depositedCoins,released=$releasedCoins)"

    private fun AuricGrandExchangeRepository.Collection.trace(): String =
        "Collection(id=$id,offer=$offerId,obj=$obj,count=$count)"

    private enum class OfferType(val label: String) {
        Buy("Buy"),
        Sell("Sell"),
    }

    private data class ExchangeSession(
        val selectedSlot: Int = 0,
        val selectedOfferId: Int? = null,
        val pendingType: OfferType? = null,
        val pendingObj: Int = -1,
        val pendingQuantity: Int = 0,
        val pendingPrice: Int = 0,
    )

    companion object {
        private const val DefaultRealmId = 1
        private const val StockMarketStable = 2
        private const val StockMarketFinished = 5
        private const val StockMarketSellBit = 8
        private val indexComponents =
            listOf(
                auric_ge_components.index0,
                auric_ge_components.index1,
                auric_ge_components.index2,
                auric_ge_components.index3,
                auric_ge_components.index4,
                auric_ge_components.index5,
                auric_ge_components.index6,
                auric_ge_components.index7,
            )
        private val nativeOfferInvs: List<InvType> =
            listOf(
                auric_ge_invs.offer0,
                auric_ge_invs.offer1,
                auric_ge_invs.offer2,
                auric_ge_invs.offer3,
                auric_ge_invs.offer4,
                auric_ge_invs.offer5,
                auric_ge_invs.offer6,
                auric_ge_invs.offer7,
            )
        private val nativeDetailObjVarps =
            listOf(
                auric_ge_varps.itemSinkObj0,
                auric_ge_varps.itemSinkObj1,
                auric_ge_varps.itemSinkObj2,
                auric_ge_varps.itemSinkObj3,
                auric_ge_varps.itemSinkObj4,
                auric_ge_varps.itemSinkObj5,
                auric_ge_varps.itemSinkObj6,
                auric_ge_varps.itemSinkObj7,
            )
        private val nativeDetailPriceVarps =
            listOf(
                auric_ge_varps.itemSinkPrice0,
                auric_ge_varps.itemSinkPrice1,
                auric_ge_varps.itemSinkPrice2,
                auric_ge_varps.itemSinkPrice3,
                auric_ge_varps.itemSinkPrice4,
                auric_ge_varps.itemSinkPrice5,
                auric_ge_varps.itemSinkPrice6,
                auric_ge_varps.itemSinkPrice7,
            )
    }
}
