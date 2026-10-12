package com.minima.chat.feature.chat.ui

import com.minima.chat.core.data.util.SessionTitle
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minima.chat.core.data.model.Session
import com.minima.chat.core.ui.components.EmptyState
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaTypography
import com.minima.chat.feature.chat.vm.SessionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onOpenSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SessionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
    val archivedCount by viewModel.archivedCount.collectAsStateWithLifecycle()
    val colors = LocalMinimaColors.current
    var actionSession by remember { mutableStateOf<Session?>(null) }
    var pendingRename by remember { mutableStateOf<Session?>(null) }
    var pendingDelete by remember { mutableStateOf<Session?>(null) }

    Scaffold(
        containerColor = colors.bgLayout,
        topBar = {
            TopAppBar(
                title = { Text("Minima", style = MinimaTypography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.bgLayout,
                    titleContentColor = colors.text,
                    actionIconContentColor = colors.text,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.create { onOpenSession(it) } },
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建对话")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = "搜索会话" },
                singleLine = true,
                placeholder = { Text("搜索…") },
            )
            TextButton(
                onClick = viewModel::toggleShowArchived,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = "切换归档" },
            ) {
                val label = when {
                    showArchived && archivedCount > 0 -> "隐藏归档 ($archivedCount)"
                    showArchived -> "隐藏归档"
                    archivedCount > 0 -> "显示归档 ($archivedCount)"
                    else -> "显示归档"
                }
                Text(label, color = colors.accent, style = MinimaTypography.bodySmall)
            }
            when {
                sessions.isEmpty() && query.isBlank() && !showArchived -> {
                    EmptyState(
                        title = "还没有对话",
                        subtitle = "先去设置 → 服务商填写 Base URL、模型与 API 密钥，再点 + 新建对话。",
                        actionLabel = "打开设置",
                        onAction = onOpenSettings,
                        secondaryActionLabel = "新建对话",
                        onSecondaryAction = { viewModel.create { onOpenSession(it) } },
                    )
                }
                sessions.isEmpty() -> {
                    EmptyState(
                        title = if (query.isNotBlank()) "无匹配" else "没有归档对话",
                        subtitle = if (query.isNotBlank())
                            "换个关键词，或清空搜索…"
                        else
                            "长按会话 → 归档。数据留在本机，删除前不会消失。",
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(sessions, key = { it.id }) { session ->
                            SessionRow(
                                session = session,
                                onClick = { onOpenSession(session.id) },
                                onLongClick = { actionSession = session },
                            )
                        }
                    }
                }
            }
        }
    }

    actionSession?.let { s ->
        AlertDialog(
            onDismissRequest = { actionSession = null },
            title = { Text(s.title, style = MinimaTypography.titleMedium) },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.setPinned(s.id, !s.pinned)
                        actionSession = null
                    }) {
                        Text(if (s.pinned) "取消置顶" else "置顶", color = colors.text)
                    }
                    TextButton(onClick = {
                        viewModel.setArchived(s.id, !s.archived)
                        actionSession = null
                    }) {
                        Text(if (s.archived) "取消归档" else "归档", color = colors.text)
                    }
                    TextButton(onClick = {
                        pendingRename = s
                        actionSession = null
                    }) {
                        Text("重命名", color = colors.text)
                    }
                    TextButton(onClick = {
                        pendingDelete = s
                        actionSession = null
                    }) {
                        Text("删除", color = colors.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionSession = null }) { Text("关闭") }
            },
        )
    }

    pendingDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除对话？") },
            text = { Text("「${s.title}」将永久删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(s.id)
                    pendingDelete = null
                }) { Text("删除", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }

    pendingRename?.let { s ->
        var draft by remember(s.id) { mutableStateOf(s.title) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(SessionTitle.MAX_LEN + 32) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (draft.trim().isEmpty()) {
                            Text("留空 → 未命名")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(s.id, SessionTitle.normalize(draft))
                    pendingRename = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: Session,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = LocalMinimaColors.current
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "Open session ${session.title}" }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                buildString {
                    if (session.archived) append("📦 ")
                    append(session.title)
                },
                style = MinimaTypography.titleMedium,
                color = if (session.archived) colors.textSecondary else colors.text,
            )
            Text(
                fmt.format(Date(session.updatedAt)),
                style = MinimaTypography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (session.pinned) {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = "Pinned",
                tint = colors.accent,
            )
        }
    }
}
