package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.managers.ConnectionManager
import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.models.NetworkAddress
import io.horizontalsystems.bitcoinkit.network.Network
import java.net.InetAddress
import java.util.logging.Logger

class PeerGroup(
        private val hostManager: PeerAddressManager,
        private val network: Network,
        private val peerManager: PeerManager,
        private val peerSize: Int,
        private val peerTaskManager: PeerTaskManager) : Thread(), Peer.Listener {

    interface IPeerGroupListener {
        fun onStart() = Unit
        fun onStop() = Unit
        fun onPeerCreate(peer: Peer) = Unit
        fun onPeerConnect(peer: Peer) = Unit
        fun onPeerDisconnect(peer: Peer, e: Exception?) = Unit
    }

    var connectionManager: ConnectionManager? = null

    var inventoryItemsHandler: IInventoryItemsHandler? = null

    @Volatile
    private var running = false
    private val logger = Logger.getLogger("PeerGroup")
    private val peerGroupListeners = mutableListOf<IPeerGroupListener>()

    fun addPeerGroupListener(listener: IPeerGroupListener) {
        peerGroupListeners.add(listener)
    }

    fun close() {
        running = false

        interrupt()
        try {
            join(5000)
        } catch (e: InterruptedException) {
        }
    }

    //
    // Thread implementations
    //
    override fun run() {
        running = true

        peerGroupListeners.forEach { it.onStart() }

        while (running) {
            if (connectionManager?.isOnline == true && peerManager.peersCount() < peerSize) {
                startConnection()
            }

            try {
                Thread.sleep(2000L)
            } catch (e: InterruptedException) {
                break
            }
        }

        peerGroupListeners.forEach { it.onStop() }
        logger.info("Closing all peer connections...")

        peerManager.disconnectAll()
    }

    //
    // PeerListener implementations
    //
    override fun onConnect(peer: Peer) {
        peerManager.add(peer)

        peerGroupListeners.forEach { it.onPeerConnect(peer) }
    }

    override fun onDisconnect(peer: Peer, e: Exception?) {
        peerManager.remove(peer)

        if (e == null) {
            logger.info("Peer ${peer.host} disconnected.")
            hostManager.markSuccess(peer.host)
        } else {
            logger.warning("Peer ${peer.host} disconnected with error ${e.message}.")
            hostManager.markFailed(peer.host)
        }

        peerGroupListeners.forEach { it.onPeerDisconnect(peer, e) }

    }

    override fun onReceiveInventoryItems(peer: Peer, inventoryItems: List<InventoryItem>) {
        inventoryItemsHandler?.handleInventoryItems(peer, inventoryItems)
    }

    override fun onReceiveAddress(addrs: Array<NetworkAddress>) {
        val peerIps = mutableListOf<String>()
        for (address in addrs) {
            val addr = InetAddress.getByAddress(address.address)
            peerIps.add(addr.hostAddress)
        }

        hostManager.addIps(peerIps.toTypedArray())
    }

    //
    // Private methods
    //
    private fun startConnection() {
        logger.info("Try open new peer connection...")
        val ip = hostManager.getIp()
        if (ip != null) {
            logger.info("Try open new peer connection to $ip...")
            val peer = Peer(ip, network, this, peerTaskManager)
            peerGroupListeners.forEach { it.onPeerCreate(peer) }
            peer.start()
        } else {
            logger.info("No peers found yet.")
        }
    }

    //
    // PeerGroup Exceptions
    //
    class Error(message: String) : Exception(message)
}
