package com.aryan.reader.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopOpdsRepositoryTest {
    @Test
    fun `desktop repository persists shared opds catalog rules`() = withTempDir { dir ->
        var nextId = 0
        val repository = DesktopOpdsRepository(
            catalogFile = File(dir, "opds_catalogs.json"),
            idFactory = { "catalog-${nextId++}" }
        )

        val defaults = repository.loadCatalogs()
        assertEquals(2, defaults.size)
        assertTrue(defaults.all { it.isDefault })

        repository.addCatalogForTest(" Custom ", " https://example.org/opds ", " user ", " pass ")
        val custom = repository.loadCatalogs().single { !it.isDefault }
        assertEquals("Custom", custom.title)
        assertEquals("https://example.org/opds", custom.url)
        assertEquals("user", custom.username)
        assertEquals("pass", custom.password)
    }

    @Test
    fun `desktop opds http blocks before network in offline flavor`() {
        withSystemProperty(DesktopFlavorProperty, DesktopFlavorOssOffline) {
            assertFailsWith<IllegalStateException> {
                DesktopOpdsHttp.fetchString("https://example.org/opds", null, null)
            }
        }
    }

    private fun DesktopOpdsRepository.addCatalogForTest(
        title: String,
        url: String,
        username: String?,
        password: String?
    ) {
        saveCatalogs(
            com.aryan.reader.shared.opds.SharedOpdsCatalogs.addCatalog(
                catalogs = loadCatalogs(),
                title = title,
                url = url,
                username = username,
                password = password,
                idFactory = { "custom" }
            )
        )
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("reader-desktop-opds").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun withSystemProperty(
        key: String,
        value: String?,
        block: () -> Unit
    ) {
        val previous = System.getProperty(key)
        try {
            if (value == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, value)
            }
            block()
        } finally {
            if (previous == null) {
                System.clearProperty(key)
            } else {
                System.setProperty(key, previous)
            }
        }
    }
}
