package com.driverade.service

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class OptimizedOfferEngine {

    companion object {
        private const val TAG = "OptimizedOfferEngine"
        private const val UBER_PACKAGE = "com.ubercab.driver"
    }

    fun processAccessibilityNode(rootNode: AccessibilityNodeInfo?, packageName: String?): OfferDetails? {
        if (rootNode == null) return null

        if (packageName == UBER_PACKAGE || isUberContext(rootNode)) {
            val extractedText = mutableListOf<String>()
            traverseNodeTree(rootNode, extractedText)
            
            Log.d(TAG, "Traversed Uber Node Tree. Extracted ${extractedText.size} text nodes.")
            return parseUberOffer(extractedText)
        }

        return null
    }

    private fun traverseNodeTree(node: AccessibilityNodeInfo, results: MutableList<String>) {
        if (!node.text.isNullOrEmpty()) {
            results.add(node.text.toString())
        } else if (!node.contentDescription.isNullOrEmpty()) {
            results.add(node.contentDescription.toString())
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseNodeTree(child, results)
                child.recycle()
            }
        }
    }

    private fun isUberContext(node: AccessibilityNodeInfo): Boolean {
        return node.packageName?.toString()?.contains("ubercab") == true
    }

    private fun parseUberOffer(texts: List<String>): OfferDetails? {
        var payout = -1.0
        var miles = -1.0

        for (text in texts) {
            val clean = text.trim()
            if (clean.startsWith("$")) {
                payout = clean.removePrefix("$").toDoubleOrNull() ?: payout
            }
            if (clean.contains("mi", ignoreCase = true)) {
                miles = clean.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: miles
            }
        }

        if (payout > 0.0) {
            return OfferDetails(platform = "UBER", payout = payout, miles = miles, rawTexts = texts)
        }
        return null
    }
}

data class OfferDetails(
    val platform: String,
    val payout: Double,
    val miles: Double,
    val rawTexts: List<String>
)
