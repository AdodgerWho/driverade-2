package com.driverade.service

import android.view.accessibility.AccessibilityNodeInfo

class OptimizedOfferEngine(
    private val targetHourlyRate: Double,
    private val minPayout: Double
) {
    private var lastCachedFare: Double = -1.0
    private var lastCachedRate: Double = -1.0

    data class Metrics(var fare: Double = 0.0, var hourlyRate: Double = 0.0)

    fun evaluateNode(rootNode: AccessibilityNodeInfo): Metrics? {
        val metrics = findAndParseOffer(rootNode) ?: return null

        if (metrics.fare == lastCachedFare && metrics.hourlyRate == lastCachedRate) {
            return null 
        }

        lastCachedFare = metrics.fare
        lastCachedRate = metrics.hourlyRate
        return metrics
    }

    private fun findAndParseOffer(node: AccessibilityNodeInfo): Metrics? {
        fun traverse(current: AccessibilityNodeInfo): Metrics? {
            val text = current.text?.toString() ?: ""
            val desc = current.contentDescription?.toString() ?: ""

            if ((text.contains("$") || desc.contains("$")) &&
                (text.contains("min") || text.contains("mi") || text.contains("/hr"))) {
                
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
            if (text.contains("$") && metrics.fare == 0.0) {
                text.replace("$", "").trim().toDoubleOrNull()?.let { metrics.fare = it }
            }
            if (text.contains("/hr", ignoreCase = true)) {
                text.replace(Regex("[^\d.]"), "").toDoubleOrNull()?.let { metrics.hourlyRate = it }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
                    try {
                        extract(child)
                    } finally {
                        child.recycle()
                    }
                }
            }
        }
        extract(cardNode)
    }
}
