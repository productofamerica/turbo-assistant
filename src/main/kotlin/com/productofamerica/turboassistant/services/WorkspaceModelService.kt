package com.productofamerica.turboassistant.services

import com.intellij.javascript.nodejs.packageJson.PackageJsonFileManager
import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

@Service(Service.Level.PROJECT)
class WorkspaceModelService(private val project: Project) {

    val taskNames: Set<String>
        get() = CachedValuesManager.getManager(project).getCachedValue(
            project, TASK_NAMES_KEY,
            {
                CachedValueProvider.Result.create(
                    computeTaskNames(),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    PackageJsonFileManager.getInstance(project).modificationTracker,
                )
            },
            false,
        )

    val packageNames: Set<String>
        get() = CachedValuesManager.getManager(project).getCachedValue(
            project, PACKAGE_NAMES_KEY,
            {
                CachedValueProvider.Result.create(
                    computePackageNames(),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    PackageJsonFileManager.getInstance(project).modificationTracker,
                )
            },
            false,
        )

    private val packageJsonFiles: Set<VirtualFile>
        get() = CachedValuesManager.getManager(project).getCachedValue(
            project, PACKAGE_JSON_FILES_KEY,
            {
                CachedValueProvider.Result.create(
                    PackageJsonFileManager.getInstance(project).validPackageJsonFiles.toSet(),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    PackageJsonFileManager.getInstance(project).modificationTracker,
                )
            },
            false,
        )

    private fun monorepoRoot(): VirtualFile? =
        packageJsonFiles.minByOrNull { it.path }?.parent

    fun isPackageConfig(file: VirtualFile): Boolean {
        if (file.name != "turbo.json" && file.name != "turbo.jsonc") return false
        val baseDir = monorepoRoot() ?: return false
        val parent = file.parent ?: return false
        if (parent == baseDir) return false
        if (!VfsUtilCore.isAncestor(baseDir, file, true)) return false
        val sibling = parent.findChild("package.json") ?: return false
        return sibling in packageJsonFiles
    }

    fun rootTurboFile(): PsiFile? {
        val baseDir: VirtualFile = monorepoRoot() ?: return null
        val vf = baseDir.findChild("turbo.json") ?: baseDir.findChild("turbo.jsonc") ?: return null
        return PsiManager.getInstance(project).findFile(vf)
    }

    fun packageJsonPsiFiles(): List<PsiFile> {
        val pm = PsiManager.getInstance(project)
        return packageJsonFiles.mapNotNull { pm.findFile(it) }
    }

    fun findPackageJsonByName(name: String): PsiFile? {
        if (name.isEmpty()) return null
        for (psi in packageJsonPsiFiles()) {
            if (packageNameIn(psi) == name) return psi
        }
        return null
    }

    private fun computeTaskNames(): Set<String> {
        val result = mutableSetOf<String>()
        rootTurboFile()?.let { result += taskNamesIn(it) }
        for (vf in PackageJsonFileManager.getInstance(project).validPackageJsonFiles) {
            val psi = PsiManager.getInstance(project).findFile(vf) as? JsonFile ?: continue
            result += scriptNamesIn(psi)
        }
        return result
    }

    private fun computePackageNames(): Set<String> {
        val result = mutableSetOf<String>()
        for (vf in PackageJsonFileManager.getInstance(project).validPackageJsonFiles) {
            val psi = PsiManager.getInstance(project).findFile(vf) as? JsonFile ?: continue
            packageNameIn(psi)?.let { result += it }
        }
        return result
    }

    private fun taskNamesIn(file: PsiFile): Set<String> =
        CachedValuesManager.getProjectPsiDependentCache(file) { f ->
            val root = (f as? JsonFile)?.topLevelValue as? JsonObject
                ?: return@getProjectPsiDependentCache emptySet()
            val tasks = root.findProperty("tasks")?.value as? JsonObject
                ?: return@getProjectPsiDependentCache emptySet()
            tasks.propertyList.mapTo(mutableSetOf()) { it.name }
        }

    private fun scriptNamesIn(file: PsiFile): Set<String> =
        CachedValuesManager.getProjectPsiDependentCache(file) { f ->
            val root = (f as? JsonFile)?.topLevelValue as? JsonObject
                ?: return@getProjectPsiDependentCache emptySet()
            val scripts = root.findProperty("scripts")?.value as? JsonObject
                ?: return@getProjectPsiDependentCache emptySet()
            scripts.propertyList.mapTo(mutableSetOf()) { it.name }
        }

    private fun packageNameIn(file: PsiFile): String? =
        CachedValuesManager.getProjectPsiDependentCache(file) { f ->
            val root = (f as? JsonFile)?.topLevelValue as? JsonObject
                ?: return@getProjectPsiDependentCache null
            val nameProp = root.findProperty("name")?.value as? JsonStringLiteral
                ?: return@getProjectPsiDependentCache null
            nameProp.value.takeIf { it.isNotBlank() }
        }

    companion object {
        private val TASK_NAMES_KEY: Key<CachedValue<Set<String>>> =
            Key.create("turbo.workspaceModelService.taskNames")
        private val PACKAGE_NAMES_KEY: Key<CachedValue<Set<String>>> =
            Key.create("turbo.workspaceModelService.packageNames")
        private val PACKAGE_JSON_FILES_KEY: Key<CachedValue<Set<VirtualFile>>> =
            Key.create("turbo.workspaceModelService.packageJsonFiles")

        fun getInstance(project: Project): WorkspaceModelService = project.service()
    }
}
