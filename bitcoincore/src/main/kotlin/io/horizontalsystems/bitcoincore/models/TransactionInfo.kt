package io.horizontalsystems.bitcoincore.models

open class TransactionInfo(
        val transactionHash: String,
        val transactionIndex: Int,
        val from: List<TransactionAddress>,
        val to: List<TransactionAddress>,
        val amount: Long,
        val blockHeight: Int?,
        val timestamp: Long
)

data class TransactionAddress(
        val address: String,
        val mine: Boolean
)

data class BlockInfo(
        val headerHash: String,
        val height: Int,
        val timestamp: Long?
)
