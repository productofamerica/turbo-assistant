package com.productofamerica.turboassistant.references

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.ResolveResult
import com.productofamerica.turboassistant.services.WorkspaceModelService

internal fun findTaskProperty(turboFile: PsiFile, taskName: String): JsonProperty? {
    val root = (turboFile as? JsonFile)?.topLevelValue as? JsonObject ?: return null
    val tasks = root.findProperty("tasks")?.value as? JsonObject ?: return null
    return tasks.findProperty(taskName)
}

internal fun findScriptProperty(packageJsonFile: PsiFile, scriptName: String): JsonProperty? {
    val root = (packageJsonFile as? JsonFile)?.topLevelValue as? JsonObject ?: return null
    val scripts = root.findProperty("scripts")?.value as? JsonObject ?: return null
    return scripts.findProperty(scriptName)
}

internal fun findPackageNameLiteral(packageJsonFile: PsiFile): JsonStringLiteral? {
    val root = (packageJsonFile as? JsonFile)?.topLevelValue as? JsonObject ?: return null
    return root.findProperty("name")?.value as? JsonStringLiteral
}

class DependsOnTaskReference(
    element: JsonStringLiteral,
    rangeInElement: TextRange,
    private val taskName: String,
) : PsiPolyVariantReferenceBase<JsonStringLiteral>(element, rangeInElement) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val containing = element.containingFile
        findTaskProperty(containing, taskName)?.let {
            return arrayOf(PsiElementResolveResult(it))
        }
        val service = WorkspaceModelService.getInstance(element.project)
        val results = mutableListOf<ResolveResult>()
        for (pkgJson in service.packageJsonPsiFiles()) {
            findScriptProperty(pkgJson, taskName)?.let {
                results += PsiElementResolveResult(it)
            }
        }
        return results.toTypedArray()
    }
}

class PackageNameReference(
    element: JsonStringLiteral,
    rangeInElement: TextRange,
    private val packageName: String,
) : PsiReferenceBase<JsonStringLiteral>(element, rangeInElement) {

    override fun resolve(): PsiElement? {
        val service = WorkspaceModelService.getInstance(element.project)
        val pkgJson = service.findPackageJsonByName(packageName) ?: return null
        return findPackageNameLiteral(pkgJson)
    }
}

class PackageScriptReference(
    element: JsonStringLiteral,
    rangeInElement: TextRange,
    private val packageName: String,
    private val scriptName: String,
) : PsiReferenceBase<JsonStringLiteral>(element, rangeInElement) {

    override fun resolve(): PsiElement? {
        val service = WorkspaceModelService.getInstance(element.project)
        val pkgJson = service.findPackageJsonByName(packageName) ?: return null
        return findScriptProperty(pkgJson, scriptName)
    }
}

class ScriptToTurboTaskReference(
    element: JsonStringLiteral,
    rangeInElement: TextRange,
    private val scriptName: String,
) : PsiReferenceBase<JsonStringLiteral>(element, rangeInElement) {

    override fun resolve(): PsiElement? {
        val service = WorkspaceModelService.getInstance(element.project)
        val rootTurbo = service.rootTurboFile() ?: return null
        return findTaskProperty(rootTurbo, scriptName)
    }
}

class RootTaskReference(
    element: JsonStringLiteral,
    rangeInElement: TextRange,
    private val taskKey: String,
) : PsiReferenceBase<JsonStringLiteral>(element, rangeInElement) {

    override fun resolve(): PsiElement? {
        val service = WorkspaceModelService.getInstance(element.project)
        val rootTurbo = service.rootTurboFile() ?: return null
        return findTaskProperty(rootTurbo, taskKey)
    }
}
