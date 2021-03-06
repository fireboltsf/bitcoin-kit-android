package io.horizontalsystems.bitcoincore.managers

import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.bitcoincore.utils.IAddressConverter

interface IAddressSelector {
    fun getAddressVariants(addressConverter: IAddressConverter, pubKey: PublicKey): List<String>
}

class BitcoinAddressSelector : IAddressSelector {
    override fun getAddressVariants(addressConverter: IAddressConverter, pubKey: PublicKey): List<String> {
        val wpkhShAddress = addressConverter.convert(pubKey.scriptHashP2WPKH, ScriptType.P2SH).string
        return listOf(wpkhShAddress, pubKey.publicKeyHash.toHexString())
    }
}

class BitcoinCashAddressSelector : IAddressSelector {
    override fun getAddressVariants(addressConverter: IAddressConverter, pubKey: PublicKey): List<String> {
        val legacyAddress = addressConverter.convert(pubKey.publicKeyHash, ScriptType.P2PKH).string
        return listOf(legacyAddress)
    }
}

class DashAddressSelector : IAddressSelector {
    override fun getAddressVariants(addressConverter: IAddressConverter, pubKey: PublicKey): List<String> {
        val legacyAddress = addressConverter.convert(pubKey.publicKeyHash, ScriptType.P2PKH).string
        return listOf(legacyAddress)
    }
}
