package io.horizontalsystems.bitcoinkit

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.horizontalsystems.bitcoincore.AbstractKit
import io.horizontalsystems.bitcoincore.BitcoinCore
import io.horizontalsystems.bitcoincore.BitcoinCoreBuilder
import io.horizontalsystems.bitcoincore.blocks.validators.BitsValidator
import io.horizontalsystems.bitcoincore.blocks.validators.LegacyDifficultyAdjustmentValidator
import io.horizontalsystems.bitcoincore.blocks.validators.LegacyTestNetDifficultyValidator
import io.horizontalsystems.bitcoincore.managers.BCoinApi
import io.horizontalsystems.bitcoincore.managers.BitcoinAddressSelector
import io.horizontalsystems.bitcoincore.managers.BlockValidatorHelper
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.bitcoincore.network.Network
import io.horizontalsystems.bitcoincore.storage.CoreDatabase
import io.horizontalsystems.bitcoincore.storage.Storage
import io.horizontalsystems.bitcoincore.utils.PaymentAddressParser
import io.horizontalsystems.bitcoincore.utils.SegwitAddressConverter
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.reactivex.Single

class BitcoinKit : AbstractKit {
    enum class NetworkType {
        MainNet,
        TestNet,
        RegTest
    }

    interface Listener : BitcoinCore.Listener

    override var bitcoinCore: BitcoinCore
    override var network: Network

    var listener: Listener? = null
        set(value) {
            field = value
            bitcoinCore.listener = value
        }

    constructor(context: Context, words: List<String>, walletId: String, networkType: NetworkType = NetworkType.MainNet, peerSize: Int = 10, newWallet: Boolean = false, confirmationsThreshold: Int = 6) :
            this(context, Mnemonic().toSeed(words), walletId, networkType, peerSize, newWallet, confirmationsThreshold)

    constructor(context: Context, seed: ByteArray, walletId: String, networkType: NetworkType = NetworkType.MainNet, peerSize: Int = 10, newWallet: Boolean = false, confirmationsThreshold: Int = 6) {
        val database = CoreDatabase.getInstance(context, getDatabaseName(networkType, walletId))
        val storage = Storage(database)
        var initialSyncUrl = ""

        network = when (networkType) {
            NetworkType.MainNet -> {
                initialSyncUrl = "https://btc.horizontalsystems.xyz/apg"
                MainNet()
            }
            NetworkType.TestNet -> {
                initialSyncUrl = "http://btc-testnet.horizontalsystems.xyz/apg"
                TestNet()
            }
            NetworkType.RegTest -> RegTest()
        }

        val paymentAddressParser = PaymentAddressParser("bitcoin", removeScheme = true)
        val addressSelector = BitcoinAddressSelector()
        val initialSyncApi = BCoinApi(initialSyncUrl)

        bitcoinCore = BitcoinCoreBuilder()
                .setContext(context)
                .setSeed(seed)
                .setNetwork(network)
                .setPaymentAddressParser(paymentAddressParser)
                .setAddressSelector(addressSelector)
                .setPeerSize(peerSize)
                .setNewWallet(newWallet)
                .setConfirmationThreshold(confirmationsThreshold)
                .setStorage(storage)
                .setInitialSyncApi(initialSyncApi)
                .build()


        //  extending bitcoinCore

        val bech32 = SegwitAddressConverter(network.addressSegwitHrp)
        bitcoinCore.prependAddressConverter(bech32)

        val blockHelper = BlockValidatorHelper(storage)

        if (networkType == NetworkType.MainNet) {
            bitcoinCore.addBlockValidator(LegacyDifficultyAdjustmentValidator(blockHelper, BitcoinCore.heightInterval, BitcoinCore.targetTimespan, BitcoinCore.maxTargetBits))
            bitcoinCore.addBlockValidator(BitsValidator())
        } else if (networkType == NetworkType.TestNet) {
            bitcoinCore.addBlockValidator(LegacyDifficultyAdjustmentValidator(blockHelper, BitcoinCore.heightInterval, BitcoinCore.targetTimespan, BitcoinCore.maxTargetBits))
            bitcoinCore.addBlockValidator(LegacyTestNetDifficultyValidator(storage, BitcoinCore.heightInterval, BitcoinCore.targetSpacing, BitcoinCore.maxTargetBits))
            bitcoinCore.addBlockValidator(BitsValidator())
        }
    }

    fun transactions(fromHash: String? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return bitcoinCore.transactions(fromHash, limit)
    }

    companion object {

        private fun getDatabaseName(networkType: NetworkType, walletId: String): String = "Bitcoin-${networkType.name}-$walletId"

        fun clear(context: Context, networkType: NetworkType, walletId: String) {
            SQLiteDatabase.deleteDatabase(context.getDatabasePath(getDatabaseName(networkType, walletId)))
        }
    }

}
