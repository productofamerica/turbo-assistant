package com.productofamerica.turboassistant.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonElementVisitor
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.productofamerica.turboassistant.services.WorkspaceModelService
import com.productofamerica.turboassistant.util.isTurboFile

class PackageQualifiedTaskInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : JsonElementVisitor() {
            override fun visitStringLiteral(literal: JsonStringLiteral) {
                if (!isTurboFile(literal.containingFile)) return
                if (!isPackageQualifiedRefSite(literal)) return

                val raw = literal.value
                if (raw.startsWith("$")) return
                val hashIdx = raw.indexOf('#')
                if (hashIdx <= 0) return

                val packageName = raw.substring(0, hashIdx)
                if (packageName == "//") return
                val known = WorkspaceModelService.getInstance(literal.project).packageNames
                if (packageName in known) return

                holder.registerProblem(
                    literal,
                    "Package '$packageName' is not defined in this workspace.",
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                    TextRange(1, 1 + packageName.length),
                )
            }
        }

    private fun isPackageQualifiedRefSite(literal: JsonStringLiteral): Boolean =
        isInDependsOnArray(literal) || isTopLevelTasksKey(literal)

    private fun isInDependsOnArray(literal: JsonStringLiteral): Boolean {
        val array = literal.parent as? JsonArray ?: return false
        val prop = array.parent as? JsonProperty ?: return false
        return prop.name == "dependsOn"
    }

    private fun isTopLevelTasksKey(literal: JsonStringLiteral): Boolean {
        val prop = literal.parent as? JsonProperty ?: return false
        if (prop.nameElement !== literal) return false
        val tasksObj = prop.parent as? JsonObject ?: return false
        val tasksProp = tasksObj.parent as? JsonProperty ?: return false
        if (tasksProp.name != "tasks") return false
        val rootObj = tasksProp.parent as? JsonObject ?: return false
        val file = rootObj.parent as? JsonFile ?: return false
        return file.topLevelValue === rootObj
    }
}
