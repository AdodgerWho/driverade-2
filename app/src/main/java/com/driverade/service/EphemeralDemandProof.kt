package com.driverade.service

import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Ephemeral, in-memory integrity proof for locally pooled demand points.
 * NOT remote authentication: a server must independently validate an attested
 * credential or server-issued token before trusting client-originated records.
 */
internal class EphemeralDemandProof {
    private val random = SecureRandom()
    private val key = ByteArray(32).also(random::nextBytes)
    private val usedNonces = mutableSetOf<String>()
    private val maxAgeMs = 5 * 60 * 1000L

    data class SignedPoint(
        val cell: String,
        val timeBucket: Long,
        val platform: String,
        val rateCents: Long,
        val nonce: String,
        val createdAtMs: Long,
        val mac: String
    )

    fun sign(cell: String, platform: String, rateCents: Long, now: Long): SignedPoint {
        val nonceBytes = ByteArray(16).also(random::nextBytes)
        val nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes)
        val bucket = now / 60_000L
        val mac = hmac(canonical(cell, bucket, platform, rateCents, nonce, now))
        return SignedPoint(cell, bucket, platform, rateCents, nonce, now, mac)
    }

    @Synchronized
    fun verifyOnce(point: SignedPoint, now: Long): Boolean {
        if (point.createdAtMs > now + 30_000L || now - point.createdAtMs > maxAgeMs) return false
        if (point.timeBucket != point.createdAtMs / 60_000L) return false
        if (point.rateCents <= 0L || point.rateCents > 25_000L) return false
        if (point.nonce in usedNonces) return false
        val expected = hmac(canonical(point.cell, point.timeBucket, point.platform,
            point.rateCents, point.nonce, point.createdAtMs))
        val valid = java.security.MessageDigest.isEqual(
            expected.toByteArray(Charsets.US_ASCII), point.mac.toByteArray(Charsets.US_ASCII)
        )
        if (valid) usedNonces.add(point.nonce)
        return valid
    }

    private fun canonical(cell: String, bucket: Long, platform: String,
                          cents: Long, nonce: String, time: Long) =
        listOf("driverade-demand-v1", cell, bucket.toString(), platform,
            cents.toString(), nonce, time.toString()).joinToString("|")

    private fun hmac(message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)))
    }
}
