package com.scribe.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scribe.app.data.model.Provider
import com.scribe.app.viewmodel.SettingsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onSetProvider: (Provider) -> Unit,
    onSaveOpenAI: (url: String, key: String, model: String) -> Unit,
    onSaveAnthropic: (url: String, key: String, model: String) -> Unit,
    onSetShowReasoning: (Boolean) -> Unit,
    onSetAiThinking: (Boolean) -> Unit,
    onSetContinueInBackground: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var openaiUrl by remember { mutableStateOf(uiState.openaiBaseUrl) }
    var openaiKey by remember { mutableStateOf(uiState.openaiApiKey) }
    var openaiModel by remember { mutableStateOf(uiState.openaiModel) }
    var anthropicUrl by remember { mutableStateOf(uiState.anthropicBaseUrl) }
    var anthropicKey by remember { mutableStateOf(uiState.anthropicApiKey) }
    var anthropicModel by remember { mutableStateOf(uiState.anthropicModel) }

    // sync from state
    LaunchedEffect(uiState) {
        openaiUrl = uiState.openaiBaseUrl
        openaiKey = uiState.openaiApiKey
        openaiModel = uiState.openaiModel
        anthropicUrl = uiState.anthropicBaseUrl
        anthropicKey = uiState.anthropicApiKey
        anthropicModel = uiState.anthropicModel
    }

    var snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            snackbarHostState.showSnackbar("设置已保存")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Provider selection
            Text("Provider", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = uiState.provider == Provider.OPENAI,
                    onClick = { onSetProvider(Provider.OPENAI) },
                    label = { Text("OpenAI 兼容") }
                )
                FilterChip(
                    selected = uiState.provider == Provider.ANTHROPIC,
                    onClick = { onSetProvider(Provider.ANTHROPIC) },
                    label = { Text("Anthropic") }
                )
            }

            HorizontalDivider()

            // OpenAI Config
            Text("OpenAI 兼容配置", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = openaiUrl,
                onValueChange = { openaiUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = openaiKey,
                onValueChange = { openaiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = openaiModel,
                onValueChange = { openaiModel = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { onSaveOpenAI(openaiUrl, openaiKey, openaiModel) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存 OpenAI 配置")
            }

            HorizontalDivider()

            // Anthropic Config
            Text("Anthropic 配置", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = anthropicUrl,
                onValueChange = { anthropicUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = anthropicKey,
                onValueChange = { anthropicKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = anthropicModel,
                onValueChange = { anthropicModel = it },
                label = { Text("Model") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                onClick = { onSaveAnthropic(anthropicUrl, anthropicKey, anthropicModel) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存 Anthropic 配置")
            }

            HorizontalDivider()

            // Display settings
            Text("显示设置", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("显示思考过程", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.showReasoning,
                    onCheckedChange = onSetShowReasoning
                )
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("AI 推理 (Thinking Mode)", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.aiThinking,
                    onCheckedChange = onSetAiThinking
                )
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("后台继续生成", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.continueInBackground,
                    onCheckedChange = onSetContinueInBackground
                )
            }
        }
    }
}
