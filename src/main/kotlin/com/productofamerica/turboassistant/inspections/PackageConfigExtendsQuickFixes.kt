package com.productofamerica.turboassistant.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonElementGenerator
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonPsiUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

internal class AddExtendsFieldFix : LocalQuickFix {
    override fun getName(): String = "Add extends field"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile as? JsonFile ?: return
        val rootObject = file.topLevelValue as? JsonObject ?: return
        val generator = JsonElementGenerator(project)
        val newProperty = generator.createProperty("extends", "[\"//\"]")
        WriteCommandAction.runWriteCommandAction(project) {
            JsonPsiUtil.addProperty(rootObject, newProperty, true)
        }
    }
}

internal class SetExtendsToRootFix : LocalQuickFix {
    override fun getName(): String = "Set extends to [\"//\"]"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val anchor = descriptor.psiElement
        val extendsProperty = anchor as? JsonProperty
            ?: anchor.parent as? JsonProperty
            ?: return
        val generator = JsonElementGenerator(project)
        val newArray = generator.createValue<JsonArray>("[\"//\"]")
        WriteCommandAction.runWriteCommandAction(project) {
            extendsProperty.value?.replace(newArray)
        }
    }
}
