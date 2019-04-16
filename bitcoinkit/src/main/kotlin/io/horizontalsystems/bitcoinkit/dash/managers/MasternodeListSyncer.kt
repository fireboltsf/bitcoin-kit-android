package io.horizontalsystems.bitcoinkit.dash.managers

import io.horizontalsystems.bitcoinkit.dash.tasks.PeerTaskFactory
import io.horizontalsystems.bitcoinkit.dash.tasks.RequestMasternodeListDiffTask
import io.horizontalsystems.bitcoinkit.network.peer.IPeerTaskHandler
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.PeerTaskManager
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask

class MasternodeListSyncer(private val peerTaskManager: PeerTaskManager, val peerTaskFactory: PeerTaskFactory, private val masternodeListManager: MasternodeListManager) : IPeerTaskHandler {

    fun sync(blockHash: ByteArray) {
        addTask(masternodeListManager.baseBlockHash, blockHash)
    }

    override fun handleCompletedTask(peer: Peer, task: PeerTask): Boolean {
        return when (task) {
            is RequestMasternodeListDiffTask -> {
                task.masternodeListDiffMessage?.let { masternodeListDiffMessage ->
                    try {
                        masternodeListManager.updateList(masternodeListDiffMessage)
                    } catch (e: MasternodeListManager.ValidationError) {
                        peer.close(e)

                        addTask(masternodeListDiffMessage.baseBlockHash, masternodeListDiffMessage.blockHash)
                    }
                }
                true
            }
            else -> false
        }
    }

    private fun addTask(baseBlockHash: ByteArray, blockHash: ByteArray) {
        val task = peerTaskFactory.createRequestMasternodeListDiffTask(baseBlockHash, blockHash)
        peerTaskManager.addTask(task)
    }
}
