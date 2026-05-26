package org.rsmod.content.other.auric

import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.rsmod.api.db.Database
import org.rsmod.api.db.DatabaseConnection

class AuricGrandExchangeRepositoryTest {
    private lateinit var database: TestDatabase
    private lateinit var repository: AuricGrandExchangeRepository

    @BeforeEach
    fun setUp() {
        database = TestDatabase()
        repository = AuricGrandExchangeRepository(database)
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    fun `new sell matching older higher buy pays existing buy price`() = runBlocking {
        repository.createBuyOffer(
            realmId = Realm,
            accountId = 1,
            characterId = Buyer,
            obj = Item,
            priceEach = 100,
            count = 10,
            coinsObj = Coins,
        )

        val sell =
            repository.createSellOffer(
                realmId = Realm,
                accountId = 2,
                characterId = Seller,
                obj = Item,
                priceEach = 60,
                count = 4,
                coinsObj = Coins,
            )

        assertEquals(4, sell.matchedCount)
        assertCollection(Buyer, Item, 4)
        assertCollection(Buyer, Coins, 0)
        assertCollection(Seller, Coins, 400)
        assertOffer(Buyer, completed = 4, releasedCoins = 400, status = "partial")
        assertOffer(Seller, completed = 4, releasedCoins = 0, status = "completed")
    }

    @Test
    fun `new buy matching older cheaper sell refunds overpay on partial fill`() = runBlocking {
        repository.createSellOffer(
            realmId = Realm,
            accountId = 2,
            characterId = Seller,
            obj = Item,
            priceEach = 60,
            count = 4,
            coinsObj = Coins,
        )

        val buy =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                obj = Item,
                priceEach = 100,
                count = 10,
                coinsObj = Coins,
            )

        assertEquals(4, buy.matchedCount)
        assertCollection(Buyer, Item, 4)
        assertCollection(Buyer, Coins, 160)
        assertCollection(Seller, Coins, 240)
        assertOffer(Buyer, completed = 4, releasedCoins = 400, status = "partial")

        val cancelled = repository.cancelOffer(Buyer, buy.offerId, Coins)

        assertEquals(true, cancelled)
        assertCollection(Buyer, Coins, 760)
        assertOffer(Buyer, completed = 4, releasedCoins = 400, status = "cancelled")
    }

    @Test
    fun `completed buy offer overpay is refunded once`() = runBlocking {
        repository.createSellOffer(
            realmId = Realm,
            accountId = 2,
            characterId = Seller,
            obj = Item,
            priceEach = 60,
            count = 4,
            coinsObj = Coins,
        )

        val buy =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                obj = Item,
                priceEach = 100,
                count = 4,
                coinsObj = Coins,
            )

