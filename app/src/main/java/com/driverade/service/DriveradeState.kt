package com.driverade.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OfferEvaluation(
    val platform: String,
    val payout: Double,
    val hourlyRate: Double,
    val meetsTarget: Boolean,
    val rawNodes: List<String>
)

object DriveradeState {
    private val _latestOffer = MutableStateFlow<OfferEvaluation?>(null)
    val latestOffer: StateFlow<OfferEvaluation?> = _latestOffer.asStateFlow()

    fun recordOffer(evaluation: OfferEvaluation) {
        _latestOffer.value = evaluation
    }
}
