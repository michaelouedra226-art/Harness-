package com.example

import com.example.domain.model.ChatContentSanitizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatContentSanitizerTest {
    @Test
    fun hidesActionPayloadAndPointsToProjectFiles() {
        val raw = "Voici la page.\n<<<ACTION:CREATE_FILE path=\"index.html\">>>\n<h1>Bonjour</h1>\n<<<END_ACTION>>>"
        val visible = ChatContentSanitizer.sanitize(raw, filesWereSaved = true)
        assertFalse(visible.contains("<h1>Bonjour</h1>"))
        assertTrue(visible.contains("Nuru Code > Fichiers"))
    }

    @Test
    fun hidesMarkdownCodeAndKeepsExplanatoryText() {
        val raw = "Voici une fonction :\n```kotlin\nfun answer() = 42\n```"
        val visible = ChatContentSanitizer.sanitize(raw)
        assertTrue(visible.contains("Voici une fonction"))
        assertFalse(visible.contains("fun answer"))
    }

    @Test
    fun hidesUntaggedSourceCode() {
        val visible = ChatContentSanitizer.sanitize("fun answer() = 42")
        assertFalse(visible.contains("fun answer"))
        assertTrue(visible.contains("code n’est pas affiché"))
    }

    @Test
    fun hidesAppControlMarkers() {
        val visible = ChatContentSanitizer.sanitize("J’ouvre le projet. <<<ACTION:OPEN_SCREEN screen=\"projects\">>>")
        assertTrue(visible.contains("J’ouvre le projet."))
        assertFalse(visible.contains("<<<ACTION:"))
    }
}
