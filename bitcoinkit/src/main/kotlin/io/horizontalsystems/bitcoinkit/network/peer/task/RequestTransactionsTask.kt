package io.horizontalsystems.bitcoinkit.network.peer.task

import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.network.messages.Message
import io.horizontalsystems.bitcoinkit.network.messages.TransactionMessage
import io.horizontalsystems.bitcoinkit.storage.FullTransaction

class RequestTransactionsTask(hashes: List<ByteArray>) : PeerTask() {
    val hashes = hashes.toMutableList()
    var transactions = mutableListOf<FullTransaction>()

    override fun start() {
        requester?.getData(hashes.map { hash ->
            InventoryItem(InventoryItem.MSG_TX, hash)
        })
    }

    override fun handleMessage(message: Message): Boolean {
        if (message !is TransactionMessage) return false

        return handleTransaction(message.transaction)
    }

    private fun handleTransaction(transaction: FullTransaction): Boolean {
        val hash = hashes.firstOrNull { it.contentEquals(transaction.header.hash) } ?: return false

        hashes.remove(hash)
        transactions.add(transaction)

        if (hashes.isEmpty()) {
            listener?.onTaskCompleted(this)
        }

        return true
    }

}
