package io.horizontalsystems.bitcoincore.models

import android.arch.persistence.room.Entity
import android.arch.persistence.room.ForeignKey
import java.util.*

/**
 * Transaction
 *
 *  Size        Field           Description
 *  ====        =====           ===========
 *  4 bytes     Version         Transaction version
 *  VarInt      InputsCount     Number of inputs
 *  Variable    Inputs          Inputs
 *  VarInt      OutputsCount    Number of outputs
 *  Variable    Outputs         Outputs
 *  4 bytes     LockTime        Transaction lock time
 */

@Entity(primaryKeys = ["hash"],
        foreignKeys = [ForeignKey(
                entity = Block::class,
                parentColumns = ["headerHash"],
                childColumns = ["blockHash"],
                onUpdate = ForeignKey.CASCADE,
                onDelete = ForeignKey.CASCADE,
                deferred = true)
        ])

class Transaction {

    var hash: ByteArray = byteArrayOf()
    var blockHash: ByteArray? = null

    var version: Int = 0
    var lockTime: Long = 0
    var timestamp: Long = 0
    var order: Int = 0 // topological order
    var isMine = false
    var isOutgoing = false
    var segwit = false
    var status: Int = Status.RELAYED

    constructor()
    constructor(version: Int = 0, lockTime: Long = 0) : this() {
        this.version = version
        this.lockTime = lockTime
        this.timestamp = Date().time / 1000
    }

    object Status {
        const val NEW = 1
        const val RELAYED = 2
        const val INVALID = 3
    }
}
