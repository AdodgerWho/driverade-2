package com.driverade.service

/** Pure selection of the most informative Uber-owned screenshot window.
 * Caller supplies only windows whose roots are identified as Uber-owned.
 * Prefer a meaningful event window, otherwise avoid a tiny overlay when
 * a substantially larger Uber window is available.
 */
internal object UberWindowSelection {
    data class Frame(val id: Int, val area: Long)

    fun select(eventWindowId: Int, frames: List<Frame>): Int? {
        val largest = frames.maxByOrNull { it.area } ?: return null
        val event = frames.firstOrNull { it.id == eventWindowId }
        return if (event != null && event.area > 0 && event.area >= largest.area / 2)
            event.id else largest.id
    }
}
