package com.homehabit.app.engine

import com.homehabit.app.model.WidgetConfig

/**
 * Moteur de placement pour la grille. Deux modes coexistent :
 * - Redimensionnement et ajout de widget : grille libre, trous autorisés,
 *   aucun réagencement automatique (isValidPlacement / findFirstFreeSlot).
 * - Déplacement au doigt (drag) : réagencement en cascade façon masonry
 *   (resolvePushLayout), qui pousse les widgets qui seraient chevauchés
 *   plus bas plutôt que de refuser le placement.
 */
object GridEngine {

    fun overlaps(a: Rect, b: Rect): Boolean {
        return a.x < b.x + b.w && a.x + a.w > b.x &&
            a.y < b.y + b.h && a.y + a.h > b.y
    }

    /**
     * Vérifie qu'un placement candidat ne sort pas de la grille et ne
     * chevauche aucun widget existant (hors lui-même).
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
     * Cherche la première position libre (scan haut -> bas, gauche -> droite)
     * pour un widget de taille w x h. Utilisé à la création d'un nouveau widget.
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
        // Garde-fou : place tout en bas si jamais rien n'est trouvé
        return Rect(0, y, w, h)
    }

    /**
     * Calcule la disposition complète si le widget `draggedId` était pose
     * en `candidate` : les widgets qui se retrouveraient chevauches sont
     * poussés vers le bas (y uniquement, x/w/h inchangés), en cascade si
     * la poussée en entraine d'autres. Traitement dans l'ordre (y, x)
     * d'origine pour un résultat stable et prévisible.
     *
     * Pure fonction : ne modifie rien, se contente de calculer le layout
     * resultant. L'appelant décide de l'utiliser en aperçu pendant le
     * drag, et/ou de le committer au drop.
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
