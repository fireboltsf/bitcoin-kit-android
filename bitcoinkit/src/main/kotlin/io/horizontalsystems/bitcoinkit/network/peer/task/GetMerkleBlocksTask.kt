package io.horizontalsystems.bitcoinkit.network.peer.task

import io.horizontalsystems.bitcoinkit.blocks.InvalidMerkleBlockException
import io.horizontalsystems.bitcoinkit.blocks.MerkleBlockExtractor
import io.horizontalsystems.bitcoinkit.core.toHexString
import io.horizontalsystems.bitcoinkit.models.BlockHash
import io.horizontalsystems.bitcoinkit.models.InventoryItem
import io.horizontalsystems.bitcoinkit.models.MerkleBlock
import io.horizontalsystems.bitcoinkit.network.messages.MerkleBlockMessage
import io.horizontalsystems.bitcoinkit.network.messages.Message
import io.horizontalsystems.bitcoinkit.network.messages.TransactionMessage
import io.horizontalsystems.bitcoinkit.storage.FullTransaction
import java.util.concurrent.TimeUnit

class GetMerkleBlocksTask(hashes: List<BlockHash>, private val merkleBlockHandler: MerkleBlockHandler, private val merkleBlockExtractor: MerkleBlockExtractor) : PeerTask() {

    interface MerkleBlockHandler {
        fun handleMerkleBlock(merkleBlock: MerkleBlock)
    }

    private var blockHashes = hashes.toMutableList()
    private var pendingMerkleBlocks = mutableListOf<MerkleBlock>()

    init {
        allowedIdleTime = TimeUnit.SECONDS.toMillis(5)
    }

    override fun start() {
        val items = blockHashes.map { hash ->
            InventoryItem(InventoryItem.MSG_FILTERED_BLOCK, hash.headerHash)
        }

        requester?.getData(items)
        resetTimer()
    }

    override fun handleMessage(message: Message): Boolean {
        return when (message) {
            is TransactionMessage -> handleTransaction(message.transaction)
            is MerkleBlockMessage -> handleMerkleBlock(message)
            else -> false
        }
    }

    private fun handleMerkleBlock(message: MerkleBlockMessage): Boolean {
        try {
            val merkleBlock = merkleBlockExtractor.extract(message)

            val blockHash = blockHashes.find { merkleBlock.blockHash.contentEquals(it.headerHash) }
                    ?: return false

            resetTimer()

            merkleBlock.height = if (blockHash.height > 0) blockHash.height else null

            if (merkleBlock.complete) {
                handleCompletedMerkleBlock(merkleBlock)
            } else {
                pendingMerkleBlocks.add(merkleBlock)
            }

            return true

        } catch (e: InvalidMerkleBlockException) {
            listener?.onTaskFailed(this, e)
            return true
        }
    }

    private fun handleTransaction(transaction: FullTransaction): Boolean {
        val block = pendingMerkleBlocks.find { it.associatedTransactionHexes.contains(transaction.header.hash.toHexString()) }
                ?: return false

        resetTimer()

        block.associatedTransactions.add(transaction)

        if (block.complete) {
            pendingMerkleBlocks.remove(block)
            handleCompletedMerkleBlock(block)
        }

        return true
    }

    override fun handleTimeout() {
        if (blockHashes.isEmpty()) {
            listener?.onTaskCompleted(this)
        } else {
            listener?.onTaskFailed(this, MerkleBlockNotReceived())
        }
    }

    private fun handleCompletedMerkleBlock(merkleBlock: MerkleBlock) {
        blockHashes.find { it.headerHash.contentEquals(merkleBlock.blockHash) }?.let {
            blockHashes.remove(it)
        }

        try {
            merkleBlockHandler.handleMerkleBlock(merkleBlock)
        } catch (e: Exception) {
            listener?.onTaskFailed(this, e)
        }

        if (blockHashes.isEmpty()) {
            listener?.onTaskCompleted(this)
        }
    }

    class MerkleBlockNotReceived : Exception("Merkle blocks are not received")

}
