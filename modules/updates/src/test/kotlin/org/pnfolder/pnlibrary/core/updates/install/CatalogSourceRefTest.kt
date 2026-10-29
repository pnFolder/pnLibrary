package org.pnfolder.pnlibrary.core.updates.install

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.updates.CatalogSource
import org.pnfolder.pnlibrary.api.updates.CatalogSource.RefType

class CatalogSourceRefTest {
    @Test
    fun `a branch tag and commit each get their own raw address`() {
        val base = "https://raw.githubusercontent.com/o/r/"
        assertEquals(base + "refs/heads/dev/.pnlibrary/releases.json", CatalogSource("o", "r", "dev").rawUrl)
        assertEquals(base + "refs/tags/v1/.pnlibrary/releases.json", CatalogSource("o", "r", "v1", refType = RefType.TAG).rawUrl)
        assertEquals(base + "abc1234/.pnlibrary/releases.json", CatalogSource("o", "r", "abc1234", refType = RefType.COMMIT).rawUrl)
    }

    @Test
    fun `a commit must look like a hash`() {
        assertThrows(IllegalArgumentException::class.java) { CatalogSource("o", "r", "main", refType = RefType.COMMIT) }
    }
}
