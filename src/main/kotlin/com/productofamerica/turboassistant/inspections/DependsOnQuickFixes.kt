package com.productofamerica.turboassistant.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

internal class ReplaceWithNearMatchFix(
    private val nearMatch: String,
    private val carryCaretPrefix: Boolean,
) : LocalQuickFix {

    override fun getName(): String = "Replace with '$nearMatch'"
    override fun getFamilyName(): String = "Replace dependsOn entry"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val literal = descriptor.psiElement as? JsonStringLiteral ?: return
        val file = literal.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val replacement = if (carryCaretPrefix) "^$nearMatch" else nearMatch
        val escaped = StringUtil.escapeStringCharacters(replacement)
        val range = literal.textRange
        document.replaceString(range.startOffset, range.endOffset, "\"$escaped\"")
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}

internal class RemoveDependsOnEntryFix : LocalQuickFix {

    override fun getName(): String = "Remove dependsOn entry"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val literal = descriptor.psiElement as? JsonStringLiteral ?: return
        val array = literal.parent as? JsonArray ?: return
        var first: PsiElement = literal
        var last: PsiElement = literal
        var sib = literal.nextSibling
        while (sib != null && sib.text.isBlank()) sib = sib.nextSibling
        if (sib != null && sib.text == ",") {
            last = sib
        } else {
            var prev = literal.prevSibling
            while (prev != null && prev.text.isBlank()) prev = prev.prevSibling
            if (prev != null && prev.text == ",") first = prev
        }
        array.deleteChildRange(first, last)
    }
}
