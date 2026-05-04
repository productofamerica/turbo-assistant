package com.productofamerica.turboassistant.references

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.json.psi.JsonStringLiteral
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DependsOnReferenceTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    private fun configure(case: String, fileName: String = "turbo.json") {
        myFixture.copyDirectoryToProject("references/$case", "")
        myFixture.configureFromTempProjectFile(fileName)
    }

    private fun moveCaretTo(needle: String, offsetWithinNeedle: Int = 1) {
        val text = myFixture.editor.document.text
        val offset = text.indexOf(needle)
        check(offset >= 0) { "needle '$needle' not found in document" }
        myFixture.editor.caretModel.moveToOffset(offset + offsetWithinNeedle)
    }

    private fun loadPsi(relativePath: String): PsiFile {
        val vf = myFixture.findFileInTempDir(relativePath) ?: error("file '$relativePath' not in temp project")
        return PsiManager.getInstance(project).findFile(vf) ?: error("no PSI for '$relativePath'")
    }

    private fun findTask(turboFile: PsiFile, taskName: String): JsonProperty {
        val root = (turboFile as JsonFile).topLevelValue as JsonObject
        val tasks = root.findProperty("tasks")?.value as JsonObject
        return tasks.findProperty(taskName) ?: error("task '$taskName' not found")
    }

    private fun findScript(packageJsonFile: PsiFile, scriptName: String): JsonProperty {
        val root = (packageJsonFile as JsonFile).topLevelValue as JsonObject
        val scripts = root.findProperty("scripts")?.value as JsonObject
        return scripts.findProperty(scriptName) ?: error("script '$scriptName' not found")
    }

    private fun findPackageNameLiteral(packageJsonFile: PsiFile): JsonStringLiteral {
        val root = (packageJsonFile as JsonFile).topLevelValue as JsonObject
        return root.findProperty("name")?.value as? JsonStringLiteral
            ?: error("name literal not found")
    }

    fun testInFileTaskResolves() {
        configure("dependsOnInFileTask")
        moveCaretTo("\"build\"]", 2)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolved = ref.resolve()
        val expected = findTask(loadPsi("turbo.json"), "build")
        assertSame(expected, resolved)
    }

    fun testPackageScriptFallback() {
        configure("dependsOnPackageScript")
        moveCaretTo("\"lint\"]", 2)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertTrue("expected poly-variant reference", ref is PsiPolyVariantReference)
        val results = (ref as PsiPolyVariantReference).multiResolve(false)
        assertEquals(1, results.size)
        val expected = findScript(loadPsi("packages/web/package.json"), "lint")
        assertSame(expected, results[0].element)
    }

    fun testCaretPrefixStripped() {
        configure("dependsOnCaret")
        moveCaretTo("\"^build\"", 3)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolved = ref.resolve()
        val expected = findTask(loadPsi("turbo.json"), "build")
        assertSame(expected, resolved)
    }

    fun testPackageHashTaskBothHalves() {
        configure("dependsOnPackageHashTask")
        val webPackageJson = loadPsi("packages/web/package.json")

        moveCaretTo("\"web#build\"", 2)
        val pkgRef = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertSame(findPackageNameLiteral(webPackageJson), pkgRef.resolve())

        moveCaretTo("\"web#build\"", 6)
        val taskRef = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertSame(findScript(webPackageJson, "build"), taskRef.resolve())
    }

    fun testUnresolvableReturnsNull() {
        configure("dependsOnUnresolvable")
        moveCaretTo("\"nopeNope\"", 2)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertNull(ref.resolve())
        if (ref is PsiPolyVariantReference) {
            assertEquals(0, ref.multiResolve(false).size)
        }
    }

    fun testJsoncResolves() {
        configure("dependsOnJsonc", fileName = "turbo.jsonc")
        moveCaretTo("\"build\"]", 2)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolved = ref.resolve()
        val expected = findTask(loadPsi("turbo.jsonc"), "build")
        assertSame(expected, resolved)
    }

    fun testRootMarkerHashTaskResolves() {
        configure("dependsOnRootMarker")
        moveCaretTo("[\"//#format-write\"]", 5)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolved = ref.resolve()
        val expected = findTask(loadPsi("turbo.json"), "//#format-write")
        assertSame(expected, resolved)
    }
}
