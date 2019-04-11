package io.horizontalsystems.bitcoinkit.network.peer

import io.horizontalsystems.bitcoinkit.crypto.BloomFilter
import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.models.NetworkAddress
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.messages.*
import io.horizontalsystems.bitcoinkit.network.peer.task.PeerTask
import io.horizontalsystems.bitcoinkit.storage.FullTransaction
import java.net.InetAddress

class Peer(val host: String, private val network: Network, private val listener: Listener, private val taskListener: TaskListener) : PeerConnection.Listener, PeerTask.Listener, PeerTask.Requester {

    interface Listener {
        fun onConnect(peer: Peer)
        fun onDisconnect(peer: Peer, e: Exception?)
        fun onReceiveInventoryItems(peer: Peer, inventoryItems: List<InventoryItem>)
        fun onReceiveAddress(addrs: Array<NetworkAddress>)
    }

    interface TaskListener {
        fun onReady(peer: Peer)
        fun onTaskComplete(peer: Peer, task: PeerTask)
    }

    // TODO seems like property connected is not needed. It is always true in PeerManager. Need to check it and remove
    var connected = false
    var synced = false
    var blockHashesSynced = false
    var announcedLastBlockHeight: Int = 0
    var localBestBlockHeight: Int = 0

    private val peerConnection = PeerConnection(host, network, this)
    private var tasks = mutableListOf<PeerTask>()
    private val timer = PeerTimer()

    val ready: Boolean
        get() = connected && tasks.isEmpty()

    fun start() {
        peerConnection.start()
    }

    fun close(disconnectError: Exception? = null) {
        peerConnection.close(disconnectError)
    }

    fun addTask(task: PeerTask) {
        tasks.add(task)

        task.listener = this
        task.requester = this

        task.start()
    }

    fun filterLoad(bloomFilter: BloomFilter) {
        peerConnection.sendMessage(FilterLoadMessage(bloomFilter))
    }

    fun sendMempoolMessage() {
        peerConnection.sendMessage(MempoolMessage())
    }

    //
    // PeerConnection Listener implementations
    //
    override fun onTimePeriodPassed() {
        try {
            timer.check()

            tasks.firstOrNull()?.checkTimeout()
        } catch (e: PeerTimer.Error.Idle) {
            ping((Math.random() * Long.MAX_VALUE).toLong())
            timer.pingSent()
        } catch (e: PeerTimer.Error.Timeout) {
            peerConnection.close(e)
        }
    }

    override fun onMessage(message: Message) {
        timer.restart()

        if (message is VersionMessage)
            return handleVersionMessage(message)
        if (message is VerAckMessage)
            return handleVerackMessage()

        if (!connected) return

        when (message) {
            is PingMessage -> peerConnection.sendMessage(PongMessage(message.nonce))
            is AddrMessage -> handleAddrMessage(message)
            is InvMessage -> handleInvMessage(message)
            is GetDataMessage -> {
                for (inv in message.inventory) {
                    if (tasks.any { it.handleGetDataInventoryItem(inv) }) {
                        continue
                    }
                }
            }
            else -> tasks.any { it.handleMessage(message) }
        }
    }

    override fun socketConnected(address: InetAddress) {
        peerConnection.sendMessage(VersionMessage(localBestBlockHeight, address, network))
    }

    override fun disconnected(e: Exception?) {
        connected = false
        listener.onDisconnect(this, e)
    }

    private fun handleVersionMessage(message: VersionMessage) = try {
        validatePeerVersion(message)

        announcedLastBlockHeight = message.lastBlock

        peerConnection.sendMessage(VerAckMessage())
    } catch (e: Error.UnsuitablePeerVersion) {
        close(e)
    }

    private fun handleVerackMessage() {
        connected = true

        listener.onConnect(this)
    }

    private fun handleAddrMessage(message: AddrMessage) {
        listener.onReceiveAddress(message.addresses)
    }

    private fun validatePeerVersion(message: VersionMessage) {
        when {
            message.lastBlock <= 0 -> throw Error.UnsuitablePeerVersion("Peer last block is not greater than 0.")
            message.lastBlock < localBestBlockHeight -> throw Error.UnsuitablePeerVersion("Peer has expired blockchain ${message.lastBlock} vs ${localBestBlockHeight}(local)")
            !message.hasBlockChain(network) -> throw Error.UnsuitablePeerVersion("Peer does not have a copy of the block chain.")
            !message.supportsBloomFilter(network) -> throw Error.UnsuitablePeerVersion("Peer does not support Bloom Filter.")
        }
    }

    //
    // PeerTask Listener implementations
    //
    override fun onTaskCompleted(task: PeerTask) {
        tasks.find { it == task }?.let { completedTask ->
            tasks.remove(completedTask)
            taskListener.onTaskComplete(this, task)
        }

        // Reset timer for the next task in list
        tasks.firstOrNull()?.resetTimer()

        if (tasks.isEmpty()) {
            taskListener.onReady(this)
        }
    }

    override fun onTaskFailed(task: PeerTask, e: Exception) {
        peerConnection.close(e)
    }

    //
    // PeerTask Requester implementations
    //
    override fun getBlocks(hashes: List<ByteArray>) {
        peerConnection.sendMessage(GetBlocksMessage(hashes, network))
    }

    override fun getData(items: List<InventoryItem>) {
        peerConnection.sendMessage(GetDataMessage(items))
    }

    override fun ping(nonce: Long) {
        peerConnection.sendMessage(PingMessage(nonce))
    }

    override fun sendTransactionInventory(hash: ByteArray) {
        peerConnection.sendMessage(InvMessage(InventoryItem.MSG_TX, hash))
    }

    override fun send(transaction: FullTransaction) {
        peerConnection.sendMessage(TransactionMessage(transaction))
    }

    override fun sendMessage(message: Message) {
        peerConnection.sendMessage(message)
    }

    //
    // Private methods
    //
    private fun handleInvMessage(message: InvMessage) {
        if (tasks.none { it.handleInventoryItems(message.inventory) }) {
            listener.onReceiveInventoryItems(this, message.inventory)
        }
    }

    open class Error(message: String) : Exception(message) {
        class UnsuitablePeerVersion(message: String) : Error(message)
    }

}
