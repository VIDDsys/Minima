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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minima.chat.core.ui.components.SettingsGroup
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaTypography
import com.minima.chat.feature.settings.vm.SettingsViewModel

/**
 * Minimal settings: Base URL / Model / API Key, public AGENTS.md, skills list, About.
 * Explicit 保存 — no auto-save. Global MD is the same file as /Minima/AGENTS.md.
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
    val draftAgentsMd by viewModel.draftAgentsMd.collectAsStateWithLifecycle()
    val diskAgentsMd by viewModel.diskAgentsMd.collectAsStateWithLifecycle()
    val publicSkills by viewModel.publicSkills.collectAsStateWithLifecycle()
    val skillsLoading by viewModel.skillsLoading.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val colors = LocalMinimaColors.current
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onSettingsVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(saveMessage) {
        val msg = saveMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearSaveMessage()
    }

    Scaffold(
        containerColor = colors.bgLayout,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MinimaTypography.titleLarge) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.semantics { contentDescription = "返回" },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            focus.clearFocus()
                            viewModel.saveAll()
                        },
                        enabled = !saving,
                        modifier = Modifier.semantics { contentDescription = "保存" },
                    ) {
                        Text(if (saving) "保存中…" else "保存", color = colors.accent)
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
                        "可自由填写。点右上角「保存」后写入本机。其它项留空则用默认。",
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
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    )
                    Text(
                        "加密保存在本机。输入后圆点显示。Agent / 工具 / 公共存储默认全开。",
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
                        "即 /storage/emulated/0/Minima/AGENTS.md（任意大小写）。" +
                            "与文件管理器编辑的是同一文件；外部改动会自动刷新到此处。点「保存」写回磁盘。",
                        style = MinimaTypography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    OutlinedTextField(
                        value = SettingsFieldValues.textOrSaved(draftAgentsMd, diskAgentsMd),
                        onValueChange = viewModel::onAgentsMdChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .semantics { contentDescription = "Global MD" },
                        minLines = 6,
                        maxLines = 16,
                        placeholder = {
                            Text("站立指令写在这里，与手机上的 AGENTS.md 完全同步…")
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            SettingsGroup(title = "技能") {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(
                        "来自 /storage/emulated/0/Minima/skills/*/SKILL.md（或 skill.md）。" +
                            "仅展示 name / description；完整正文由 Agent 调用 read_skill。",
                        style = MinimaTypography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TextButton(
                        onClick = { viewModel.reloadSkills() },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.semantics { contentDescription = "刷新技能" },
                    ) {
                        Text(if (skillsLoading) "刷新中…" else "刷新技能列表", color = colors.accent)
                    }
                    if (publicSkills.isEmpty()) {
                        Text(
                            "暂无技能。在 Minima/skills/<名称>/ 下放入 SKILL.md 后点刷新。",
                            style = MinimaTypography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    } else {
                        publicSkills.forEach { skill ->
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    skill.name,
                                    style = MinimaTypography.titleMedium,
                                    color = colors.text,
                                    modifier = Modifier.semantics {
                                        contentDescription = "Skill ${skill.name}"
                                    },
                                )
                                if (skill.description.isNotBlank()) {
                                    Text(
                                        skill.description,
                                        style = MinimaTypography.bodySmall,
                                        color = colors.textSecondary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                Text(
                                    "skills/${skill.folderName}",
                                    style = MinimaTypography.labelSmall,
                                    color = colors.textTertiary,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    focus.clearFocus()
                    viewModel.saveAll()
                },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "保存设置" },
            ) {
                Text(if (saving) "保存中…" else "保存")
            }
            Spacer(Modifier.height(8.dp))

            SettingsGroup(title = "关于") {
                val pkgVersion = remember(context) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull().orEmpty().ifBlank { "2.43.0" }
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
                                    "API 密钥与聊天数据留在本机；不会转发到任何 Minima 云端。",
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
