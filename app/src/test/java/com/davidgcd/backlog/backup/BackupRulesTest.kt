package com.davidgcd.backlog.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Keeps the Android 6-11 and 12+ backup rule files in phase, and the sensitive/unrestorable data out. */
class BackupRulesTest {

    private fun includes(file: String, parent: String? = null): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val scope = if (parent == null) doc.documentElement else doc.getElementsByTagName(parent).item(0) as org.w3c.dom.Element
        val nodes = scope.getElementsByTagName("include")
        return (0 until nodes.length).map {
            val e = nodes.item(it) as org.w3c.dom.Element
            "${e.getAttribute("domain")}:${e.getAttribute("path")}"
        }.toSet()
    }

    @Test
    fun `cloud backup, device transfer and legacy rules save the same files`() {
        val legacy = includes("backup_rules.xml")
        assertEquals(legacy, includes("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(legacy, includes("data_extraction_rules.xml", "device-transfer"))
    }

    @Test
    fun `library database and settings are backed up`() {
        val rules = includes("backup_rules.xml")
        assertTrue("database:backlog.db" in rules)
        assertTrue("file:datastore/notification_prefs.preferences_pb" in rules)
        assertTrue("file:datastore/library_accounts.preferences_pb" in rules)
    }

    @Test
    fun `auto export folder is not restored because its SAF permission cannot follow`() {
        assertFalse(includes("backup_rules.xml").any { "auto_export" in it })
    }

    @Test
    fun `manifest wires the rules and the agent`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("@xml/backup_rules" in manifest)
        assertTrue("@xml/data_extraction_rules" in manifest)
        assertTrue(".BacklogBackupAgent" in manifest)
    }
}
