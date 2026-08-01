package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.OwnedPackDao
import com.hasyame.marvelchampions.data.db.dao.PackDao
import com.hasyame.marvelchampions.data.db.entity.OwnedPackEntity
import com.hasyame.marvelchampions.data.db.entity.PackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A pack together with how many copies the user owns. */
data class PackOwnership(
    val pack: PackEntity,
    val quantity: Int,
) {
    val isOwned: Boolean get() = quantity > 0
}

/**
 * The user's collection. This is the single source of truth for what the
 * randomiser may draw and for deck legality checks.
 */
@Singleton
class CollectionRepository @Inject constructor(
    private val packDao: PackDao,
    private val ownedPackDao: OwnedPackDao,
) {

    fun observeCollection(): Flow<List<PackOwnership>> =
        combine(packDao.observePacks(), ownedPackDao.observeOwned()) { packs, owned ->
            val quantities = owned.associate { it.packCode to it.quantity }
            packs.map { PackOwnership(pack = it, quantity = quantities[it.code] ?: 0) }
        }

    fun observeOwnedCodes(): Flow<Set<String>> =
        ownedPackDao.observeOwned().map { owned -> owned.map { it.packCode }.toSet() }

    suspend fun getOwnedCodes(): Set<String> = ownedPackDao.getOwnedCodes().toSet()

    suspend fun setOwned(packCode: String, owned: Boolean) {
        if (owned) {
            ownedPackDao.upsert(OwnedPackEntity(packCode = packCode, quantity = 1))
        } else {
            ownedPackDao.remove(packCode)
        }
    }

    suspend fun setQuantity(packCode: String, quantity: Int) {
        if (quantity <= 0) {
            ownedPackDao.remove(packCode)
        } else {
            ownedPackDao.upsert(OwnedPackEntity(packCode = packCode, quantity = quantity))
        }
    }

    suspend fun setOwnedBulk(packCodes: Collection<String>, owned: Boolean) {
        if (owned) {
            ownedPackDao.upsertAll(packCodes.map { OwnedPackEntity(it, quantity = 1) })
        } else {
            packCodes.forEach { ownedPackDao.remove(it) }
        }
    }

    /** Replaces the whole collection, for the JSON import path. */
    suspend fun replaceCollection(ownedPacks: Map<String, Int>) {
        ownedPackDao.replaceAll(
            ownedPacks.filterValues { it > 0 }
                .map { (code, quantity) -> OwnedPackEntity(code, quantity) },
        )
    }

    suspend fun isEmpty(): Boolean = ownedPackDao.countOwned() == 0
}
