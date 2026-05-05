package com.scribe.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.scribe.app.data.model.MessageRole

@Composable
fun ChatBubble(
    role: MessageRole,
    content: String,
    reasoning: String = "",
    reasoningExpanded: Boolean = true,
    onToggleReasoning: () -> Unit = {},
    onCopy: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    incomplete: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (role == MessageRole.SYSTEM) return
    if (content.isBlank() && reasoning.isBlank()) return

    val isUser = role == MessageRole.USER
    var showMenu by remember { mutableStateOf(false) }
    val hasMenu = onCopy != null || onShare != null || onEdit != null || onDelete != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .then(
                    if (hasMenu) {
                        Modifier.pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showMenu = true })
                        }
                    } else Modifier
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "自动手记人偶",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    if (!isUser && onRegenerate != null) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重新生成",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                // Collapsible reasoning section (assistant only)
                if (!isUser && reasoning.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable { onToggleReasoning() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "思考过程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = if (reasoningExpanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (reasoningExpanded) "折叠" else "展开",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    AnimatedVisibility(
                        visible = reasoningExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Text(
                            text = reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }

                // Main content
                if (content.isNotBlank()) {
                    if (!(!isUser && reasoning.isNotBlank())) {
                        Spacer(modifier = Modifier.height(4.dp))
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Default,
                            fontStyle = if (incomplete && !isUser) FontStyle.Italic else FontStyle.Normal
                        ),
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary
                        else if (incomplete) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (incomplete && !isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "生成中断",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (onCopy != null) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = { showMenu = false; onCopy() }
                    )
                }
                if (isUser) {
                    if (onEdit != null) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = { showMenu = false; onEdit() }
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("撤回", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                } else {
                    if (onShare != null) {
                        DropdownMenuItem(
                            text = { Text("分享") },
                            onClick = { showMenu = false; onShare() }
                        )
                    }
                }
            }
        }
    }
}
