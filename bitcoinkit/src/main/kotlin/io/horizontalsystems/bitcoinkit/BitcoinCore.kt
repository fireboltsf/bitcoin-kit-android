package io.horizontalsystems.bitcoinkit

import android.content.Context
import io.horizontalsystems.bitcoinkit.blocks.*
import io.horizontalsystems.bitcoinkit.blocks.validators.BlockValidatorChain
import io.horizontalsystems.bitcoinkit.blocks.validators.IBlockValidator
import io.horizontalsystems.bitcoinkit.blocks.validators.ProofOfWorkValidator
import io.horizontalsystems.bitcoinkit.core.DataProvider
import io.horizontalsystems.bitcoinkit.core.IStorage
import io.horizontalsystems.bitcoinkit.core.KitStateProvider
import io.horizontalsystems.bitcoinkit.core.Wallet
import io.horizontalsystems.bitcoinkit.managers.*
import io.horizontalsystems.bitcoinkit.models.BitcoinPaymentData
import io.horizontalsystems.bitcoinkit.models.BlockInfo
import io.horizontalsystems.bitcoinkit.models.FeePriority
import io.horizontalsystems.bitcoinkit.models.TransactionInfo
import io.horizontalsystems.bitcoinkit.network.Network
import io.horizontalsystems.bitcoinkit.network.messages.BitcoinMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.IMessageParser
import io.horizontalsystems.bitcoinkit.network.messages.Message
import io.horizontalsystems.bitcoinkit.network.messages.MessageParserChain
import io.horizontalsystems.bitcoinkit.network.peer.*
import io.horizontalsystems.bitcoinkit.serializers.BlockHeaderSerializer
import io.horizontalsystems.bitcoinkit.transactions.*
import io.horizontalsystems.bitcoinkit.transactions.builder.TransactionBuilder
import io.horizontalsystems.bitcoinkit.transactions.scripts.ScriptType
import io.horizontalsystems.bitcoinkit.utils.AddressConverterChain
import io.horizontalsystems.bitcoinkit.utils.Base58AddressConverter
import io.horizontalsystems.bitcoinkit.utils.IAddressConverter
import io.horizontalsystems.bitcoinkit.utils.PaymentAddressParser
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.reactivex.Single
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BitcoinCoreBuilder {

    // required parameters
    private var context: Context? = null
    private var seed: ByteArray? = null
    private var words: List<String>? = null
    private var network: Network? = null
    private var paymentAddressParser: PaymentAddressParser? = null
    private var addressSelector: IAddressSelector? = null
    private var apiFeeRateCoinCode: String? = null
    private var storage: IStorage? = null

    // parameters with default values
    private var confirmationsThreshold = 6
    private var newWallet = false
    private var peerSize = 10

    fun setContext(context: Context): BitcoinCoreBuilder {
        this.context = context
        return this
    }

    fun setSeed(seed: ByteArray): BitcoinCoreBuilder {
        this.seed = seed
        return this
    }

    fun setWords(words: List<String>): BitcoinCoreBuilder {
        this.words = words
        return this
    }

    fun setNetwork(network: Network): BitcoinCoreBuilder {
        this.network = network
        return this
    }

    fun setPaymentAddressParser(paymentAddressParser: PaymentAddressParser): BitcoinCoreBuilder {
        this.paymentAddressParser = paymentAddressParser
        return this
    }

    fun setAddressSelector(addressSelector: IAddressSelector): BitcoinCoreBuilder {
        this.addressSelector = addressSelector
        return this
    }

    fun setApiFeeRateCoinCode(apiFeeRateCoinCode: String): BitcoinCoreBuilder {
        this.apiFeeRateCoinCode = apiFeeRateCoinCode
        return this
    }

    fun setConfirmationThreshold(confirmationsThreshold: Int): BitcoinCoreBuilder {
        this.confirmationsThreshold = confirmationsThreshold
        return this
    }

    fun setNewWallet(newWallet: Boolean): BitcoinCoreBuilder {
        this.newWallet = newWallet
        return this
    }

    fun setPeerSize(peerSize: Int): BitcoinCoreBuilder {
        this.peerSize = peerSize
        return this
    }

    fun setStorage(storage: IStorage): BitcoinCoreBuilder {
        this.storage = storage
        return this
    }

    fun build(): BitcoinCore {
        val context = checkNotNull(this.context)
        val seed = checkNotNull(this.seed ?: words?.let { Mnemonic().toSeed(it) })
        val network = checkNotNull(this.network)
        val paymentAddressParser = checkNotNull(this.paymentAddressParser)
        val addressSelector = checkNotNull(this.addressSelector)
        val apiFeeRateCoinCode = checkNotNull(this.apiFeeRateCoinCode)
        val storage = checkNotNull(this.storage)

        BlockHeaderSerializer.network = network

        val apiFeeRate = ApiFeeRate(apiFeeRateCoinCode)

        val addressConverter = AddressConverterChain()

        val unspentOutputProvider = UnspentOutputProvider(storage, confirmationsThreshold)

        val dataProvider = DataProvider(storage, unspentOutputProvider)

        val connectionManager = ConnectionManager(context)

        val hdWallet = HDWallet(seed, network.coinType)

        val addressManager = AddressManager.create(storage, hdWallet, addressConverter)

        val transactionLinker = TransactionLinker(storage)
        val transactionExtractor = TransactionExtractor(addressConverter, storage)
        val transactionProcessor = TransactionProcessor(storage, transactionExtractor, transactionLinker, addressManager, dataProvider)

        val kitStateProvider = KitStateProvider()

        val peerHostManager = PeerAddressManager(network, storage)
        val bloomFilterManager = BloomFilterManager(storage)

        val peerManager = PeerManager()

        val peerTaskManager = PeerTaskManager(peerManager)
        val peerGroup = PeerGroup(peerHostManager, network, peerManager, peerSize, peerTaskManager)
        peerGroup.connectionManager = connectionManager

        val transactionSyncer = TransactionSyncer(storage, transactionProcessor, addressManager, bloomFilterManager)

        val transactionSender = TransactionSender(peerManager)
        transactionSender.transactionSyncer = transactionSyncer

        val transactionBuilder = TransactionBuilder(addressConverter, hdWallet, network, addressManager, unspentOutputProvider)
        val transactionCreator = TransactionCreator(transactionBuilder, transactionProcessor, transactionSender)

        val feeRateSyncer = FeeRateSyncer(storage, apiFeeRate)
        val blockHashFetcher = BlockHashFetcher(addressSelector, addressConverter, BCoinApi(network, HttpRequester()), BlockHashFetcherHelper())
        val blockDiscovery = BlockDiscoveryBatch(Wallet(hdWallet), blockHashFetcher, network.checkpointBlock.height)
        val stateManager = StateManager(storage, network, newWallet)
        val initialSyncer = InitialSyncer(storage, blockDiscovery, stateManager, addressManager, kitStateProvider)

        val syncManager = SyncManager(connectionManager, feeRateSyncer, peerGroup, initialSyncer)
        initialSyncer.listener = syncManager

        val bitcoinCore = BitcoinCore(
                storage,
                dataProvider,
                addressManager,
                addressConverter,
                kitStateProvider,
                transactionBuilder,
                transactionCreator,
                paymentAddressParser,
                syncManager)

        dataProvider.listener = bitcoinCore
        kitStateProvider.listener = bitcoinCore

        bitcoinCore.peerGroup = peerGroup
        bitcoinCore.peerTaskManager = peerTaskManager
        bitcoinCore.transactionSyncer = transactionSyncer

        peerTaskManager.peerTaskHandler = bitcoinCore.peerTaskHandlerChain
        peerGroup.inventoryItemsHandler = bitcoinCore.inventoryItemsHandlerChain
        Message.Builder.messageParser = bitcoinCore.messageParserChain

        bitcoinCore.prependAddressConverter(Base58AddressConverter(network.addressVersion, network.addressScriptVersion))

        // this part can be moved to another place

        bitcoinCore.addMessageParser(BitcoinMessageParser())

        val bloomFilterLoader = BloomFilterLoader(bloomFilterManager)
        bloomFilterManager.listener = bloomFilterLoader
        bitcoinCore.addPeerGroupListener(bloomFilterLoader)

        val initialBlockDownload = InitialBlockDownload(BlockSyncer(storage, Blockchain(storage, bitcoinCore.blockValidatorChain, dataProvider), transactionProcessor, addressManager, bloomFilterManager, kitStateProvider, network), peerManager, kitStateProvider, MerkleBlockExtractor(network.maxBlockSize))
        bitcoinCore.addPeerTaskHandler(initialBlockDownload)
        bitcoinCore.addInventoryItemsHandler(initialBlockDownload)
        bitcoinCore.addPeerGroupListener(initialBlockDownload)
        initialBlockDownload.peersSyncedListener = SendTransactionsOnPeersSynced(transactionSender)

        val mempoolTransactions = MempoolTransactions(transactionSyncer)
        bitcoinCore.addPeerTaskHandler(mempoolTransactions)
        bitcoinCore.addInventoryItemsHandler(mempoolTransactions)
        bitcoinCore.addPeerGroupListener(mempoolTransactions)

        return bitcoinCore
    }

}

