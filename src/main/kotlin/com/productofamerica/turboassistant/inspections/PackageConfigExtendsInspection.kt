package com.productofamerica.turboassistant.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.json.psi.JsonValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.productofamerica.turboassistant.services.WorkspaceModelService
import com.productofamerica.turboassistant.util.isTurboFile

class PackageConfigExtendsInspection : LocalInspectionTool() {

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor>? {
        if (!isTurboFile(file)) return null
        val vFile = file.virtualFile ?: return null
        if (!WorkspaceModelService.getInstance(file.project).isPackageConfig(vFile)) return null

        val rootObject = (file as? JsonFile)?.topLevelValue as? JsonObject ?: return null
        val holder = ProblemsHolder(manager, file, isOnTheFly)
        val extendsProperty = rootObject.findProperty("extends")

        if (extendsProperty == null) {
            val anchor: PsiElement = rootObject.firstChild ?: rootObject
            holder.registerProblem(
                anchor,
                "Package config must extend the root config. Add \"extends\": [\"//\"].",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                AddExtendsFieldFix(),
            )
        } else if (!isExactlyRootExtendsArray(extendsProperty.value)) {
            val anchor: PsiElement = extendsProperty.value ?: extendsProperty
            holder.registerProblem(
                anchor,
                "Package config 'extends' must be exactly [\"//\"].",
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                SetExtendsToRootFix(),
            )
        }
        return holder.resultsArray
    }

    private fun isExactlyRootExtendsArray(value: JsonValue?): Boolean {
        val array = value as? JsonArray ?: return false
        if (array.valueList.size != 1) return false
        val only = array.valueList.single() as? JsonStringLiteral ?: return false
        return only.value == "//"
    }
}
