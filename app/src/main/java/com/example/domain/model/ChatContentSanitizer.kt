package com.example.domain.model

/** Keeps source code in the project workspace instead of exposing it in chat bubbles. */
object ChatContentSanitizer {
    private val actionBlock = Regex("<<<ACTION:CREATE_FILE\\s+path=\"[^\"]+\">>>[\\s\\S]*?<<<END_ACTION>>>")
    private val legacyFileBlock = Regex("FILE:\\s*[^\\r\\n]+[\\r\\n]+[\\s\\S]*?(?:END_FILE|(?=FILE:|$))")
    private val fencedCodeBlock = Regex("(?m)^```[^\\r\\n]*\\r?\\n[\\s\\S]*?^```\\s*$")
    private val appControlAction = Regex("<<<ACTION:(?:OPEN_FILE\\s+path=\"[^\"]+\"|OPEN_SCREEN\\s+screen=\"[^\"]+\")>>>")
    private val codeLine = Regex("(?i)^\\s*(package\\s+|import\\s+|fun\\s+|class\\s+|interface\\s+|object\\s+|def\\s+|function\\s+|const\\s+|let\\s+|var\\s+|public\\s+class|<!doctype|</?(html|head|body|main|div|style|script)\\b|export\\s+default|\"[^\"\\r\\n]+\"\\s*:)")

    fun sanitize(raw: String, filesWereSaved: Boolean = false): String {
        var removedCode = false
        var result = actionBlock.replace(raw) {
            removedCode = true
            "\n"
        }
        result = legacyFileBlock.replace(result) {
            removedCode = true
            "\\n"
        }
        result = appControlAction.replace(result, "\\n")
        result = fencedCodeBlock.replace(result) {
            removedCode = true
            "\n"
        }.trim()

        val codeLikeLines = result.lineSequence().count { codeLine.containsMatchIn(it) }
        val compactCodeLine = codeLikeLines == 1 && result.lines().size <= 3 && Regex("[=({;<>]").containsMatchIn(result)
        if (codeLikeLines >= 2 || compactCodeLine || (result.contains("{") && result.contains(";") && result.lines().size >= 4)) {
            removedCode = true
            result = ""
        }

        val notice = when {
            !removedCode -> ""
            filesWereSaved -> "Le code source est disponible dans **Nuru Code > Fichiers**."
            else -> "Le code n’est pas affiché dans la discussion. Demandez à l’assistant de créer ou modifier les fichiers pour le consulter dans **Nuru Code > Fichiers**."
        }
        return listOf(result, notice).filter { it.isNotBlank() }.joinToString("\n\n")
    }
}
