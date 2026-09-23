package ru.privatenull.pnlibrary.update

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.platform.PlatformType

class ProductDescriptorCodecTest {
    private val codec = ProductDescriptorCodec()

    @Test fun `decodes complete release with two Java artifacts`() {
        val release = codec.decodeRelease("""{
          "schema":1,"component":"economy","version":"3.4.0","channel":"stable",
          "pnLibraryApi":{"minimum":2,"maximum":3},
          "dependencies":[{"component":"permissions","minimumVersion":"2.1.0"}],
          "artifacts":[
            {"platform":"bukkit","java":{"minimum":8,"maximum":16},"file":"economy-java8.jar","size":10,"sha256":"${"01".repeat(32)}"},
            {"platform":"bukkit","java":{"minimum":17},"file":"economy-java17.jar","size":20,"sha256":"${"02".repeat(32)}"}
          ]
        }""".toByteArray())
        assertEquals("economy", release.product.value)
        assertEquals(2, release.supportedApi.minimum)
        assertEquals("permissions", release.dependencies.single().product.value)
        assertEquals(listOf(8, 17), release.artifacts.map { it.minimumJava })
        assertEquals(PlatformType.BUKKIT, release.artifacts.last().platform)
    }

    @Test fun `rejects unsupported schema and unsafe release fields`() {
        assertEquals("schema", assertThrows(ManifestException::class.java) {
            codec.decodeRelease("""{"schema":2}""".toByteArray())
        }.field)
        val base = """{"schema":1,"component":"economy","version":"3.4.0","channel":"stable","pnLibraryApi":{"minimum":1,"maximum":1},"dependencies":[],"artifacts":[%s]}"""
        listOf(
            """{"platform":"bukkit","java":{"minimum":8},"file":"../bad.jar","size":1,"sha256":"${"00".repeat(32)}"}""",
            """{"platform":"bukkit","java":{"minimum":8},"file":"ok.jar","size":-1,"sha256":"${"00".repeat(32)}"}""",
            """{"platform":"bukkit","java":{"minimum":8},"file":"ok.jar","size":1,"sha256":"00"}""",
        ).forEach { artifact -> assertThrows(ManifestException::class.java) { codec.decodeRelease(base.format(artifact).toByteArray()) } }
    }

    @Test fun `rejects duplicate release dependencies`() {
        val json = """{"schema":1,"component":"economy","version":"3.4.0","channel":"stable","pnLibraryApi":{"minimum":1,"maximum":1},"dependencies":[{"component":"permissions","minimumVersion":"1.0.0"},{"component":"permissions","minimumVersion":"2.0.0"}],"artifacts":[]}"""
        assertEquals("dependencies", assertThrows(ManifestException::class.java) {
            codec.decodeRelease(json.toByteArray())
        }.field)
    }
}
