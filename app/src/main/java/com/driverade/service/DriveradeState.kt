package com.driverade.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.floor
import kotlin.math.roundToLong

data class OfferEvaluation(
    val platform: String,
    val packageName: String,
    val payout: Double,
    val totalMiles: Double,
    val totalMinutes: Double,
    val hourlyRate: Double,
    val dollarsPerMile: Double,
    val meetsTarget: Boolean,
    val rawNodes: List<String>,
    val timestamp: Long = System.currentTimeMillis()
)

data class DemandPoint(
    val geoHashCell: String,
    val platform: String,
    val hourlyRate: Double,
    val timestamp: Long
)

object DriveradeState {
    private val _latestOffer = MutableStateFlow<OfferEvaluation?>(null)
    val latestOffer: StateFlow<OfferEvaluation?> = _latestOffer.asStateFlow()

    var uberMapTotal: Double = -1.0
    var lyftMapTotal: Double = -1.0
    var hichMapTotal: Double = -1.0

    private val demandWindowMs = 5 * 60 * 1000L
    private val pooledDemandPoints = mutableListOf<DemandPoint>()
    private val demandProof = EphemeralDemandProof()

    fun recordOffer(evaluation: OfferEvaluation) {
        _latestOffer.value = evaluation
    }

    @Synchronized
    fun recordValidatedTelemetry(
        lat: Double,
        lon: Double,
        platform: String,
        hourlyRate: Double
    ): Boolean {
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return false
        if (platform !in setOf("UBER", "UBER_EATS", "LYFT", "HICH", "DOORDASH", "GRUBHUB")) return false
        if (!hourlyRate.isFinite() || hourlyRate <= 0.0 || hourlyRate > 250.0) return false

        val now = System.currentTimeMillis()
        pooledDemandPoints.removeAll { now - it.timestamp > demandWindowMs }

        // Coarse ~0.02 degree grid; exact GPS values are never retained.
        val cellLat = floor(lat * 50.0).toInt()
        val cellLon = floor(lon * 50.0).toInt()
        val cellId = "cell_${cellLat}_${cellLon}"
        val cents = (hourlyRate * 100.0).roundToLong()
        val signed = demandProof.sign(cellId, platform, cents, now)
        if (!demandProof.verifyOnce(signed, now)) return false
        pooledDemandPoints.add(DemandPoint(cellId, platform, hourlyRate, now))
        return true
    }

    @Synchronized
    fun getRollingGridDensity(): Map<String, Int> {
        val now = System.currentTimeMillis()
        pooledDemandPoints.removeAll { now - it.timestamp > demandWindowMs }
        return pooledDemandPoints.groupingBy { it.geoHashCell }.eachCount()
    }
}