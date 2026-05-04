package com.productofamerica.turboassistant.references

import com.intellij.json.psi.JsonArray
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.util.ProcessingContext
import com.productofamerica.turboassistant.util.isPackageJsonFile
import com.productofamerica.turboassistant.util.isTurboFile

class TurboReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val dependsOnPattern = PlatformPatterns.psiElement(JsonStringLiteral::class.java)
            .withParent(
                PlatformPatterns.psiElement(JsonArray::class.java)
                    .withParent(
                        PlatformPatterns.psiElement(JsonProperty::class.java).withName("dependsOn")
                    )
            )
        registrar.registerReferenceProvider(dependsOnPattern, DependsOnReferenceProvider())

        val scriptKeyPattern = PlatformPatterns.psiElement(JsonStringLiteral::class.java)
            .withParent(
                PlatformPatterns.psiElement(JsonProperty::class.java)
                    .withParent(
                        PlatformPatterns.psiElement(JsonObject::class.java)
                            .withParent(
                                PlatformPatterns.psiElement(JsonProperty::class.java).withName("scripts")
                            )
                    )
            )
        registrar.registerReferenceProvider(scriptKeyPattern, ScriptKeyReferenceProvider())
    }
}

private class DependsOnReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val literal = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY
        if (!isTurboFile(literal.containingFile)) return PsiReference.EMPTY_ARRAY

        val raw = literal.value
        if (raw.isEmpty()) return PsiReference.EMPTY_ARRAY
        if (raw.startsWith("$")) return PsiReference.EMPTY_ARRAY

        val hashIdx = raw.indexOf('#')
        if (hashIdx >= 0) {
            if (hashIdx == 0 || hashIdx == raw.length - 1) return PsiReference.EMPTY_ARRAY
            val packageName = raw.substring(0, hashIdx)
            val taskName = raw.substring(hashIdx + 1)
            if (packageName == "//") {
                val contentRange = TextRange(1, literal.textLength - 1)
                return arrayOf(RootTaskReference(literal, contentRange, raw))
            }
            val pkgRange = TextRange(1, 1 + packageName.length)
            val taskRange = TextRange(2 + packageName.length, 2 + packageName.length + taskName.length)
            return arrayOf(
                PackageNameReference(literal, pkgRange, packageName),
                PackageScriptReference(literal, taskRange, packageName, taskName),
            )
        }

        val taskName = if (raw.startsWith("^")) raw.removePrefix("^") else raw
        if (taskName.isEmpty()) return PsiReference.EMPTY_ARRAY

        val contentRange = TextRange(1, literal.textLength - 1)
        return arrayOf(DependsOnTaskReference(literal, contentRange, taskName))
    }
}

private class ScriptKeyReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val literal = element as? JsonStringLiteral ?: return PsiReference.EMPTY_ARRAY
        if (!isPackageJsonFile(literal.containingFile)) return PsiReference.EMPTY_ARRAY

        val prop = literal.parent as? JsonProperty ?: return PsiReference.EMPTY_ARRAY
        if (prop.nameElement !== literal) return PsiReference.EMPTY_ARRAY

        val scriptName = literal.value
        if (scriptName.isEmpty()) return PsiReference.EMPTY_ARRAY

        val contentRange = TextRange(1, literal.textLength - 1)
        return arrayOf(ScriptToTurboTaskReference(literal, contentRange, scriptName))
    }
}