        assertEquals(4, buy.matchedCount)
        assertCollection(Buyer, Item, 4)
        assertCollection(Buyer, Coins, 160)
        assertCollection(Seller, Coins, 240)
        assertOffer(Buyer, completed = 4, releasedCoins = 400, status = "completed")
    }

    @Test
    fun `offers from the same character do not match each other`() = runBlocking {
        val buy =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                obj = Item,
                priceEach = 100,
                count = 10,
                coinsObj = Coins,
            )

        val sell =
            repository.createSellOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                obj = Item,
                priceEach = 60,
                count = 4,
                coinsObj = Coins,
            )

        assertEquals(0, buy.matchedCount)
        assertEquals(0, sell.matchedCount)
        assertEquals(2, repository.listOffers(Buyer).size)
        assertCollection(Buyer, Item, 0)
        assertCollection(Buyer, Coins, 0)
    }

    @Test
    fun `explicit offer slot is honored and cannot be reused while occupied`() = runBlocking {
        val buy =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                slot = 3,
                obj = Item,
                priceEach = 100,
                count = 10,
                coinsObj = Coins,
            )

        assertEquals(3, buy.slot)
        assertEquals(3, repository.listOffers(Buyer).single().slot)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.createSellOffer(
                    realmId = Realm,
                    accountId = 1,
                    characterId = Buyer,
                    slot = 3,
                    obj = Item + 1,
                    priceEach = 100,
                    count = 1,
                    coinsObj = Coins,
                )
            }
        }
    }

    @Test
    fun `cancelled offer frees slot after refund collection is claimed`() = runBlocking {
        val buy =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                slot = 2,
                obj = Item,
                priceEach = 100,
                count = 10,
                coinsObj = Coins,
            )

        assertEquals(true, repository.cancelOffer(Buyer, buy.offerId, Coins))
        val refund = repository.listCollections(Buyer).single()
        assertEquals(true, repository.deleteCollection(Buyer, refund.id))
        assertEquals(emptyList<AuricGrandExchangeRepository.Offer>(), repository.listOffers(Buyer))

        val replacement =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                slot = 2,
                obj = Item + 1,
                priceEach = 50,
                count = 1,
                coinsObj = Coins,
            )
        assertEquals(2, replacement.slot)
    }

    @Test
    fun `completed offer frees slot after completed collection is claimed`() = runBlocking {
        repository.createSellOffer(
            realmId = Realm,
            accountId = 2,
            characterId = Seller,
            obj = Item,
            priceEach = 100,
            count = 4,
            coinsObj = Coins,
        )
        val buy =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                slot = 1,
                obj = Item,
                priceEach = 100,
                count = 4,
                coinsObj = Coins,
            )

        assertEquals(4, buy.matchedCount)
        val collection = repository.listCollections(Buyer).single()
        assertEquals(true, repository.deleteCollection(Buyer, collection.id))
        assertEquals(emptyList<AuricGrandExchangeRepository.Offer>(), repository.listOffers(Buyer))

        val replacement =
            repository.createBuyOffer(
                realmId = Realm,
                accountId = 1,
                characterId = Buyer,
                slot = 1,
                obj = Item + 1,
                priceEach = 50,
                count = 1,
                coinsObj = Coins,
            )
        assertEquals(1, replacement.slot)
    }

    private suspend fun assertCollection(characterId: Int, obj: Int, count: Int) {
        val total = repository.listCollections(characterId).filter { it.obj == obj }.sumOf { it.count }
        assertEquals(count, total)
    }

    private suspend fun assertOffer(
        characterId: Int,
        completed: Int,
        releasedCoins: Int,
        status: String,
    ) {
        val offer = repository.listOffers(characterId).single()
        assertEquals(completed, offer.completedCount)
        assertEquals(releasedCoins, offer.releasedCoins)
        assertEquals(status, offer.status)
    }

    private class TestDatabase : Database, AutoCloseable {
        private val connection: Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

        init {
            connection.autoCommit = false
            createSchema()
            connection.commit()
        }

        override suspend fun <T> withTransaction(block: (DatabaseConnection) -> T): T {
            return try {
                val result = block(DatabaseConnection(connection))
                connection.commit()
                result
            } catch (t: Throwable) {
                connection.rollback()
                throw t
            }
        }

        override fun close() {
            connection.close()
        }

        private fun createSchema() {
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE grand_exchange_offers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        realm_id INTEGER NOT NULL,
                        account_id INTEGER NOT NULL,
                        character_id INTEGER NOT NULL,
                        slot INTEGER NOT NULL,
                        offer_type TEXT NOT NULL CHECK (offer_type IN ('buy', 'sell')),
                        obj INTEGER NOT NULL,
                        price_each INTEGER NOT NULL,
                        requested_count INTEGER NOT NULL,
                        completed_count INTEGER NOT NULL DEFAULT 0,
                        status TEXT NOT NULL DEFAULT 'open'
                            CHECK (status IN ('open', 'partial', 'completed', 'cancelled')),
                        deposited_coins INTEGER NOT NULL DEFAULT 0,
                        spent_coins INTEGER NOT NULL DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """
                        .trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE grand_exchange_collections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        offer_id INTEGER NOT NULL,
                        character_id INTEGER NOT NULL,
                        obj INTEGER NOT NULL,
                        count INTEGER NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """
                        .trimIndent()
                )
            }
        }
    }

    private companion object {
        const val Realm = 1
        const val Buyer = 101
        const val Seller = 202
        const val Item = 4151
        const val Coins = 995
    }
}
