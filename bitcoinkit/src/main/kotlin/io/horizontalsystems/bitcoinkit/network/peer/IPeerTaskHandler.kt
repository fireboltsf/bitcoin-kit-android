package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask

interface IPeerTaskHandler {
    fun handleCompletedTask(peer: Peer, task: PeerTask): Boolean
    fun onPeerReady(peer: Peer) = Unit
}

class PeerTaskHandlerChain : IPeerTaskHandler {

    private val concreteHandlers = mutableListOf<IPeerTaskHandler>()

    override fun handleCompletedTask(peer: Peer, task: PeerTask): Boolean {
        return concreteHandlers.any {
            it.handleCompletedTask(peer, task)
        }
    }

    override fun onPeerReady(peer: Peer) {
        concreteHandlers.forEach {
            it.onPeerReady(peer)
        }
    }

    fun addHandler(h: IPeerTaskHandler) {
        concreteHandlers.add(h)
    }

}
