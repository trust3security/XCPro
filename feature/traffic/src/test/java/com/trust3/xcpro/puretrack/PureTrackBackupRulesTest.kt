package com.trust3.xcpro.puretrack

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PureTrackBackupRulesTest {

    @Test
    fun androidBackupRulesExcludePureTrackTokenStore() {
        val root = repoRoot()

        assertTrue(
            excludedSharedPrefs(root.resolve("app/src/main/res/xml/backup_rules.xml"))
                .contains(PURETRACK_TOKEN_STORE_FILE)
        )
        assertTrue(
            excludedSharedPrefs(
                root.resolve("app/src/main/res/xml/data_extraction_rules.xml"),
                parentTag = "cloud-backup"
            ).contains(PURETRACK_TOKEN_STORE_FILE)
        )
        assertTrue(
            excludedSharedPrefs(
                root.resolve("app/src/main/res/xml/data_extraction_rules.xml"),
                parentTag = "device-transfer"
            ).contains(PURETRACK_TOKEN_STORE_FILE)
        )
    }

    private fun excludedSharedPrefs(path: Path, parentTag: String? = null): Set<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(path.toFile())
        val parent = parentTag?.let {
            document.getElementsByTagName(it).item(0) as Element
        } ?: document.documentElement
        val excludes = parent.getElementsByTagName("exclude")
        return (0 until excludes.length)
            .map { excludes.item(it) as Element }
            .filter { it.getAttribute("domain") == "sharedpref" }
            .map { it.getAttribute("path") }
            .toSet()
    }

    private fun repoRoot(): Path {
        var current: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))) {
                return current
            }
            current = current.parent
        }
        error("Could not locate repo root")
    }

    private companion object {
        private const val PURETRACK_TOKEN_STORE_FILE = "puretrack_token_store.xml"
    }
}
