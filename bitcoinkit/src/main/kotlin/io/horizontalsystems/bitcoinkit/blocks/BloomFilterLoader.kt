package io.horizontalsystems.bitcoinkit.blocks

import io.horizontalsystems.bitcoinkit.crypto.BloomFilter
import io.horizontalsystems.bitcoinkit.managers.BloomFilterManager
import io.horizontalsystems.bitcoinkit.network.peer.Peer
import io.horizontalsystems.bitcoinkit.network.peer.PeerGroup.IPeerGroupListener
import io.horizontalsystems.bitcoinkit.network.peer.PeerManager

class BloomFilterLoader(private val bloomFilterManager: BloomFilterManager, val peerManager: PeerManager) : IPeerGroupListener, BloomFilterManager.Listener {

    override fun onPeerConnect(peer: Peer) {
        bloomFilterManager.bloomFilter?.let {
            peer.filterLoad(it)
        }
    }

    override fun onFilterUpdated(bloomFilter: BloomFilter) {
        peerManager.connected().forEach {
            it.filterLoad(bloomFilter)
        }
    }

}
