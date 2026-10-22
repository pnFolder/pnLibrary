package org.pnfolder.pnlibrary.api.architecture

import org.jetbrains.kotlin.com.intellij.lang.java.lexer.JavaLexer
import org.jetbrains.kotlin.com.intellij.lexer.Lexer
import org.jetbrains.kotlin.com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.com.intellij.psi.JavaTokenType
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

/** Finds static platform references without treating comments or literal text as dependencies. */
internal object PlatformSourceReferences {
    val packages = mapOf(
        "bukkit" to listOf(
            "org.pnfolder.pnlibrary.bukkit", "org.bukkit", "org.spigotmc",
            "io.papermc.paper", "com.destroystokyo.paper",
        ),
        "bungee" to listOf("org.pnfolder.pnlibrary.bungee", "net.md_5.bungee"),
        "velocity" to listOf("org.pnfolder.pnlibrary.velocity", "com.velocitypowered"),
    )

    fun find(
        source: String,
        javaSource: Boolean,
        forbiddenPackages: Collection<String> = packages.values.flatten(),
    ): Set<String> {
        val lexer: Lexer = if (javaSource) JavaLexer(LanguageLevel.HIGHEST) else KotlinLexer()
        val references = linkedSetOf<String>()
        val name = StringBuilder()
        var expectsIdentifier = true

        fun finish() {
            val reference = name.toString().trimEnd('.')
            if (forbiddenPackages.any { reference == it || reference.startsWith("$it.") }) {
                references += reference
            }
            name.setLength(0)
            expectsIdentifier = true
        }

        lexer.start(source)
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            val text = lexer.tokenText
            when {
                text.isBlank() || text.startsWith("//") || text.startsWith("/*") -> Unit
                type == KtTokens.IDENTIFIER || type == JavaTokenType.IDENTIFIER -> {
                    if (!expectsIdentifier) finish()
                    name.append(text.removeSurrounding("`"))
                    expectsIdentifier = false
                }
                type == KtTokens.DOT || type == JavaTokenType.DOT -> {
                    if (!expectsIdentifier) {
                        name.append('.')
                        expectsIdentifier = true
                    } else finish()
                }
                else -> finish()
            }
            lexer.advance()
        }
        finish()
        return references
    }
}
