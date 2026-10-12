package com.minima.chat.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minima.chat.core.ui.components.SettingsGroup
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaTypography
import com.minima.chat.feature.settings.vm.SettingsViewModel

/**
 * Minimal settings: Base URL / Model / API Key (+ About).
 * All advanced / sandbox / agent toggles removed — defaults are max permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val draftUrl by viewModel.draftBaseUrl.collectAsStateWithLifecycle()
    val draftModel by viewModel.draftModel.collectAsStateWithLifecycle()
    val draftKey by viewModel.draftApiKey.collectAsStateWithLifecycle()
    val draftSystemPrompt by viewModel.draftSystemPrompt.collectAsStateWithLifecycle()
    val colors = LocalMinimaColors.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        onDispose { viewModel.flushAllPending() }
    }

    fun persistAndBack() {
        viewModel.flushAllPending()
        onBack()
    }

    Scaffold(
        containerColor = colors.bgLayout,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MinimaTypography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = { persistAndBack() },
                        modifier = Modifier.semantics { contentDescription = "返回" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.bgLayout,
                    titleContentColor = colors.text,
                    navigationIconContentColor = colors.text,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            SettingsGroup(title = "服务商") {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        "可自由填写。修改后自动保存，退出再进仍会保留。其它项留空则用默认。",
                        style = MinimaTypography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Label("Base URL")
                    OutlinedTextField(
                        value = SettingsFieldValues.textOrSaved(draftUrl, settings.baseUrl),
                        onValueChange = viewModel::onBaseUrlChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Base URL" },
                        singleLine = true,
                        enabled = true,
                        readOnly = false,
                        placeholder = { Text("https://api.deepseek.com/v1") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(12.dp))
                    Label("模型")
                    OutlinedTextField(
                        value = SettingsFieldValues.textOrSaved(draftModel, settings.modelId),
                        onValueChange = viewModel::onModelChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Model id" },
                        singleLine = true,
                        enabled = true,
                        readOnly = false,
                        placeholder = { Text("deepseek-flash") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Spacer(Modifier.height(12.dp))
                    Label("API 密钥")
                    OutlinedTextField(
                        value = SettingsFieldValues.apiKeyValue(draftKey, settings.apiKey),
                        onValueChange = viewModel::onApiKeyChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "API key" },
                        singleLine = true,
                        enabled = true,
                        readOnly = false,
                        placeholder = {
                            Text(if (settings.hasApiKey) "已保存（圆点显示）" else "sk-…")
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.saveApiKey()
                            focus.clearFocus()
                        }),
                    )
                    Text(
                        "加密保存在本机。输入后圆点显示，不会半途消失。Agent / 工具 / 公共存储默认全开，无需其它开关。",
                        style = MinimaTypography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            SettingsGroup(title = "全局 MD") {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        "自定义系统指令（AGENT.md）。修改后自动保存，并同步到本机 AGENT.md，对话与搜索都会用到。",
                        style = MinimaTypography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    OutlinedTextField(
                        value = SettingsFieldValues.textOrSaved(draftSystemPrompt, settings.systemPrompt),
                        onValueChange = viewModel::onSystemPromptChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .semantics { contentDescription = "Global MD" },
                        minLines = 6,
                        maxLines = 16,
                        placeholder = {
                            Text("例如：用简洁中文回答；优先读写 Documents/Minima …")
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            SettingsGroup(title = "关于") {
                val pkgVersion = remember(context) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull().orEmpty().ifBlank { "2.40.0" }
                }
                var showOss by remember { mutableStateOf(false) }
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text("Minima", style = MinimaTypography.titleMedium, color = colors.text)
                    Text(
                        "极简本机 AI Agent。密钥与对话留在本机。",
                        style = MinimaTypography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "版本 $pkgVersion",
                        style = MinimaTypography.labelSmall,
                        color = colors.textTertiary,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .semantics { contentDescription = "App version" },
                    )
                    TextButton(
                        onClick = { showOss = true },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .semantics { contentDescription = "Open source notes" },
                    ) {
                        Text("开源说明", color = colors.accent)
                    }
                }
                if (showOss) {
                    AlertDialog(
                        onDismissRequest = { showOss = false },
                        title = { Text("开源", style = MinimaTypography.titleMedium) },
                        text = {
                            Text(
                                "Minima 使用 Android Jetpack、OkHttp 与 Kotlin 协程。" +
                                    "API 密钥与聊天数据留在本机；不会转发到 Minima 云端。",
                                style = MinimaTypography.bodySmall,
                                color = colors.textSecondary,
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showOss = false }) {
                                Text("关闭", color = colors.accent)
                            }
                        },
                        containerColor = colors.bgElevated,
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    val colors = LocalMinimaColors.current
    Text(
        text,
        style = MinimaTypography.labelMedium,
        color = colors.textSecondary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
