package com.productofamerica.turboassistant.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DependsOnUndefinedTaskInspectionTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    private fun configure(case: String, fileName: String = "turbo.json") {
        myFixture.enableInspections(DependsOnUndefinedTaskInspection::class.java)
        myFixture.copyDirectoryToProject("inspections/dependsOnUndefinedTask/$case", "")
        myFixture.configureFromTempProjectFile(fileName)
    }

    fun testKnownTaskNoWarning() {
        configure("knownTask")
        myFixture.testHighlighting(true, false, true)
    }

    fun testKnownScriptNoWarning() {
        configure("knownScript")
        myFixture.testHighlighting(true, false, true)
    }

    fun testUnknownTaskWarns() {
        configure("unknownTask")
        myFixture.testHighlighting(true, false, true)
    }

    fun testCaretKnownNoWarning() {
        configure("caretKnown")
        myFixture.testHighlighting(true, false, true)
    }

    fun testCaretUnknownWarns() {
        configure("caretUnknown")
        myFixture.testHighlighting(true, false, true)
    }

    fun testPackageHashTaskSkipped() {
        configure("packageHashTask")
        myFixture.testHighlighting(true, false, true)
    }

    fun testEnvVarSkipped() {
        configure("envVar")
        myFixture.testHighlighting(true, false, true)
    }

    fun testJsoncUnknownTaskWarns() {
        configure("unknownTaskJsonc", fileName = "turbo.jsonc")
        myFixture.testHighlighting(true, false, true)
    }

    private fun moveCaretInsideStringLiteral(needle: String) {
        val text = myFixture.editor.document.text
        val offset = text.indexOf(needle)
        check(offset >= 0) { "needle '$needle' not found in editor document" }
        myFixture.editor.caretModel.moveToOffset(offset + 1)
    }

    fun testReplaceWithNearMatchFix() {
        configure("unknownTaskWithNearMatch")
        myFixture.testHighlighting(true, false, true)
        moveCaretInsideStringLiteral("\"buld\"")
        val intention = myFixture.findSingleIntention("Replace with 'build'")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "turbo.json",
            "inspections/dependsOnUndefinedTask/unknownTaskWithNearMatch.after/turbo.json",
            true,
        )
    }

    fun testRemoveEntryFixWhenNoNearMatch() {
        configure("unknownTaskNoNearMatch")
        myFixture.testHighlighting(true, false, true)
        moveCaretInsideStringLiteral("\"completelyUnrelated\"")
        val intention = myFixture.findSingleIntention("Remove dependsOn entry")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "turbo.json",
            "inspections/dependsOnUndefinedTask/unknownTaskNoNearMatch.after/turbo.json",
            true,
        )
    }
}
