package com.scribe.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scribe.app.data.repository.SkillManager

@Composable
fun SkillSelector(
    skills: List<SkillManager.SkillMeta>,
    currentSkillName: String?,
    onSkillSelected: (String) -> Unit,
    onDeleteSkill: (String) -> Unit,
    onImportSkill: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.padding(horizontal = 8.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (currentSkillName != null) "角色: $currentSkillName" else "选择角色 Skill",
                maxLines = 1
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("无 (默认助手)") },
                onClick = {
                    expanded = false
                    onSkillSelected("")
                }
            )
            HorizontalDivider()
            skills.forEach { skill ->
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(skill.name, style = MaterialTheme.typography.bodyMedium)
                                Text(skill.id, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                            IconButton(
                                onClick = {
                                    expanded = false
                                    showDeleteConfirm = skill.id
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete, "删除",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSkillSelected(skill.id)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, "导入", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("导入新 Skill")
                    }
                },
                onClick = {
                    expanded = false
                    onImportSkill()
                }
            )
        }
    }

    // delete confirmation dialog
    showDeleteConfirm?.let { skillId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除 skill \"$skillId\" 吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSkill(skillId)
                    showDeleteConfirm = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }
}
