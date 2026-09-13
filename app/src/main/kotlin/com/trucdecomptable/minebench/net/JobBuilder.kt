package com.trucdecomptable.minebench.net

import com.trucdecomptable.minebench.core.Block
import com.trucdecomptable.minebench.core.Hex
import com.trucdecomptable.minebench.core.MiningWork
import com.trucdecomptable.minebench.core.Sha256Md
import com.trucdecomptable.minebench.core.Target

/** Transforme un job Stratum en en-tête de 80 octets prêt à miner. */
object JobBuilder {

    fun coinbase(job: StratumJob, extranonce1Hex: String, extranonce2Hex: String): ByteArray =
        Hex.decode(job.coinb1 + extranonce1Hex + extranonce2Hex + job.coinb2)

    fun header(job: StratumJob, extranonce1Hex: String, extranonce2Hex: String, nonce: Long): ByteArray {
        val sha = Sha256Md()
        val branches = job.merkleBranches.map { Hex.decode(it) }
        val merkle = Block.merkleRoot(sha, coinbase(job, extranonce1Hex, extranonce2Hex), branches)
        return Block.header(
            version = Hex.parseUInt32(job.versionHex),
            prevHashInternal = Block.prevHashFromStratumHex(job.prevHashHex),
            merkleRootInternal = merkle,
            ntime = Hex.parseUInt32(job.ntimeHex),
            nbits = Hex.parseUInt32(job.nbitsHex),
            nonce = nonce
        )
    }

    fun build(
        job: StratumJob,
        extranonce1Hex: String,
        extranonce2Hex: String,
        shareDifficulty: Double
    ): MiningWork = MiningWork(
        jobId = job.jobId,
        header = header(job, extranonce1Hex, extranonce2Hex, 0L),
        shareTarget = Target.fromDifficulty(shareDifficulty),
        blockTarget = Target.fromCompact(Hex.parseUInt32(job.nbitsHex)),
        xn2Hex = extranonce2Hex,
        ntimeHex = job.ntimeHex
    )
}
