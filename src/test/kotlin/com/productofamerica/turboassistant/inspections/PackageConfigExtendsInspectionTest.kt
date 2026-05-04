package com.productofamerica.turboassistant.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PackageConfigExtendsInspectionTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    private fun configure(case: String, fileName: String = "packages/foo/turbo.json") {
        myFixture.enableInspections(PackageConfigExtendsInspection::class.java)
        myFixture.copyDirectoryToProject("inspections/packageConfigExtends/$case", "")
        myFixture.configureFromTempProjectFile(fileName)
    }

    private fun moveCaretTo(needle: String) {
        val text = myFixture.editor.document.text
        val offset = text.indexOf(needle)
        check(offset >= 0) { "needle '$needle' not found in editor document" }
        myFixture.editor.caretModel.moveToOffset(offset + 1)
    }

    fun testCorrectExtendsInPackageNoWarning() {
        configure("correctExtendsInPackage")
        myFixture.testHighlighting(true, false, true)
    }

    fun testRootWithoutExtendsNoWarning() {
        configure("rootWithoutExtends", fileName = "turbo.json")
        myFixture.testHighlighting(true, false, true)
    }

    fun testRootWithExtendsNoWarning() {
        configure("rootWithExtends", fileName = "turbo.json")
        myFixture.testHighlighting(true, false, true)
    }

    fun testMissingExtendsWarnsAndFixInserts() {
        configure("missingExtendsInPackage")
        myFixture.testHighlighting(true, false, true)
        moveCaretTo("{")
        val intention = myFixture.findSingleIntention("Add extends field")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "packages/foo/turbo.json",
            "inspections/packageConfigExtends/missingExtendsInPackage.after/packages/foo/turbo.json",
            true,
        )
    }

    fun testExtendsExtraEntryWarnsAndFixReplaces() {
        configure("extendsExtraEntry")
        myFixture.testHighlighting(true, false, true)
        moveCaretTo("[\"//\", \"../base\"]")
        val intention = myFixture.findSingleIntention("Set extends to [\"//\"]")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "packages/foo/turbo.json",
            "inspections/packageConfigExtends/extendsExtraEntry.after/packages/foo/turbo.json",
            true,
        )
    }

    fun testExtendsEmptyArrayWarnsAndFixReplaces() {
        configure("extendsEmptyArray")
        myFixture.testHighlighting(true, false, true)
        moveCaretTo("[]")
        val intention = myFixture.findSingleIntention("Set extends to [\"//\"]")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "packages/foo/turbo.json",
            "inspections/packageConfigExtends/extendsEmptyArray.after/packages/foo/turbo.json",
            true,
        )
    }

    fun testExtendsStringWarnsAndFixReplaces() {
        configure("extendsString")
        myFixture.testHighlighting(true, false, true)
        moveCaretTo("\"//\"")
        val intention = myFixture.findSingleIntention("Set extends to [\"//\"]")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "packages/foo/turbo.json",
            "inspections/packageConfigExtends/extendsString.after/packages/foo/turbo.json",
            true,
        )
    }

    fun testExtendsObjectWarnsAndFixReplaces() {
        configure("extendsObject")
        myFixture.testHighlighting(true, false, true)
        moveCaretTo("{}")
        val intention = myFixture.findSingleIntention("Set extends to [\"//\"]")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "packages/foo/turbo.json",
            "inspections/packageConfigExtends/extendsObject.after/packages/foo/turbo.json",
            true,
        )
    }

    fun testJsoncMissingInPackageWarnsAndFixInserts() {
        configure("jsoncMissingInPackage", fileName = "packages/foo/turbo.jsonc")
        myFixture.testHighlighting(true, false, true)
        moveCaretTo("{")
        val intention = myFixture.findSingleIntention("Add extends field")
        myFixture.launchAction(intention)
        myFixture.checkResultByFile(
            "packages/foo/turbo.jsonc",
            "inspections/packageConfigExtends/jsoncMissingInPackage.after/packages/foo/turbo.jsonc",
            true,
        )
    }
}
