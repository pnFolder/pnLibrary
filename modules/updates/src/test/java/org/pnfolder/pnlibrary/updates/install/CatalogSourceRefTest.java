package org.pnfolder.pnlibrary.updates.install;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.pnfolder.pnlibrary.api.updates.request.CatalogSource;
import org.pnfolder.pnlibrary.api.updates.request.CatalogSource.RefType;

class CatalogSourceRefTest {
    @Test
    @DisplayName("a branch tag and commit each get their own raw address")
    void aBranchTagAndCommitEachGetTheirOwnRawAddress() {
        String base = "https://raw.githubusercontent.com/o/r/";
        assertEquals(base + "refs/heads/dev/.pnlibrary/releases.json", new CatalogSource("o", "r", "dev").getRawUrl());
        assertEquals(base + "refs/tags/v1/.pnlibrary/releases.json",
            new CatalogSource("o", "r", "v1", ".pnlibrary", RefType.TAG).getRawUrl());
        assertEquals(base + "abc1234/.pnlibrary/releases.json",
            new CatalogSource("o", "r", "abc1234", ".pnlibrary", RefType.COMMIT).getRawUrl());
    }

    @Test
    @DisplayName("a commit must look like a hash")
    void aCommitMustLookLikeAHash() {
        assertThrows(IllegalArgumentException.class,
            () -> new CatalogSource("o", "r", "main", ".pnlibrary", RefType.COMMIT));
    }
}
