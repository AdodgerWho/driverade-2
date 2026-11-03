package com.driverade.service

import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect

class OptimizedOfferEngine(
    private var targetHourlyRate: Double = 25.0,
    private var minPayout: Double = 10.0
) {
    data class Metrics(
        var fare: Double = 0.0,
        var hourlyRate: Double = 0.0,
        var isAuthoritativeMapTotal: Boolean = false,
        var isRideOffer: Boolean = false,
        var isGoodOffer: Boolean = false,
        var signature: String = ""
    )

    fun updateThresholds(targetHourlyRate: Double, minPayout: Double) {
        this.targetHourlyRate = targetHourlyRate
        this.minPayout = minPayout
    }

    fun evaluateNode(node: AccessibilityNodeInfo): Metrics? {
        val metrics = findAndParseOffer(node) ?: return null
        metrics.isGoodOffer = metrics.fare >= minPayout || metrics.hourlyRate >= targetHourlyRate
        return metrics
    }

    private fun findAndParseOffer(node: AccessibilityNodeInfo): Metrics? {
        var topMiddleFare = -1.0

        fun parseTopMiddle(current: AccessibilityNodeInfo) {
            val text = listOfNotNull(
                current.text?.toString(),
                current.contentDescription?.toString()
            ).joinToString(" ")
            if (text.contains("$") && text.any { it.isDigit() }) {
                val rect = Rect()
                current.getBoundsInScreen(rect)
                if (rect.top >= 0 && rect.top <= 240 && rect.width() > 0 && rect.height() > 0) {
                    extractCurrencyAmount(text)?.let {
                        if (it >= 0.0 && it < 10000.0) topMiddleFare = it
                    }
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child: AccessibilityNodeInfo ->
                    try { parseTopMiddle(child) } finally { child.recycle() }
                }
            }
        }
        fun traverse(current: AccessibilityNodeInfo): Metrics? {
            val text = current.text?.toString() ?: ""
            val desc = current.contentDescription?.toString() ?: ""
            val combined = "$text $desc"

            val hasCurrency = combined.contains("$")
            val hasTripKeywords = combined.contains("min", ignoreCase = true) ||
                                  combined.contains("mi", ignoreCase = true) ||
                                  combined.contains("/hr", ignoreCase = true) ||
                                  combined.contains("trip", ignoreCase = true) ||
                                  combined.contains("ride", ignoreCase = true) ||
                                  combined.contains("offer", ignoreCase = true)

            if (hasCurrency && (hasTripKeywords || combined.contains("accept", ignoreCase = true))) {
                val metrics = Metrics(isRideOffer = true)
                parseMetrics(current, metrics)
                if (metrics.fare > 0.0 || metrics.hourlyRate > 0.0) {
                    return metrics
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child: AccessibilityNodeInfo ->
                    try {
                        val result = traverse(child)
                        if (result != null) return result
                    } finally {
                        child.recycle()
                    }
                }
            }
            return null
        }
        traverse(node)?.let { return it }

        parseTopMiddle(node)
        if (topMiddleFare >= 0.0) {
            return Metrics(fare = topMiddleFare, hourlyRate = 0.0, isAuthoritativeMapTotal = true)
        }
        return null
    }

    private fun parseMetrics(cardNode: AccessibilityNodeInfo, metrics: Metrics) {
        val signatureParts = mutableListOf<String>()
        val durationMinutes = mutableSetOf<Double>()

        fun extract(current: AccessibilityNodeInfo) {
            val text = listOfNotNull(
                current.text?.toString(),
                current.contentDescription?.toString()
            ).joinToString(" ")
            if (text.isNotBlank()) {
                signatureParts.add(text.trim())
                Regex("""(\d+(?:\.\d+)?)\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
                    .findAll(text)
                    .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                    .filter { it > 0.0 }
                    .forEach { durationMinutes.add(it) }
            }
            if (text.contains("$") && !text.contains("/hr", ignoreCase = true) && metrics.fare == 0.0) {
                extractCurrencyAmount(text)?.let { metrics.fare = it }
            }
            if (text.contains("/hr", ignoreCase = true)) {
                val ratePart = text.substringBefore("/hr").substringBefore("for")
                val rate = extractCurrencyAmount(ratePart)
                    ?: Regex("""\d+(?:\.\d+)?""").find(ratePart)?.value?.toDoubleOrNull()
                if (rate != null && rate > 0.0 && metrics.hourlyRate == 0.0) {
                    metrics.hourlyRate = rate
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child: AccessibilityNodeInfo ->
                    try { extract(child) } finally { child.recycle() }
                }
            }
        }
        extract(cardNode)
        val totalMinutes = durationMinutes.sum()
        if (metrics.hourlyRate == 0.0 && metrics.fare > 0.0 && totalMinutes > 0.0) {
            metrics.hourlyRate = metrics.fare * 60.0 / totalMinutes
        }
        metrics.signature = signatureParts.joinToString(" ").replace(Regex("\\s+"), " ").take(512)
    }

    private fun extractCurrencyAmount(text: String): Double? {
        val amount = Regex("""\$\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]+)?)""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?: return null
        return amount.replace(",", "").toDoubleOrNull()
    }
}
