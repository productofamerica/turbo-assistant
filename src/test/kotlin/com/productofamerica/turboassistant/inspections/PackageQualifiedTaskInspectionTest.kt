package com.productofamerica.turboassistant.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PackageQualifiedTaskInspectionTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String = "src/test/testData"

    private fun configure(case: String, fileName: String = "turbo.json") {
        myFixture.enableInspections(PackageQualifiedTaskInspection::class.java)
        myFixture.copyDirectoryToProject("inspections/packageQualifiedTask/$case", "")
        myFixture.configureFromTempProjectFile(fileName)
    }

    fun testKnownPackageInDependsOnNoWarning() {
        configure("knownPackageInDependsOn")
        myFixture.testHighlighting(true, false, true)
    }

    fun testUnknownPackageInDependsOnWarns() {
        configure("unknownPackageInDependsOn")
        myFixture.testHighlighting(true, false, true)
    }

    fun testUnknownPackageInDependsOnArrayWarns() {
        configure("unknownPackageInDependsOnArray")
        myFixture.testHighlighting(true, false, true)
    }

    fun testKnownPackageAsTasksKeyNoWarning() {
        configure("knownPackageAsTasksKey")
        myFixture.testHighlighting(true, false, true)
    }

    fun testUnknownPackageAsTasksKeyWarns() {
        configure("unknownPackageAsTasksKey")
        myFixture.testHighlighting(true, false, true)
    }

    fun testJsoncUnknownPackageWarns() {
        configure("unknownPackageJsonc", fileName = "turbo.jsonc")
        myFixture.testHighlighting(true, false, true)
    }

    fun testNoHashSkipped() {
        configure("noHashSkipped")
        myFixture.testHighlighting(true, false, true)
    }

    fun testEnvVarSkipped() {
        configure("envVarSkipped")
        myFixture.testHighlighting(true, false, true)
    }

    fun testRootMarkerInTasksKeyNoWarning() {
        configure("rootMarkerInTasksKey")
        myFixture.testHighlighting(true, false, true)
    }

    fun testRootMarkerInDependsOnNoWarning() {
        configure("rootMarkerInDependsOn")
        myFixture.testHighlighting(true, false, true)
    }
}
