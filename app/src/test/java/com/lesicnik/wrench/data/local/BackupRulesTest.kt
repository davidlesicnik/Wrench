package com.lesicnik.wrench.data.local

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BackupRulesTest {
    @Test
    fun `credentials and all database files are excluded from every backup transport`() {
        for ((file, sections) in mapOf(
            "backup_rules.xml" to listOf("full-backup-content"),
            "data_extraction_rules.xml" to listOf("cloud-backup", "device-transfer")
        )) {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src/main/res/xml/$file"))
            for (section in sections) {
                val exclusions = (document.getElementsByTagName(section).item(0) as Element)
                    .getElementsByTagName("exclude")
                val rules = (0 until exclusions.length).map {
                    val element = exclusions.item(it) as Element
                    element.getAttribute("domain") to element.getAttribute("path")
                }
                assertTrue("$file $section database", "database" to "." in rules)
                assertTrue("$file $section credentials", "sharedpref" to "wrench_secure_prefs.xml" in rules)
            }
        }
    }
}
