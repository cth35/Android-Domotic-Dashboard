package com.homehabit.app.engine

import com.homehabit.app.model.WidgetConfig

/**
 * Placement engine for the grid. Two modes coexist:
 * - Resizing and adding widgets: free grid, holes allowed,
 *   no automatic rearrangement (isValidPlacement / findFirstFreeSlot).
 * - Finger movement (drag): cascade rearrangement like masonry
 *   (resolvePushLayout), which pushes widgets that would be overlapped
 *   lower rather than refusing placement.
 */
object GridEngine {

    fun overlaps(a: Rect, b: Rect): Boolean {
        return a.x < b.x + b.w && a.x + a.w > b.x &&
            a.y < b.y + b.h && a.y + a.h > b.y
    }

    /**
     * Verifies that a candidate placement does not go out of the grid and does not
     * overlap any existing widget (except itself).
     */
    fun isValidPlacement(
        candidateId: String,
        rect: Rect,
        others: List<WidgetConfig>,
        columns: Int
    ): Boolean {
        if (rect.x < 0 || rect.y < 0) return false
        if (rect.w <= 0 || rect.h <= 0) return false
        if (rect.x + rect.w > columns) return false

        return others.none { other ->
            other.id != candidateId && overlaps(rect, other.toRect())
        }
    }

    /**
     * Searches for the first free position (scan top -> bottom, left -> right)
     * for a widget of size w x h. Used when creating a new widget.
     */
    fun findFirstFreeSlot(
        w: Int,
        h: Int,
        existing: List<WidgetConfig>,
        columns: Int,
        maxRowsScanned: Int = 500
    ): Rect {
        var y = 0
        while (y < maxRowsScanned) {
            for (x in 0..(columns - w)) {
                val candidate = Rect(x, y, w, h)
                if (isValidPlacement("_new_widget_", candidate, existing, columns)) {
                    return candidate
                }
            }
            y++
        }
        // Safeguard: place at the very bottom if nothing is found
        return Rect(0, y, w, h)
    }

    /**
     * Calculates the full layout if the `draggedId` widget was placed
     * in `candidate`: widgets that would be overlapped are
     * pushed down (y only, x/w/h unchanged), in cascade if
     * the push triggers others. Processed in the original (y, x) order
     * for a stable and predictable result.
     *
     * Pure function: modifies nothing, only calculates the resulting
     * layout. The caller decides whether to use it as a preview during
     * drag, and/or commit it at drop.
     */
    fun resolvePushLayout(
        draggedId: String,
        candidate: Rect,
        allWidgets: List<WidgetConfig>,
        columns: Int
    ): Map<String, Rect> {
        val clampedCandidate = candidate.copy(
            x = candidate.x.coerceIn(0, (columns - candidate.w).coerceAtLeast(0)),
            y = candidate.y.coerceAtLeast(0)
        )

        val finalized = LinkedHashMap<String, Rect>()
        finalized[draggedId] = clampedCandidate

        val others = allWidgets
            .filter { it.id != draggedId }
            .sortedWith(compareBy({ it.y }, { it.x }))

        for (widget in others) {
            var rect = widget.toRect()
            var guard = 0
            var moved = true
            while (moved && guard < 300) {
                moved = false
                guard++
                for (placed in finalized.values) {
                    if (overlaps(rect, placed)) {
                        val pushedY = placed.y + placed.h
                        rect = rect.copy(y = if (pushedY > rect.y) pushedY else rect.y + 1)
                        moved = true
                    }
                }
            }
            finalized[widget.id] = rect
        }

        return finalized
    }

    data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun WidgetConfig.toRect() = Rect(x, y, w, h)
}
