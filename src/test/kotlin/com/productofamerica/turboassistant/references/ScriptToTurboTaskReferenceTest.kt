package com.productofamerica.turboassistant.references

import com.intellij.json.psi.JsonFile
import com.intellij.json.psi.JsonObject
import com.intellij.json.psi.JsonProperty
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ScriptToTurboTaskReferenceTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    private fun configure(case: String, fileName: String = "package.json") {
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

    fun testScriptResolvesToTurboTask() {
        configure("scriptToTask")
        moveCaretTo("\"lint\":", 2)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        val resolved = ref.resolve()
        val expected = findTask(loadPsi("turbo.json"), "lint")
        assertSame(expected, resolved)
    }

    fun testUnresolvableReturnsNull() {
        configure("scriptToTaskUnresolvable")
        moveCaretTo("\"lint\":", 2)
        val ref = myFixture.getReferenceAtCaretPositionWithAssertion()
        assertNull(ref.resolve())
    }
}
