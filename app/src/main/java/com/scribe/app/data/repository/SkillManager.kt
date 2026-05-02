package com.scribe.app.data.repository

import android.content.Context
import android.net.Uri
import com.scribe.app.data.model.Skill
import com.scribe.app.data.model.SkillAppendix
import com.scribe.app.data.model.SkillFrontmatter
import java.io.File
import java.util.zip.ZipInputStream

class SkillManager(private val context: Context) {

    companion object {
        const val BUNDLED_SKILLS_VERSION = 1
    }

    data class SkillMeta(
        val id: String,
        val name: String,
        val description: String
    )

    private val skillsDir: File
        get() = File(context.filesDir, "skills").also { it.mkdirs() }

    /**
     * Copy built-in skills from assets to internal storage.
     * @param storedVersion previously persisted bundled skills version (0 if never installed)
     * @return new version to persist if skills were updated, or storedVersion if unchanged
     */
    fun ensureBuiltInSkills(storedVersion: Int): Int {
        val assetsSkills = try {
            context.assets.list("skills") ?: emptyArray()
        } catch (_: Exception) { emptyArray() }

        if (assetsSkills.isEmpty()) return storedVersion

        // Clean up stale temp dirs from any prior interrupted overwrite
        skillsDir.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach { it.deleteRecursively() }

        if (storedVersion >= BUNDLED_SKILLS_VERSION) {
            // Version current — only fill missing or broken skill dirs
            for (skillId in assetsSkills) {
                try {
                    val destDir = File(skillsDir, skillId)
                    if (File(destDir, "SKILL.md").exists()) continue
                    if (destDir.exists()) destDir.deleteRecursively()
                    copySkillFromAssets(skillId, destDir)
                } catch (_: Exception) {} // one broken asset shouldn't crash the app
            }
        } else {
            // Bundled skills updated — overwrite each via temp dir for atomicity
            for (skillId in assetsSkills) {
                val destDir = File(skillsDir, skillId)
                val tmpDir = File(skillsDir, "$skillId.tmp")
                try {
                    if (tmpDir.exists()) tmpDir.deleteRecursively()
                    copySkillFromAssets(skillId, tmpDir)
                    if (destDir.exists()) destDir.deleteRecursively()
                    if (!tmpDir.renameTo(destDir)) {
                        tmpDir.deleteRecursively()
                    }
                } catch (_: Exception) {
                    tmpDir.deleteRecursively()
                }
            }
        }

        return BUNDLED_SKILLS_VERSION
    }

    private fun copySkillFromAssets(skillId: String, destDir: File) {
        destDir.mkdirs()
        val skillContent = context.assets.open("skills/$skillId/SKILL.md")
            .bufferedReader().use { it.readText() }
        File(destDir, "SKILL.md").writeText(skillContent)

        for (appendixDir in listOf("references", "appendices")) {
            try {
                val files = context.assets.list("skills/$skillId/$appendixDir") ?: continue
                val appendixDest = File(destDir, appendixDir)
                appendixDest.mkdirs()
                for (file in files) {
                    val content = context.assets.open("skills/$skillId/$appendixDir/$file")
                        .bufferedReader().use { it.readText() }
                    File(appendixDest, file).writeText(content)
                }
            } catch (_: Exception) {}
        }
    }

