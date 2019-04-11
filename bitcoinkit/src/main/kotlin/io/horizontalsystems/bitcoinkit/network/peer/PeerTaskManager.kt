package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class PeerTaskManager(private val peerManager: PeerManager) : Peer.TaskListener {
    var peerTaskHandler: IPeerTaskHandler? = null

    private val taskQueue: BlockingQueue<PeerTask> = ArrayBlockingQueue(10)

    override fun onReady(peer: Peer) {
        peerTaskHandler?.onPeerReady(peer)

        if (peer.ready) {
//            todo check if peer is not syncPeer
            taskQueue.poll()?.let {
                peer.addTask(it)
            }
        }
    }

    override fun onTaskComplete(peer: Peer, task: PeerTask) {
        peerTaskHandler?.handleCompletedTask(peer, task)
    }

    fun addTask(peerTask: PeerTask) {
        // todo find better solution
        val peer = peerManager.someReadyPeers().firstOrNull()

        if (peer == null) {
            taskQueue.add(peerTask)
        } else {
            peer.addTask(peerTask)
        }

    }

}