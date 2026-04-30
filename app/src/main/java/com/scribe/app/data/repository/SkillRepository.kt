package com.scribe.app.data.repository

import android.content.Context
import android.net.Uri
import com.scribe.app.data.model.Skill

class SkillRepository(context: Context) {

    val manager = SkillManager(context)

    fun init() { manager.ensureBuiltInSkills() }

    fun getSkillIds(): List<String> = manager.getSkillIds()

    fun getSkillMetas(): List<SkillManager.SkillMeta> = manager.getSkillMetas()

    fun loadSkill(skillId: String): Skill? = manager.loadSkill(skillId)

    fun deleteSkill(skillId: String): Boolean = manager.deleteSkill(skillId)

    fun importSkill(content: String): SkillManager.SkillMeta? = manager.importSkill(content)

    fun importSkillFromZip(uri: Uri): SkillManager.SkillMeta? = manager.importSkillFromZip(uri)
}
