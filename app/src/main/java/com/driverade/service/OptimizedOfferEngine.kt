package com.driverade.service

import android.view.accessibility.AccessibilityNodeInfo

class OptimizedOfferEngine(
    private var targetRate: Double = 25.0,
    private var minPayout: Double = 10.0
) {

    data class Metrics(
        val fare: Double,
        val hourlyRate: Double,
        val isRideOffer: Boolean,
        val isAuthoritativeMapTotal: Boolean,
        val isGoodOffer: Boolean,
        val signature: String
    )

    companion object {
        private val FARE_REGEX = Regex("""\$\s*(\d{1,4}(?:\.\d{1,2})?)""")
        private val MINUTES_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
        private val HOURS_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:hrs?|hours?)\b""", RegexOption.IGNORE_CASE)
        private val MILES_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:mi|miles?)\b""", RegexOption.IGNORE_CASE)
        private val MAP_TOTAL_REGEX = Regex("""today'?s?\s+(?:earnings|total)""", RegexOption.IGNORE_CASE)
        private val ACTION_REGEX = Regex("""\b(?:accept|match|select)\b""", RegexOption.IGNORE_CASE)
        private const val AVG_MPH = 25.0
    }

    fun updateThresholds(targetRate: Double, minPayout: Double) {
        this.targetRate = targetRate
        this.minPayout = minPayout
    }

    fun evaluateNode(root: AccessibilityNodeInfo?): Metrics? {
        if (root == null) return null
        val text = collectText(root).trim()
        if (text.isEmpty()) return null
        return evaluateText(text)
    }

    internal fun evaluateText(text: String): Metrics? {
        val fare = FARE_REGEX.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null

        if (MAP_TOTAL_REGEX.containsMatchIn(text)) {
            return Metrics(fare, 0.0, false, true, false, text)
        }

        val isRideOffer = ACTION_REGEX.containsMatchIn(text)
        val minutes = MINUTES_REGEX.findAll(text).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 } +
            HOURS_REGEX.findAll(text).sumOf { (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60.0 }
        val miles = MILES_REGEX.findAll(text).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
        val effectiveMinutes = if (minutes > 0.0) minutes else miles / AVG_MPH * 60.0
        val hourlyRate = if (effectiveMinutes > 0.0) fare / (effectiveMinutes / 60.0) else 0.0
        val isGood = fare >= minPayout && hourlyRate >= targetRate

        return Metrics(fare, hourlyRate, isRideOffer, false, isGood, text)
    }

    private fun collectText(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun traverse(curr: AccessibilityNodeInfo) {
            curr.text?.toString()?.let { sb.append(it).append(' ') }
            curr.contentDescription?.toString()?.let { sb.append(it).append(' ') }
            for (i in 0 until curr.childCount) {
                curr.getChild(i)?.let { child ->
                    try {
                        traverse(child)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }
        traverse(node)
        return sb.toString()
    }
}
