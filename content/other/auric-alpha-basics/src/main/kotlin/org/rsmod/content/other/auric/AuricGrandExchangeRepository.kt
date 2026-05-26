package org.rsmod.content.other.auric

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import java.sql.Statement
import org.rsmod.api.db.Database
import org.rsmod.api.db.DatabaseConnection

class AuricGrandExchangeRepository @Inject constructor(private val database: Database) {
    private val logger = InlineLogger()

    data class Offer(
        val id: Int,
        val slot: Int,
        val type: String,
        val obj: Int,
        val priceEach: Int,
        val requestedCount: Int,
        val completedCount: Int,
        val status: String,
        val depositedCoins: Int,
        val releasedCoins: Int,
    ) {
        val remaining: Int
            get() = requestedCount - completedCount
    }

    data class Collection(val id: Int, val offerId: Int, val obj: Int, val count: Int)

    data class OfferResult(val offerId: Int, val slot: Int, val matchedCount: Int)

    data class SelfTestResult(val passed: Boolean, val details: List<String>)

    suspend fun countActiveOffers(characterId: Int): Int =
        database.withTransaction { connection ->
            connection.cleanupCollectedOffers(characterId)
            connection
                .prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM grand_exchange_offers
                    WHERE character_id = ?
                        AND status IN ('open', 'partial')
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, characterId)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.getInt(1) else 0
                    }
                }
        }

    suspend fun listOffers(characterId: Int): List<Offer> =
        database.withTransaction { connection ->
            connection.cleanupCollectedOffers(characterId)
            connection
                .prepareStatement(
                    """
                    SELECT id, slot, offer_type, obj, price_each, requested_count, completed_count,
                        status, deposited_coins, spent_coins
                    FROM grand_exchange_offers
                    WHERE character_id = ?
                    ORDER BY slot ASC, id DESC
                    LIMIT 16
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, characterId)
                    statement.executeQuery().use { result -> result.toOffers() }
                }
        }

    suspend fun listCollections(characterId: Int): List<Collection> =
        database.withTransaction { connection ->
            connection
                .prepareStatement(
                    """
                    SELECT id, offer_id, obj, count
                    FROM grand_exchange_collections
                    WHERE character_id = ?
                    ORDER BY id ASC
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, characterId)
                    statement.executeQuery().use { result ->
                        val collections = mutableListOf<Collection>()
                        while (result.next()) {
                            collections +=
                                Collection(
                                    id = result.getInt("id"),
                                    offerId = result.getInt("offer_id"),
                                    obj = result.getInt("obj"),
                                    count = result.getInt("count"),
                                )
                        }
                        collections
                    }
                }
        }

    suspend fun deleteCollection(characterId: Int, collectionId: Int): Boolean =
        database.withTransaction { connection ->
            val offerId = connection.offerIdForCollection(characterId, collectionId) ?: return@withTransaction false
            val deleted =
                connection
                    .prepareStatement(
                        """
                        DELETE FROM grand_exchange_collections
                        WHERE id = ? AND character_id = ?
                        """
                            .trimIndent()
                    )
                    .use { statement ->
                        statement.setInt(1, collectionId)
                        statement.setInt(2, characterId)
                        statement.executeUpdate() == 1
                    }
            if (deleted) {
                logger.info {
                    "[GE_DB][COLLECTION_DELETED] character=$characterId collection=$collectionId offer=$offerId"
                }
                connection.cleanupOfferIfFullyClaimed(characterId, offerId)
            } else {
                logger.info {
                    "[GE_DB][COLLECTION_DELETE_MISS] character=$characterId collection=$collectionId offer=$offerId"
                }
            }
            deleted
        }

    suspend fun restoreCollection(collection: Collection, characterId: Int) {
        database.withTransaction { connection ->
            connection.addCollection(collection.offerId, characterId, collection.obj, collection.count)
        }
    }

    suspend fun runSelfTest(realmId: Int, accountId: Int, characterId: Int, coinsObj: Int): SelfTestResult =
        database.withTransaction { connection ->
            val createdOffers = mutableListOf<Int>()
            val details = mutableListOf<String>()
            try {
                val olderBuy =
                    connection.insertSelfTestOffer(
                        realmId = realmId,
                        accountId = accountId,
                        characterId = characterId,
                        slot = MaxOfferSlots,
                        type = "buy",
                        obj = SelfTestObj,
                        priceEach = 100,
                        count = 10,
                        depositedCoins = 1_000,
                    )
                createdOffers += olderBuy
                val newerSell =
                    connection.insertSelfTestOffer(
                        realmId = realmId,
                        accountId = accountId,
                        characterId = characterId,
                        slot = MaxOfferSlots + 1,
                        type = "sell",
                        obj = SelfTestObj,
                        priceEach = 60,
                        count = 4,
                        depositedCoins = 0,
                    )
                createdOffers += newerSell

                val matched = connection.matchSellOffer(newerSell, SelfTestObj, minPrice = 60, requestedCount = 4, coinsObj)
                details += "same-character sell-into-buy matched=$matched"

                val buyOffer = connection.selectOffer(olderBuy)
                val sellOffer = connection.selectOffer(newerSell)
                val buyItems = connection.collectionTotal(olderBuy, characterId, SelfTestObj)
                val sellCoins = connection.collectionTotal(newerSell, characterId, coinsObj)

                check(matched == 0) { "Expected same-character offers not to match, got $matched." }
                check(buyOffer.completedCount == 0 && buyOffer.status == "open") {
                    "Expected older buy to remain open 0/10, got ${buyOffer.completedCount}/${buyOffer.requestedCount} ${buyOffer.status}."
                }
                check(buyOffer.releasedCoins == 0) {
                    "Expected older buy released escrow 0, got ${buyOffer.releasedCoins}."
                }
                check(sellOffer.completedCount == 0 && sellOffer.status == "open") {
                    "Expected newer sell to remain open 0/4, got ${sellOffer.completedCount}/${sellOffer.requestedCount} ${sellOffer.status}."
                }
                check(buyItems == 0) { "Expected buyer collection 0 test items, got $buyItems." }
                check(sellCoins == 0) { "Expected seller collection 0 coins, got $sellCoins." }

                val olderSell =
                    connection.insertSelfTestOffer(
                        realmId = realmId,
                        accountId = accountId,
                        characterId = characterId,
                        slot = MaxOfferSlots + 2,
                        type = "sell",
                        obj = SelfTestObj + 1,
                        priceEach = 60,
                        count = 4,
                        depositedCoins = 0,
                    )
                createdOffers += olderSell
                val newerBuy =
                    connection.insertSelfTestOffer(
                        realmId = realmId,
                        accountId = accountId,
                        characterId = characterId,
                        slot = MaxOfferSlots + 3,
                        type = "buy",
                        obj = SelfTestObj + 1,
                        priceEach = 100,
                        count = 10,
                        depositedCoins = 1_000,
                    )
                createdOffers += newerBuy

                val buyMatched = connection.matchBuyOffer(newerBuy, SelfTestObj + 1, maxPrice = 100, requestedCount = 10, coinsObj)
                details += "same-character buy-into-sell matched=$buyMatched"

                val buyAfter = connection.selectOffer(newerBuy)
                val sellAfter = connection.selectOffer(olderSell)
                val buyerItems = connection.collectionTotal(newerBuy, characterId, SelfTestObj + 1)
                val buyerRefund = connection.collectionTotal(newerBuy, characterId, coinsObj)
                val sellerCoins = connection.collectionTotal(olderSell, characterId, coinsObj)

                check(buyMatched == 0) { "Expected same-character buy not to match, got $buyMatched." }
                check(buyAfter.completedCount == 0 && buyAfter.status == "open") {
                    "Expected newer buy to remain open 0/10, got ${buyAfter.completedCount}/${buyAfter.requestedCount} ${buyAfter.status}."
                }
                check(buyAfter.releasedCoins == 0) {
                    "Expected newer buy released escrow 0, got ${buyAfter.releasedCoins}."
                }
                check(sellAfter.completedCount == 0 && sellAfter.status == "open") {
                    "Expected older sell to remain open 0/4, got ${sellAfter.completedCount}/${sellAfter.requestedCount} ${sellAfter.status}."
                }
                check(buyerItems == 0) { "Expected buyer collection 0 test items, got $buyerItems." }
                check(buyerRefund == 0) { "Expected buyer overpay refund 0 coins, got $buyerRefund." }
                check(sellerCoins == 0) { "Expected seller collection 0 coins, got $sellerCoins." }

                details += "live database GE same-character safety passed"
                SelfTestResult(passed = true, details = details)
            } catch (t: Throwable) {
                details += "FAILED: ${t.message ?: t::class.simpleName}"
                SelfTestResult(passed = false, details = details)
            } finally {
                connection.deleteOffers(createdOffers)
            }
        }

    suspend fun createBuyOffer(
        realmId: Int,
        accountId: Int,
        characterId: Int,
        slot: Int? = null,
        obj: Int,
        priceEach: Int,
        count: Int,
        coinsObj: Int,
    ): OfferResult =
        database.withTransaction { connection ->
            logger.info {
                "[GE_DB][CREATE_BUY_START] realm=$realmId account=$accountId character=$characterId " +
                    "slot=$slot obj=$obj price=$priceEach count=$count"
            }
            val slot = slot ?: connection.nextOpenSlot(characterId)
            connection.requireOpenSlot(characterId, slot)
            val depositedCoins = priceEach * count
            val offerId =
                connection
                    .prepareStatement(
                        """
                        INSERT INTO grand_exchange_offers (
                            realm_id, account_id, character_id, slot, offer_type, obj, price_each,
                            requested_count, completed_count, status, deposited_coins, spent_coins
                        )
                        VALUES (?, ?, ?, ?, 'buy', ?, ?, ?, 0, 'open', ?, 0)
                        """
                            .trimIndent(),
                        Statement.RETURN_GENERATED_KEYS,
                    )
                    .use { statement ->
                        statement.setInt(1, realmId)
                        statement.setInt(2, accountId)
                        statement.setInt(3, characterId)
                        statement.setInt(4, slot)
                        statement.setInt(5, obj)
                        statement.setInt(6, priceEach)
                        statement.setInt(7, count)
                        statement.setInt(8, depositedCoins)
                        statement.executeUpdate()
                        statement.generatedKeys.use { keys ->
                            check(keys.next()) { "No generated key returned for GE buy offer." }
                            keys.getInt(1)
                        }
                    }
            val matched = connection.matchBuyOffer(offerId, obj, priceEach, count, coinsObj)
            val result = OfferResult(offerId, slot, matched)
            logger.info { "[GE_DB][CREATE_BUY_DONE] character=$characterId result=$result" }
            result
        }

    suspend fun createSellOffer(
        realmId: Int,
        accountId: Int,
        characterId: Int,
        slot: Int? = null,
        obj: Int,
        priceEach: Int,
        count: Int,
        coinsObj: Int,
    ): OfferResult =
        database.withTransaction { connection ->
            logger.info {
                "[GE_DB][CREATE_SELL_START] realm=$realmId account=$accountId character=$characterId " +
                    "slot=$slot obj=$obj price=$priceEach count=$count"
            }
            val slot = slot ?: connection.nextOpenSlot(characterId)
            connection.requireOpenSlot(characterId, slot)
            val offerId =
                connection
                    .prepareStatement(
                        """
                        INSERT INTO grand_exchange_offers (
                            realm_id, account_id, character_id, slot, offer_type, obj, price_each,
                            requested_count, completed_count, status, deposited_coins, spent_coins
                        )
                        VALUES (?, ?, ?, ?, 'sell', ?, ?, ?, 0, 'open', 0, 0)
                        """
                            .trimIndent(),
                        Statement.RETURN_GENERATED_KEYS,
                    )
                    .use { statement ->
                        statement.setInt(1, realmId)
                        statement.setInt(2, accountId)
                        statement.setInt(3, characterId)
                        statement.setInt(4, slot)
                        statement.setInt(5, obj)
                        statement.setInt(6, priceEach)
                        statement.setInt(7, count)
                        statement.executeUpdate()
                        statement.generatedKeys.use { keys ->
                            check(keys.next()) { "No generated key returned for GE sell offer." }
                            keys.getInt(1)
                        }
                    }
            val matched = connection.matchSellOffer(offerId, obj, priceEach, count, coinsObj)
            val result = OfferResult(offerId, slot, matched)
            logger.info { "[GE_DB][CREATE_SELL_DONE] character=$characterId result=$result" }
            result
        }

    suspend fun cancelOffer(characterId: Int, offerId: Int, coinsObj: Int): Boolean =
        database.withTransaction { connection ->
            val offer = connection.selectActiveOffer(characterId, offerId) ?: return@withTransaction false
            logger.info { "[GE_DB][CANCEL_START] character=$characterId offer=${offer.trace()}" }
            if (offer.type == "buy") {
                val refund = offer.depositedCoins - offer.releasedCoins
                if (refund > 0) {
                    logger.info { "[GE_DB][CANCEL_REFUND_COINS] character=$characterId offer=$offerId count=$refund" }
                    connection.addCollection(offer.id, characterId, coinsObj, refund)
                }
            } else if (offer.remaining > 0) {
                logger.info { "[GE_DB][CANCEL_REFUND_ITEMS] character=$characterId offer=$offerId obj=${offer.obj} count=${offer.remaining}" }
                connection.addCollection(offer.id, characterId, offer.obj, offer.remaining)
            }
            connection.updateOfferStatus(offer.id, offer.completedCount, "cancelled")
            logger.info { "[GE_DB][CANCEL_DONE] character=$characterId offer=$offerId" }
            true
        }

    private fun DatabaseConnection.nextOpenSlot(characterId: Int): Int {
        cleanupCollectedOffers(characterId)
        val used =
            prepareStatement(
                    """
                    SELECT slot
                    FROM grand_exchange_offers
                    WHERE character_id = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, characterId)
                    statement.executeQuery().use { result ->
                        val slots = mutableSetOf<Int>()
                        while (result.next()) {
                            slots += result.getInt("slot")
                        }
                        slots
                    }
                }
        return (0 until MaxOfferSlots).firstOrNull { it !in used }
            ?: error("No free Grand Exchange offer slots.")
    }

    private fun DatabaseConnection.requireOpenSlot(characterId: Int, slot: Int) {
        require(slot in 0 until MaxOfferSlots) { "Invalid Grand Exchange slot: $slot" }
        cleanupCollectedOffers(characterId)
        val occupied =
            prepareStatement(
                    """
                    SELECT COUNT(*)
                    FROM grand_exchange_offers
                    WHERE character_id = ? AND slot = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, characterId)
                    statement.setInt(2, slot)
                    statement.executeQuery().use { result -> if (result.next()) result.getInt(1) else 0 }
                }
        check(occupied == 0) { "Grand Exchange slot ${slot + 1} is already occupied." }
    }

    private fun DatabaseConnection.matchBuyOffer(
        buyOfferId: Int,
        obj: Int,
        maxPrice: Int,
        requestedCount: Int,
        coinsObj: Int,
    ): Int {
        var completed = 0
        var matched = 0
        val ownerCharacterId = characterIdForOffer(buyOfferId)
        val sellers = selectMatchingOffers(obj, offerType = "sell", price = maxPrice, buyOfferId, ownerCharacterId)
        logger.info {
            "[GE_DB][MATCH_BUY_START] buyOffer=$buyOfferId owner=$ownerCharacterId obj=$obj maxPrice=$maxPrice " +
                "requested=$requestedCount sellers=${sellers.joinToString(prefix = "[", postfix = "]") { it.trace() }}"
        }
        for (seller in sellers) {
            val remaining = requestedCount - completed
            if (remaining <= 0) break
            val count = minOf(remaining, seller.remaining)
            val coins = seller.priceEach * count
            val refund = (maxPrice - seller.priceEach) * count
            addCollection(buyOfferId, ownerCharacterId, obj, count)
            addCollection(buyOfferId, ownerCharacterId, coinsObj, refund)
            addCollection(seller.id, characterIdForOffer(seller.id), coinsObj, coins)
            updateOfferStatus(seller.id, seller.completedCount + count)
            completed += count
            updateOfferStatus(buyOfferId, completed, extraSpentCoins = coins + refund)
            matched += count
            logger.info {
                "[GE_DB][MATCH_BUY_STEP] buyOffer=$buyOfferId seller=${seller.id} count=$count coins=$coins " +
                    "refund=$refund completed=$completed matched=$matched"
            }
        }
        refundCompletedBuyOffer(buyOfferId, coinsObj)
        logger.info { "[GE_DB][MATCH_BUY_DONE] buyOffer=$buyOfferId matched=$matched" }
        return matched
    }

    private fun DatabaseConnection.matchSellOffer(
        sellOfferId: Int,
        obj: Int,
        minPrice: Int,
        requestedCount: Int,
        coinsObj: Int,
    ): Int {
        var completed = 0
        var matched = 0
        val ownerCharacterId = characterIdForOffer(sellOfferId)
        val buyers = selectMatchingOffers(obj, offerType = "buy", price = minPrice, sellOfferId, ownerCharacterId)
        logger.info {
            "[GE_DB][MATCH_SELL_START] sellOffer=$sellOfferId owner=$ownerCharacterId obj=$obj minPrice=$minPrice " +
                "requested=$requestedCount buyers=${buyers.joinToString(prefix = "[", postfix = "]") { it.trace() }}"
        }
        for (buyer in buyers) {
            val remaining = requestedCount - completed
            if (remaining <= 0) break
            val count = minOf(remaining, buyer.remaining)
            val coins = buyer.priceEach * count
            addCollection(buyer.id, characterIdForOffer(buyer.id), obj, count)
            addCollection(sellOfferId, characterIdForOffer(sellOfferId), coinsObj, coins)
            updateOfferStatus(buyer.id, buyer.completedCount + count, extraSpentCoins = coins)
            refundCompletedBuyOffer(buyer.id, coinsObj)
            completed += count
            updateOfferStatus(sellOfferId, completed)
            matched += count
            logger.info {
                "[GE_DB][MATCH_SELL_STEP] sellOffer=$sellOfferId buyer=${buyer.id} count=$count coins=$coins " +
                    "completed=$completed matched=$matched"
            }
        }
        logger.info { "[GE_DB][MATCH_SELL_DONE] sellOffer=$sellOfferId matched=$matched" }
        return matched
    }

    private fun DatabaseConnection.selectMatchingOffers(
        obj: Int,
        offerType: String,
        price: Int,
        excludeOfferId: Int,
        excludeCharacterId: Int,
    ): List<Offer> {
        val priceClause = if (offerType == "sell") "price_each <= ?" else "price_each >= ?"
        val order = if (offerType == "sell") "price_each ASC" else "price_each DESC"
        return prepareStatement(
                """
                SELECT id, slot, offer_type, obj, price_each, requested_count, completed_count,
                    status, deposited_coins, spent_coins
                FROM grand_exchange_offers
                WHERE obj = ?
                    AND offer_type = ?
                    AND $priceClause
                    AND status IN ('open', 'partial')
                    AND id != ?
                    AND character_id != ?
                ORDER BY $order, id ASC
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, obj)
                statement.setString(2, offerType)
                statement.setInt(3, price)
                statement.setInt(4, excludeOfferId)
                statement.setInt(5, excludeCharacterId)
                statement.executeQuery().use { result -> result.toOffers() }
            }
    }

    private fun DatabaseConnection.selectActiveOffer(characterId: Int, offerId: Int): Offer? =
        prepareStatement(
                """
                SELECT id, slot, offer_type, obj, price_each, requested_count, completed_count,
                    status, deposited_coins, spent_coins
                FROM grand_exchange_offers
                WHERE id = ? AND character_id = ? AND status IN ('open', 'partial')
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, offerId)
                statement.setInt(2, characterId)
                statement.executeQuery().use { result ->
                    if (!result.next()) null else result.toOffer()
                }
            }

    private fun DatabaseConnection.selectOffer(offerId: Int): Offer =
        prepareStatement(
                """
                SELECT id, slot, offer_type, obj, price_each, requested_count, completed_count,
                    status, deposited_coins, spent_coins
                FROM grand_exchange_offers
                WHERE id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, offerId)
                statement.executeQuery().use { result ->
                    check(result.next()) { "Could not resolve GE offer: $offerId" }
                    result.toOffer()
                }
            }

    private fun DatabaseConnection.characterIdForOffer(offerId: Int): Int =
        prepareStatement("SELECT character_id FROM grand_exchange_offers WHERE id = ?").use {
            statement ->
            statement.setInt(1, offerId)
            statement.executeQuery().use { result ->
                check(result.next()) { "Could not resolve character id for GE offer: $offerId" }
                result.getInt("character_id")
            }
        }

    private fun DatabaseConnection.offerIdForCollection(characterId: Int, collectionId: Int): Int? =
        prepareStatement(
                """
                SELECT offer_id
                FROM grand_exchange_collections
                WHERE id = ? AND character_id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, collectionId)
                statement.setInt(2, characterId)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getInt("offer_id") else null
                }
            }

    private fun DatabaseConnection.addCollection(
        offerId: Int,
        characterId: Int,
        obj: Int,
        count: Int,
    ) {
        if (count <= 0) {
            return
        }
        logger.info { "[GE_DB][ADD_COLLECTION] offer=$offerId character=$characterId obj=$obj count=$count" }
        prepareStatement(
                """
                INSERT INTO grand_exchange_collections (offer_id, character_id, obj, count)
                VALUES (?, ?, ?, ?)
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, offerId)
                statement.setInt(2, characterId)
                statement.setInt(3, obj)
                statement.setInt(4, count)
                statement.executeUpdate()
            }
    }

    private fun DatabaseConnection.cleanupCollectedOffers(characterId: Int) {
        val deleted =
            prepareStatement(
                """
                DELETE FROM grand_exchange_offers
                WHERE character_id = ?
                    AND status IN ('completed', 'cancelled')
                    AND NOT EXISTS (
                        SELECT 1
                        FROM grand_exchange_collections
                        WHERE grand_exchange_collections.offer_id = grand_exchange_offers.id
                    )
                """
                    .trimIndent()
            )
                .use { statement ->
                    statement.setInt(1, characterId)
                    statement.executeUpdate()
                }
        if (deleted > 0) {
            logger.info { "[GE_DB][CLEANUP_COLLECTED_OFFERS] character=$characterId deleted=$deleted" }
        }
  }

  private fun DatabaseConnection.cleanupOfferIfFullyClaimed(characterId: Int, offerId: Int) {
      val deleted =
          prepareStatement(
                """
                DELETE FROM grand_exchange_offers
                WHERE id = ?
                    AND character_id = ?
                    AND status IN ('completed', 'cancelled')
                    AND NOT EXISTS (
                        SELECT 1
                        FROM grand_exchange_collections
                        WHERE grand_exchange_collections.offer_id = grand_exchange_offers.id
                    )
                """
                    .trimIndent()
          )
              .use { statement ->
                  statement.setInt(1, offerId)
                  statement.setInt(2, characterId)
                  statement.executeUpdate()
              }
      logger.info { "[GE_DB][CLEANUP_OFFER_IF_CLAIMED] character=$characterId offer=$offerId deleted=$deleted" }
  }

    private fun DatabaseConnection.collectionTotal(offerId: Int, characterId: Int, obj: Int): Int =
        prepareStatement(
                """
                SELECT COALESCE(SUM(count), 0)
                FROM grand_exchange_collections
                WHERE offer_id = ? AND character_id = ? AND obj = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, offerId)
                statement.setInt(2, characterId)
                statement.setInt(3, obj)
                statement.executeQuery().use { result ->
                    if (result.next()) result.getInt(1) else 0
                }
            }

    private fun DatabaseConnection.insertSelfTestOffer(
        realmId: Int,
        accountId: Int,
        characterId: Int,
        slot: Int,
        type: String,
        obj: Int,
        priceEach: Int,
        count: Int,
        depositedCoins: Int,
    ): Int =
        prepareStatement(
                """
                INSERT INTO grand_exchange_offers (
                    realm_id, account_id, character_id, slot, offer_type, obj, price_each,
                    requested_count, completed_count, status, deposited_coins, spent_coins
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 'open', ?, 0)
                """
                    .trimIndent(),
                Statement.RETURN_GENERATED_KEYS,
            )
            .use { statement ->
                statement.setInt(1, realmId)
                statement.setInt(2, accountId)
                statement.setInt(3, characterId)
                statement.setInt(4, slot)
                statement.setString(5, type)
                statement.setInt(6, obj)
                statement.setInt(7, priceEach)
                statement.setInt(8, count)
                statement.setInt(9, depositedCoins)
                statement.executeUpdate()
                statement.generatedKeys.use { keys ->
                    check(keys.next()) { "No generated key returned for GE self-test offer." }
                    keys.getInt(1)
                }
            }

    private fun DatabaseConnection.deleteOffers(offerIds: List<Int>) {
        if (offerIds.isEmpty()) {
            return
        }
        prepareStatement(
                """
                DELETE FROM grand_exchange_offers
                WHERE id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                for (offerId in offerIds) {
                    statement.setInt(1, offerId)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
    }

    private fun DatabaseConnection.updateOfferStatus(
        offerId: Int,
        completedCount: Int,
        statusOverride: String? = null,
        extraSpentCoins: Int = 0,
    ) {
        val status =
            statusOverride
                ?: prepareStatement(
                        """
                        SELECT requested_count
                        FROM grand_exchange_offers
                        WHERE id = ?
                        """
                            .trimIndent()
                    )
                    .use { statement ->
                        statement.setInt(1, offerId)
                        statement.executeQuery().use { result ->
                            check(result.next()) { "Could not resolve GE offer status: $offerId" }
                            val requested = result.getInt("requested_count")
                            if (completedCount >= requested) "completed" else "partial"
                        }
                    }
        prepareStatement(
                """
                UPDATE grand_exchange_offers
                SET completed_count = ?, status = ?, spent_coins = spent_coins + ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setInt(1, completedCount)
                statement.setString(2, status)
                statement.setInt(3, extraSpentCoins)
                statement.setInt(4, offerId)
                statement.executeUpdate()
            }
    }

    private fun DatabaseConnection.refundCompletedBuyOffer(offerId: Int, coinsObj: Int) {
        val offer =
            prepareStatement(
                    """
                    SELECT id, slot, offer_type, obj, price_each, requested_count, completed_count,
                        status, deposited_coins, spent_coins
                    FROM grand_exchange_offers
                    WHERE id = ?
                    """
                        .trimIndent()
                )
                .use { statement ->
                    statement.setInt(1, offerId)
                    statement.executeQuery().use { result ->
                        if (!result.next()) return
                        result.toOffer()
                    }
                }
        if (offer.type != "buy" || offer.completedCount < offer.requestedCount) {
            return
        }
        val refund = offer.depositedCoins - offer.releasedCoins
        if (refund > 0) {
            logger.info { "[GE_DB][REFUND_COMPLETED_BUY] offer=$offerId refund=$refund" }
            addCollection(offer.id, characterIdForOffer(offer.id), coinsObj, refund)
            updateOfferStatus(offer.id, offer.completedCount, extraSpentCoins = refund)
        }
    }

    private fun java.sql.ResultSet.toOffers(): List<Offer> {
        val offers = mutableListOf<Offer>()
        while (next()) {
            offers += toOffer()
        }
        return offers
    }

    private fun java.sql.ResultSet.toOffer(): Offer =
        Offer(
            id = getInt("id"),
            slot = getInt("slot"),
            type = getString("offer_type"),
            obj = getInt("obj"),
            priceEach = getInt("price_each"),
            requestedCount = getInt("requested_count"),
            completedCount = getInt("completed_count"),
            status = getString("status"),
            depositedCoins = getInt("deposited_coins"),
            releasedCoins = getInt("spent_coins"),
        )

    companion object {
        const val MaxOfferSlots = 8
        private const val SelfTestObj = 2_147_000_001
    }
}

private fun AuricGrandExchangeRepository.Offer.trace(): String =
    "Offer(id=$id,slot=$slot,type=$type,obj=$obj,price=$priceEach,requested=$requestedCount,completed=$completedCount,status=$status,deposited=$depositedCoins,released=$releasedCoins)"
