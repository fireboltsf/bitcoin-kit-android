package io.horizontalsystems.bitcoincash.blocks.validators

import io.horizontalsystems.bitcoincash.blocks.BitcoinCashBlockValidatorHelper
import io.horizontalsystems.bitcoincore.blocks.validators.BlockValidatorException
import io.horizontalsystems.bitcoincore.blocks.validators.IBlockValidator
import io.horizontalsystems.bitcoincore.crypto.CompactBits
import io.horizontalsystems.bitcoincore.models.Block

class DAAValidator(private val targetSpacing: Int, private val blockValidatorHelper: BitcoinCashBlockValidatorHelper) : IBlockValidator {
    private val largestHash = 1.toBigInteger() shl 256
    private val diffDate = 1510600000 // 2017 November 3, 14:06 GMT

    override fun isBlockValidatable(block: Block, previousBlock: Block): Boolean {
        return blockValidatorHelper.medianTimePast(block) >= diffDate
    }

    override fun validate(block: Block, previousBlock: Block) {
        val chunk = blockValidatorHelper.getPreviousChunk(previousBlock.height, 147)
        if (chunk.size < 147) {
            return
        }

        validateDAA(block, chunk)
    }

    private fun validateDAA(candidate: Block, chunk: List<Block>) {
        val firstSuitable = blockValidatorHelper.getSuitableBlock(mutableListOf(chunk[0], chunk[1], chunk[2]))
        val lastSuitable = blockValidatorHelper.getSuitableBlock(mutableListOf(chunk[chunk.size - 3], chunk[chunk.size - 2], chunk[chunk.size - 1]))

        val heightInterval = lastSuitable.height - firstSuitable.height

        var actualTimespan = lastSuitable.timestamp - firstSuitable.timestamp
        if (actualTimespan > 288 * targetSpacing)
            actualTimespan = 288 * targetSpacing.toLong()
        if (actualTimespan < 72 * targetSpacing)
            actualTimespan = 72 * targetSpacing.toLong()

        val lastSuitableIndex = chunk.indexOf(lastSuitable)
        val blocks = chunk.slice((lastSuitableIndex - heightInterval + 1) until lastSuitableIndex).toMutableList()

        var chainWork = 0.toBigInteger()
        blocks += lastSuitable
        blocks.forEach {
            val target = CompactBits.decode(it.bits)
            chainWork += largestHash / (target + 1.toBigInteger())
        }

        chainWork = chainWork * targetSpacing.toBigInteger() / actualTimespan.toBigInteger()

        val target = largestHash / chainWork - 1.toBigInteger()
        val bits = CompactBits.encode(target)
        if (bits != candidate.bits) {
            throw BlockValidatorException.NotEqualBits()
        }
    }
}
