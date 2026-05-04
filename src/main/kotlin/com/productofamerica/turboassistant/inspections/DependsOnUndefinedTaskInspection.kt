package com.productofamerica.turboassistant.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonElementVisitor
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.util.text.EditDistance
import com.intellij.psi.PsiElementVisitor
import com.productofamerica.turboassistant.services.WorkspaceModelService
import com.productofamerica.turboassistant.util.isTurboFile

class DependsOnUndefinedTaskInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : JsonElementVisitor() {
            override fun visitStringLiteral(literal: JsonStringLiteral) {
                if (!isTurboFile(literal.containingFile)) return
                if (!isInDependsOnArray(literal)) return

                val raw = literal.value
                if (raw.startsWith("$")) return
                if ("#" in raw) return

                val carryCaret = raw.startsWith("^")
                val taskName = if (carryCaret) raw.removePrefix("^") else raw

                val known = WorkspaceModelService.getInstance(literal.project).taskNames
                if (taskName in known) return

                val nearMatch = nearestMatch(taskName, known, MAX_EDIT_DISTANCE)
                val fixes: Array<LocalQuickFix> = buildList {
                    if (nearMatch != null) add(ReplaceWithNearMatchFix(nearMatch, carryCaret))
                    add(RemoveDependsOnEntryFix())
                }.toTypedArray()

                holder.registerProblem(
                    literal,
                    "Task '$taskName' is not defined in turbo.json or any package.json script.",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    *fixes,
                )
            }
        }

    private fun isInDependsOnArray(literal: JsonStringLiteral): Boolean {
        val array = literal.parent as? JsonArray ?: return false
        val prop = array.parent as? JsonProperty ?: return false
        return prop.name == "dependsOn"
    }

    private fun nearestMatch(target: String, candidates: Set<String>, maxDistance: Int): String? {
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for (c in candidates) {
            val d = EditDistance.levenshtein(target, c, true)
            if (d < bestDist && d <= maxDistance) {
                best = c
                bestDist = d
            }
        }
        return best
    }

    companion object {
        private const val MAX_EDIT_DISTANCE = 2
    }
}
