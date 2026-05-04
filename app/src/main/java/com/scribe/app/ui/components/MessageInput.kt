package com.scribe.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("输入消息...") },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            maxLines = 4
        )

        Spacer(modifier = Modifier.width(8.dp))

        FilledIconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text.trim())
                    onTextChange("")
                }
            },
            enabled = enabled && text.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