class BitcoinCore(private val storage: IStorage, private val dataProvider: DataProvider, private val addressManager: AddressManager, private val addressConverter: AddressConverterChain, private val kitStateProvider: KitStateProvider, private val transactionBuilder: TransactionBuilder, private val transactionCreator: TransactionCreator, private val paymentAddressParser: PaymentAddressParser, private val syncManager: SyncManager)
    : KitStateProvider.Listener, DataProvider.Listener {

    interface Listener {
        fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) = Unit
        fun onTransactionsDelete(hashes: List<String>) = Unit
        fun onBalanceUpdate(balance: Long) = Unit
        fun onLastBlockInfoUpdate(blockInfo: BlockInfo) = Unit
        fun onKitStateUpdate(state: KitState) = Unit
    }

    // START: Extending
    lateinit var peerGroup: PeerGroup
    lateinit var peerTaskManager: PeerTaskManager
    lateinit var transactionSyncer: TransactionSyncer

    val inventoryItemsHandlerChain = InventoryItemsHandlerChain()
    val peerTaskHandlerChain = PeerTaskHandlerChain()
    val messageParserChain = MessageParserChain()
    val blockValidatorChain = BlockValidatorChain(ProofOfWorkValidator())

    fun addMessageParser(messageParser: IMessageParser) {
        messageParserChain.addParser(messageParser)
    }

    fun addInventoryItemsHandler(handler: IInventoryItemsHandler) {
        inventoryItemsHandlerChain.addHandler(handler)
    }

    fun addPeerTaskHandler(handler: IPeerTaskHandler) {
        peerTaskHandlerChain.addHandler(handler)
    }

    fun addPeerGroupListener(listener: PeerGroup.IPeerGroupListener) {
        peerGroup.addPeerGroupListener(listener)
    }

    fun prependAddressConverter(converter: IAddressConverter) {
        addressConverter.prependConverter(converter)
    }

    fun addBlockValidator(validator: IBlockValidator) {
        blockValidatorChain.add(validator)
    }

    // END: Extending

    var listenerExecutor: Executor = Executors.newSingleThreadExecutor()

    //  DataProvider getters
    val balance get() = dataProvider.balance
    val lastBlockInfo get() = dataProvider.lastBlockInfo
    val syncState get() = kitStateProvider.syncState

    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    //
    // API methods
    //
    fun start() {
        syncManager.start()
    }

    fun stop() {
        dataProvider.clear()
        syncManager.stop()
        storage.clear()
    }

    fun clear() {
        stop()
    }

    fun refresh() {
        start()
    }

    fun transactions(fromHash: String? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return dataProvider.transactions(fromHash, limit)
    }

    fun fee(value: Long, address: String? = null, senderPay: Boolean = true, feePriority: FeePriority = FeePriority.Medium): Long {
        return transactionBuilder.fee(value, getFeeRate(feePriority), senderPay, address)
    }

    fun send(address: String, value: Long, senderPay: Boolean = true, feePriority: FeePriority = FeePriority.Medium) {
        transactionCreator.create(address, value, getFeeRate(feePriority), senderPay)
    }

    fun receiveAddress(): String {
        return addressManager.receiveAddress()
    }

    fun validateAddress(address: String) {
        addressConverter.convert(address)
    }

    fun parsePaymentAddress(paymentAddress: String): BitcoinPaymentData {
        return paymentAddressParser.parse(paymentAddress)
    }

    fun showDebugInfo() {
        addressManager.fillGap()
        storage.getPublicKeys().forEach { pubKey ->
            try {
//                    val scriptType = if (network is MainNetBitcoinCash || network is TestNetBitcoinCash)
//                        ScriptType.P2PKH else
//                        ScriptType.P2WPKH

                val legacy = addressConverter.convert(pubKey.publicKeyHash, ScriptType.P2PKH).string
//                    val wpkh = addressConverter.convert(pubKey.scriptHashP2WPKH, ScriptType.P2SH).string
//                    val bechAddress = try {
//                        addressConverter.convert(OpCodes.push(0) + OpCodes.push(pubKey.publicKeyHash), scriptType).string
//                    } catch (e: Exception) {
//                        ""
//                    }
                println("${pubKey.index} --- extrnl: ${pubKey.external} --- hash: ${pubKey.publicKeyHex} ---- legacy: $legacy")
//                    println("legacy: $legacy --- bech32: $bechAddress --- SH(WPKH): $wpkh")
            } catch (e: Exception) {
                println(e.message)
            }        }
    }

    //
    // DataProvider Listener implementations
    //
    override fun onTransactionsUpdate(inserted: List<TransactionInfo>, updated: List<TransactionInfo>) {
        listenerExecutor.execute {
            listeners.forEach {
                it.onTransactionsUpdate(inserted, updated)
            }
        }
    }

    override fun onTransactionsDelete(hashes: List<String>) {
        listenerExecutor.execute {
            listeners.forEach {
                it.onTransactionsDelete(hashes)
            }
        }
    }

    override fun onBalanceUpdate(balance: Long) {
        listenerExecutor.execute {
            listeners.forEach { it ->
                it.onBalanceUpdate(balance)
            }

        }
    }

    override fun onLastBlockInfoUpdate(blockInfo: BlockInfo) {
        listenerExecutor.execute {
            listeners.forEach {
                it.onLastBlockInfoUpdate(blockInfo)
            }
        }
    }

    //
    // KitStateProvider Listener implementations
    //
    override fun onKitStateUpdate(state: KitState) {
        listenerExecutor.execute {
            listeners.forEach {
                it.onKitStateUpdate(state)
            }
        }
    }

    private fun getFeeRate(feePriority: FeePriority) = when(feePriority) {
        FeePriority.Lowest -> dataProvider.feeRate.lowPriority
        FeePriority.Low -> (dataProvider.feeRate.lowPriority + dataProvider.feeRate.mediumPriority) / 2
        FeePriority.Medium -> dataProvider.feeRate.mediumPriority
        FeePriority.High -> (dataProvider.feeRate.mediumPriority + dataProvider.feeRate.highPriority) / 2
        FeePriority.Highest -> dataProvider.feeRate.highPriority
        is FeePriority.Custom -> feePriority.feeRate
    }

    sealed class KitState {
        object Synced : KitState()
        object NotSynced : KitState()
        class Syncing(val progress: Double) : KitState()

        override fun equals(other: Any?) = when {
            this is Synced && other is Synced -> true
            this is NotSynced && other is NotSynced -> true
            this is Syncing && other is Syncing -> this.progress == other.progress
            else -> false
        }

        override fun hashCode(): Int {
            var result = javaClass.hashCode()
            if (this is Syncing) {
                result = 31 * result + progress.hashCode()
            }
            return result
        }
    }

}
