package io.horizontalsystems.dashkit.storage

import io.horizontalsystems.bitcoincore.models.Block
import io.horizontalsystems.bitcoincore.models.TransactionInput
import io.horizontalsystems.bitcoincore.storage.FullTransactionInfo
import io.horizontalsystems.bitcoincore.storage.Storage
import io.horizontalsystems.dashkit.IDashStorage
import io.horizontalsystems.dashkit.models.InstantTransactionHash
import io.horizontalsystems.dashkit.models.InstantTransactionInput
import io.horizontalsystems.dashkit.models.Masternode
import io.horizontalsystems.dashkit.models.MasternodeListState

class DashStorage(private val dashStore: DashKitDatabase, private val coreStorage: Storage) : IDashStorage {

    override fun getBlock(blockHash: ByteArray): Block? {
        return coreStorage.getBlock(blockHash)
    }

    override fun instantTransactionHashes(): List<ByteArray> {
        return dashStore.instantTransactionHashDao.getAll().map { it.txHash }
    }

    override fun instantTransactionInputs(txHash: ByteArray): List<InstantTransactionInput> {
        return dashStore.instantTransactionInputDao.getByTx(txHash)
    }

    override fun getTransactionInputs(txHash: ByteArray): List<TransactionInput> {
        return coreStorage.getTransactionInputs(txHash)
    }

    override fun addInstantTransactionInput(instantTransactionInput: InstantTransactionInput) {
        dashStore.instantTransactionInputDao.insert(instantTransactionInput)
    }

    override fun addInstantTransactionHash(txHash: ByteArray) {
        dashStore.instantTransactionHashDao.insert(InstantTransactionHash(txHash))
    }

    override fun removeInstantTransactionInputs(txHash: ByteArray) {
        dashStore.instantTransactionInputDao.deleteByTx(txHash)
    }

    fun getFullTransactionInfo(txHash: ByteArray): FullTransactionInfo? {
        return coreStorage.getFullTransactionInfo(txHash)
    }

    override var masternodes: List<Masternode>
        get() = dashStore.masternodeDao.getAll()
        set(value) {
            dashStore.masternodeDao.clearAll()
            dashStore.masternodeDao.insertAll(value)
        }

    override var masternodeListState: MasternodeListState?
        get() = dashStore.masternodeListStateDao.getState()
        set(value) {
            value?.let {
                dashStore.masternodeListStateDao.setState(value)
            }
        }
}
