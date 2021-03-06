package io.horizontalsystems.bitcoincore.storage

import android.arch.persistence.room.Database
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import io.horizontalsystems.bitcoincore.models.*

@Database(version = 1, exportSchema = false, entities = [
    BlockchainState::class,
    PeerAddress::class,
    BlockHash::class,
    Block::class,
    SentTransaction::class,
    Transaction::class,
    TransactionInput::class,
    TransactionOutput::class,
    PublicKey::class
])

abstract class CoreDatabase : RoomDatabase() {

    abstract val blockchainState: BlockchainStateDao
    abstract val peerAddress: PeerAddressDao
    abstract val blockHash: BlockHashDao
    abstract val block: BlockDao
    abstract val sentTransaction: SentTransactionDao
    abstract val transaction: TransactionDao
    abstract val input: TransactionInputDao
    abstract val output: TransactionOutputDao
    abstract val publicKey: PublicKeyDao

    companion object {

        fun getInstance(context: Context, dbName: String): CoreDatabase {
            return Room.databaseBuilder(context, CoreDatabase::class.java, dbName)
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .build()
        }
    }
}
