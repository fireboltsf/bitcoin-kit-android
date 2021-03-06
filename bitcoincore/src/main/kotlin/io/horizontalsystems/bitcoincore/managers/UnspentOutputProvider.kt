package io.horizontalsystems.bitcoincore.managers

import io.horizontalsystems.bitcoincore.core.IStorage
import io.horizontalsystems.bitcoincore.storage.UnspentOutput

class UnspentOutputProvider(private val storage: IStorage, private val confirmationsThreshold: Int = 6) : IUnspentOutputProvider {
    override fun getUnspentOutputs(): List<UnspentOutput> {
        val lastBlockHeight = storage.lastBlock()?.height ?: 0

        return storage.getUnspentOutputs().filter {
            if (it.transaction.isOutgoing) {
                return@filter true
            }

            val block = it.block ?: return@filter false
            if (block.height <= lastBlockHeight - confirmationsThreshold + 1) {
                return@filter true
            }

            false
        }
    }

    fun getBalance() = getUnspentOutputs().map { it.output.value }.sum()
}
