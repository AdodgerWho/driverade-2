package com.driverade.service

import android.accessibilityservice.AccessibilityNodeInfo
import android.graphics.Rect

class OptimizedOfferEngine(
    private val targetHourlyRate: Double = 25.0,
    private val minPayout: Double = 10.0
) {
    private var lastCachedFare: Double = -1.0
    private var lastCachedRate: Double = -1.0
    private var lastOfferTimestampMs: Long = 0L

    data class Metrics(
        var fare: Double = 0.0,
        var hourlyRate: Double = 0.0,
        var isAuthoritativeMapTotal: Boolean = false
    )

    fun evaluateNode(rootNode: AccessibilityNodeInfo): Metrics? {
        val metrics = findAndParseOffer(rootNode) ?: return null
        val now = System.currentTimeMillis()

        if (!metrics.isAuthoritativeMapTotal && metrics.fare == lastCachedFare && metrics.hourlyRate == lastCachedRate && (now - lastOfferTimestampMs) < 15000L) {
            return null
        }

        lastCachedFare = metrics.fare
        lastCachedRate = metrics.hourlyRate
        lastOfferTimestampMs = now
        return metrics
    }

    private fun findAndParseOffer(node: AccessibilityNodeInfo): Metrics? {
        var topMiddleFare = -1.0

        fun parseTopMiddle(current: AccessibilityNodeInfo) {
            val text = current.text?.toString() ?: current.contentDescription?.toString() ?: ""
            if (text.contains("$") && text.any { it.isDigit() }) {
                val rect = Rect()
                current.getBoundsInScreen(rect)
                // Top-middle map pill bounds (verified via screenshots for Uber & Hich Driver maps)
                if (rect.top >= 0 && rect.top <= 240 && rect.width() > 0 && rect.height() > 0) {
                    val cleaned = text.replace(Regex("[^0-9.]"), "")
                    cleaned.toDoubleOrNull()?.let {
                        if (it >= 0.0 && it < 10000.0) {
                            topMiddleFare = it
                        }
                    }
                }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
                    try { parseTopMiddle(child) } finally { child.recycle() }
                }
            }
        }
        parseTopMiddle(node)

        // If top-middle pill is found, it serves as the authoritative final total check
        if (topMiddleFare >= 0.0) {
            return Metrics(fare = topMiddleFare, hourlyRate = 0.0, isAuthoritativeMapTotal = true)
        }

        fun traverse(current: AccessibilityNodeInfo): Metrics? {
            val text = current.text?.toString() ?: ""
            val desc = current.contentDescription?.toString() ?: ""

            if ((text.contains("$") || desc.contains("$")) &&
                (text.contains("min") || text.contains("mi") || text.contains("/hr"))
            ) {
                val metrics = Metrics()
                parseMetrics(current, metrics)
                if (metrics.fare > 0.0 || metrics.hourlyRate > 0.0) {
                    return metrics
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
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
        return traverse(node)
    }

    private fun parseMetrics(cardNode: AccessibilityNodeInfo, metrics: Metrics) {
        fun extract(current: AccessibilityNodeInfo) {
            val text = current.text?.toString() ?: current.contentDescription?.toString() ?: ""
            if (text.contains("$") && text.any { it.isDigit() } && !text.contains("/hr", ignoreCase = true) && metrics.fare == 0.0) {
                text.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.let { metrics.fare = it }
            }
            if (text.contains("/hr", ignoreCase = true)) {
                val ratePart = text.substringBefore("/hr")
                ratePart.replace(Regex("[^0-9.]"), "").toDoubleOrNull()?.let { metrics.hourlyRate = it }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
                    try {
                        extract(child)
                    } while (false) {
                        child.recycle()
                    }
                    try {
                        child.recycle()
                    } catch (e: Exception) {}
                }
            }
        }
        extract(cardNode)
    }
}
