package com.davidgcd.backlog.data.csv

import com.davidgcd.backlog.data.csv.AutoExportFolder.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoExportFolderTest {
    @Test
    fun `le motif accepte seulement backlog csv et ses copies numerotees`() {
        listOf("backlog.csv", "backlog (1).csv", "backlog (12).csv").forEach { assertTrue(it, AutoExportFolder.isExportName(it)) }
        listOf("backlog.csv.bak", "mon backlog.csv", "backlog (a).csv", "backlog().csv", "Backlog.csv", "backlog (1).txt", "autre.csv")
            .forEach { assertFalse(it, AutoExportFolder.isExportName(it)) }
        assertFalse(AutoExportFolder.isExportName(null))
    }

    @Test
    fun `reutilise le backlog csv exact le plus recent et marque le reste comme doublons`() {
        val vieux = Entry("1", "backlog.csv", 10)
        val recent = Entry("2", "backlog.csv", 30)
        val copie = Entry("3", "backlog (1).csv", 50)
        val plan = AutoExportFolder.plan(listOf(vieux, copie, recent))
        assertEquals(recent, plan.target)
        assertEquals(setOf(vieux, copie), plan.duplicates.toSet())
    }

    @Test
    fun `sans nom exact reutilise la copie la plus recente`() {
        val a = Entry("1", "backlog (1).csv", 10)
        val b = Entry("2", "backlog (2).csv", 20)
        val plan = AutoExportFolder.plan(listOf(a, b))
        assertEquals(b, plan.target)
        assertEquals(listOf(a), plan.duplicates)
    }

    @Test
    fun `dossier sans export ne supprime rien`() {
        val plan = AutoExportFolder.plan(listOf(Entry("1", "photo.jpg", 5), Entry("2", "backlog.csv.bak", 5)))
        assertNull(plan.target)
        assertTrue(plan.duplicates.isEmpty())
    }
}