    fun getSkillIds(): List<String> {
        return skillsDir.listFiles()
            ?.filter { it.isDirectory && File(it, "SKILL.md").exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun getSkillMetas(): List<SkillMeta> {
        return getSkillIds().map { id ->
            val skill = loadSkill(id)
            SkillMeta(
                id = id,
                name = skill?.name ?: id,
                description = skill?.description ?: "(无描述)"
            )
        }
    }

    fun loadSkill(skillId: String): Skill? {
        val skillDir = File(skillsDir, skillId)
        val skillFile = File(skillDir, "SKILL.md")
        if (!skillFile.exists()) return null

        return try {
            val content = skillFile.readText()
            val parsed = parseSkillFile(content)
            val appendices = loadAppendices(skillDir)

            Skill(
                id = skillId,
                name = parsed.first?.name ?: skillId,
                description = parsed.first?.description ?: "(无描述)",
                body = parsed.second,
                appendices = appendices
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deleteSkill(skillId: String): Boolean {
        val skillDir = File(skillsDir, skillId)
        if (!skillDir.exists()) return false
        return skillDir.deleteRecursively()
    }

    fun importSkill(skillContent: String): SkillMeta? {
        val parsed = parseSkillFile(skillContent)
        val name = parsed.first?.name ?: return null

        val skillId = deriveSkillId(name)
        val skillDir = resolveSkillDir(skillId)

        skillDir.mkdirs()
        File(skillDir, "SKILL.md").writeText(skillContent)
        return SkillMeta(skillDir.name, name, parsed.first?.description ?: "(无描述)")
    }

    fun importSkillFromZip(uri: Uri): SkillMeta? {
        val tempDir = File(context.cacheDir, "skill_import_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        try {
            // 1. Extract zip to temp directory
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val targetFile = File(tempDir, entry.name)
                        if (targetFile.canonicalPath.startsWith(tempDir.canonicalPath)) {
                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                            } else {
                                targetFile.parentFile?.mkdirs()
                                targetFile.outputStream().use { fos -> zis.copyTo(fos) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return null

            // 2. Find SKILL.md anywhere in the extracted tree
            val skillMdFile = tempDir.walkTopDown().find { it.name == "SKILL.md" } ?: return null
            val skillContent = skillMdFile.readText()

            val parsed = parseSkillFile(skillContent)
            val name = parsed.first?.name ?: return null

            val skillId = deriveSkillId(name)
            val skillDir = resolveSkillDir(skillId)

            // 3. Copy the skill root (parent of SKILL.md) recursively
            val sourceRoot = skillMdFile.parentFile ?: return null
            sourceRoot.copyRecursively(skillDir, overwrite = true)

            return SkillMeta(skillDir.name, name, parsed.first?.description ?: "(无描述)")
        } catch (_: Exception) {
            return null
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun deriveSkillId(name: String): String = name.lowercase()
        .replace(Regex("[^a-z0-9\\u4e00-\\u9fff-]"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "imported-${System.currentTimeMillis()}" }

    private fun resolveSkillDir(skillId: String): File {
        val dir = File(skillsDir, skillId)
        if (dir.exists()) {
            return File(skillsDir, "$skillId-${System.currentTimeMillis()}")
        }
        return dir
    }

    private fun parseSkillFile(content: String): Pair<SkillFrontmatter?, String> {
        if (!content.startsWith("---")) {
            return Pair(null, content)
        }

        val endOfHeader = content.indexOf("---", 3)
        if (endOfHeader == -1) {
            return Pair(null, content)
        }

        val header = content.substring(3, endOfHeader).trim()
        val body = content.substring(endOfHeader + 3).trim()

        val name = Regex("""(?m)^name:\s*(.+)""").find(header)?.groupValues?.get(1)?.trim()
        val description = Regex("""(?m)^description:\s*(.+)""").find(header)?.groupValues?.get(1)?.trim()

        return Pair(SkillFrontmatter(name = name ?: "", description = description ?: ""), body)
    }

    private fun loadAppendices(skillDir: File): List<SkillAppendix> {
        val result = mutableListOf<SkillAppendix>()
        val appendixDirs = listOf("references", "appendices")

        for (dirName in appendixDirs) {
            val dir = File(skillDir, dirName)
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".md") }?.sortedBy { it.name }
                ?: continue
            for (file in files) {
                try {
                    val content = file.readText()
                    if (content.isNotBlank()) {
                        result.add(SkillAppendix(label = "$dirName/${file.name}", content = content))
                    }
                } catch (_: Exception) {}
            }
        }

        return result
    }
}
