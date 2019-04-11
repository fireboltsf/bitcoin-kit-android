package io.horizontalsystems.bitcoinkit.transactions

import io.horizontalsystems.bitcoinkit.network.peer.PeerGroup
import io.horizontalsystems.bitcoinkit.network.peer.PeerManager
import io.horizontalsystems.bitcoinkit.network.peer.task.SendTransactionTask

class TransactionSender(private var peerManager: PeerManager) {
    var transactionSyncer: TransactionSyncer? = null

    fun sendPendingTransactions() {
        try {
            canSendTransaction()

            peerManager.someReadyPeers().forEach { peer ->
                transactionSyncer?.getPendingTransactions()?.forEach { pendingTransaction ->
                    peer.addTask(SendTransactionTask(pendingTransaction))
                }
            }


        } catch (e: PeerGroup.Error) {
//            logger.warning("Handling pending transactions failed with: ${e.message}")
        }

    }

    @Throws
    fun canSendTransaction() {
        if (peerManager.peersCount() < 1) {
            throw PeerGroup.Error("No peers connected")
        }

        if (!peerManager.isHalfSynced()) {
            throw PeerGroup.Error("Peers not synced yet")
        }

    }

}