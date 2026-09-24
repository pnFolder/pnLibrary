package ru.privatenull.pnlibrary.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ComponentMetadataWriterTest {
    @TempDir lateinit var temporary: Path

    @Test
    fun `writes canonical runtime descriptor`() {
        val file = ComponentMetadataWriter.write(temporary, ComponentMetadata("pncases", "2.4.0", 1, 2))
        assertEquals(temporary.resolve("META-INF/pnlibrary/component.json"), file)
        assertEquals(
            """{
  "schema": 1,
  "component": "pncases",
  "version": "2.4.0",
  "pnLibraryApi": {
    "minimum": 1,
    "maximum": 2
  }
}
""",
            file.toFile().readText(),
        )
    }

    @Test
    fun `rejects invalid identity and API range`() {
        assertThrows(IllegalArgumentException::class.java) { ComponentMetadata("PN Cases", "1.0.0", 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { ComponentMetadata("pncases", "1.0.0", 2, 1) }
    }
}
