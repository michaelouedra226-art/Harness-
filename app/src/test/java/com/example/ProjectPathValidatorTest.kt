package com.example

import com.example.domain.model.ProjectPathValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectPathValidatorTest {
    @Test
    fun normalizesRelativePathSeparators() {
        assertEquals("src/main.kt", ProjectPathValidator.normalizeRelativePath("src\\main.kt"))
        assertEquals("assets/logo.png", ProjectPathValidator.normalizeRelativePath("assets//logo.png"))
    }

    @Test
    fun rejectsAbsoluteAndTraversalPaths() {
        assertNull(ProjectPathValidator.normalizeRelativePath("../secret.txt"))
        assertNull(ProjectPathValidator.normalizeRelativePath("src/../../secret.txt"))
        assertNull(ProjectPathValidator.normalizeRelativePath("/etc/passwd"))
        assertNull(ProjectPathValidator.normalizeRelativePath("C:\\Windows\\system.ini"))
    }
}
