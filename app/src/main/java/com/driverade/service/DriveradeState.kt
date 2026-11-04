package com.driverade.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

object DriveradeState {
    private val _latestOffer = MutableStateFlow<OfferEvaluation?>(null)
    val latestOffer: StateFlow<OfferEvaluation?> = _latestOffer.asStateFlow()

    fun recordOffer(evaluation: OfferEvaluation) {
        _latestOffer.value = evaluation
    }

    fun getRollingGridDensity(): Map<String, Int> = emptyMap()
}
