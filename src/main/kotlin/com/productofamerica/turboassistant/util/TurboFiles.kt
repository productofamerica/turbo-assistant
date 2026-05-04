package com.productofamerica.turboassistant.util

import com.intellij.psi.PsiFile

fun isTurboFile(file: PsiFile): Boolean =
    file.name == "turbo.json" || file.name == "turbo.jsonc"

fun isPackageJsonFile(file: PsiFile): Boolean =
    file.name == "package.json"
