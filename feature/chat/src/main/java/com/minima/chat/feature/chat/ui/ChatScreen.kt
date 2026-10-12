package com.minima.chat.feature.chat.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minima.chat.core.agent.tools.PermissionDecision
import com.minima.chat.core.data.media.AttachmentCodec
import com.minima.chat.core.data.model.Message
import com.minima.chat.core.data.model.MessageKind
import com.minima.chat.core.data.util.TokenUsageEstimator
import com.minima.chat.core.data.model.MessageRole
import com.minima.chat.core.data.model.ToolMessageStatus
import com.minima.chat.core.ui.components.ComposerChip
import com.minima.chat.core.ui.components.EmptyState
import com.minima.chat.core.ui.components.MessageBubble
import com.minima.chat.core.ui.components.MinimaComposer
import com.minima.chat.core.ui.components.ToolCard
import com.minima.chat.core.ui.components.ToolCardStatus
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaTypography
import com.minima.chat.core.ui.util.ChatListAutoscroll
import com.minima.chat.feature.chat.vm.ChatViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val messagesReady by viewModel.messagesReady.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val colors = LocalMinimaColors.current
    // reverseLayout: index 0 = visual bottom = newest → first frame lands at end.
    val listState = rememberLazyListState()
    var stuckToBottom by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Modern Photo Picker — more reliable multi-image grants than GetMultipleContents on OEMs.
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = AttachmentCodec.MAX_PENDING_IMAGES,
        ),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.attachImages(uris)
    }

    // Inline error text under composer (no snackbar / popup covering the input).
    LaunchedEffect(ui.error) {
        if (ui.error != null) {
            kotlinx.coroutines.delay(4_000)
            viewModel.clearError()
        }
    }
    LaunchedEffect(ui.info) {
        if (ui.info != null) viewModel.clearInfo()
    }

    // Track stick-to-bottom: pause when user scrolls up; resume at bottom.
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }.distinctUntilChanged().collect { (idx, offset, scrolling) ->
            stuckToBottom = ChatListAutoscroll.nextStuck(
                firstVisibleIndex = idx,
                firstVisibleOffset = offset,
                currentlyStuck = stuckToBottom,
                userScrolling = scrolling,
            )
        }
    }

    // Autoscroll to newest when pinned.
    LaunchedEffect(messages.size, ui.isStreaming, ui.streamingAssistantId, stuckToBottom, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        if (ChatListAutoscroll.shouldAutoScroll(stuckToBottom, messages.size)) {
            runCatching { listState.scrollToItem(0) }
        }
    }

    val lastAssistantId = remember(messages, ui.isStreaming) {
        if (ui.isStreaming) null
        else messages.lastOrNull {
            it.kind == MessageKind.Text &&
                it.role == MessageRole.Assistant &&
                it.content.isNotBlank()
        }?.id
    }
    val lastUserId = remember(messages, ui.isStreaming) {
        if (ui.isStreaming) null
        else messages.lastOrNull {
            it.kind == MessageKind.Text &&
                it.role == MessageRole.User &&
                it.content.isNotBlank()
        }?.id
    }
    val usageSubtitle = remember(messages, settings.showUsageEstimate, ui.isStreaming) {
        if (!settings.showUsageEstimate || ui.isStreaming || messages.isEmpty()) null
        else {
            val textMsgs = messages.filter { it.kind == MessageKind.Text }
            TokenUsageEstimator.sessionSubtitle(
                billedPairs = textMsgs.map { it.inputTokens to it.outputTokens },
                textsForEstimate = textMsgs.map { it.content },
            )
        }
    }

    ui.permissionRequest?.let { req ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.resolvePermission(PermissionDecision.Deny) },
            sheetState = sheetState,
            containerColor = colors.bgElevated,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("Workspace file access", style = MinimaTypography.titleMedium, color = colors.text)
                Spacer(Modifier.height(8.dp))
                Text(
                    "「${req.toolName}」需要读写你授权的文件夹。",
                    style = MinimaTypography.bodyMedium,
                    color = colors.textSecondary,
                )
                Text(
                    "Deny remembers for this tool until you leave the chat. This session allows all file tools here.",
                    style = MinimaTypography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (req.argsSummary.isNotBlank()) {
                    Text(
                        req.argsSummary.take(160),
                        style = MinimaTypography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.resolvePermission(PermissionDecision.Deny) }) {
                        Text("Deny for chat", color = colors.error)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.resolvePermission(PermissionDecision.AllowOnce) }) {
                        Text("Allow once", color = colors.accent)
                    }
                    TextButton(onClick = { viewModel.resolvePermission(PermissionDecision.AllowSession) }) {
                        Text("Allow all here", color = colors.accent)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    Scaffold(
        containerColor = colors.bgLayout,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            session?.title ?: "Chat",
                            style = MinimaTypography.titleMedium,
                            maxLines = 1,
                        )
                        when {
                            settings.agentEnabled && ui.isStreaming -> {
                                Text(
                                    "Agent 运行中",
                                    style = MinimaTypography.labelSmall,
                                    color = colors.accent,
                                )
                            }
                            else -> {
                                val subtitle = usageSubtitle
                                if (subtitle != null) {
                                    Text(
                                        subtitle,
                                        style = MinimaTypography.labelSmall,
                                        color = colors.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::shareSession,
                            enabled = !ui.isStreaming,
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share chat")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.bgLayout,
                    titleContentColor = colors.text,
                    navigationIconContentColor = colors.text,
                    actionIconContentColor = colors.text,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    // Wait for first Room emission — never flash empty new-chat EmptyState.
                    !messagesReady -> {
                        Box(Modifier.fillMaxSize())
                    }
                    messages.isEmpty() && !settings.hasApiKey -> {
                        EmptyState(
                            title = "还差一步",
                            subtitle = "打开设置 → 服务商，填写 Base URL、模型与 API 密钥。密钥加密保存在本机。",
                            actionLabel = "打开设置",
                            onAction = onOpenSettings,
                        )
                    }
                    messages.isEmpty() -> {
                        EmptyState(
                            title = "打个招呼吧",
                            subtitle = if (settings.agentEnabled)
                                "Agent 已就绪，默认可使用本机工作区与公共存储工具（最大权限）。"
                            else
                                "消息留在本机，回复来自你配置的 OpenAI 兼容接口。",
                            suggestions = ChatEmptyTips.suggestions(settings.agentEnabled),
                            onSuggestion = viewModel::onDraftChange,
                        )
                    }
                    else -> {
                        val newestFirst = remember(messages) { messages.asReversed() }
                        LazyColumn(
                            state = listState,
                            reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = newestFirst,
                                key = { it.id },
                                contentType = { msg ->
                                    if (msg.kind == MessageKind.Tool) "tool" else "text"
                                },
                            ) { msg ->
                                ChatRow(
                                    msg = msg,
                                    isStreamingRow = ui.isStreaming && msg.id == ui.streamingAssistantId,
                                    showActions = false,
                                    canRegenerate = msg.id == lastAssistantId,
                                    canEdit = msg.id == lastUserId,
                                    onRegenerate = viewModel::regenerateLast,
                                    onEdit = viewModel::editLastUser,
                                    onDelete = { viewModel.deleteMessage(msg.id) },
                                )
                            }
                        }
                    }
                }
            }
            if (ui.error != null) {
                Text(
                    text = ui.error!!,
                    style = MinimaTypography.bodySmall,
                    color = colors.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            val chips = remember(ui.pendingImages) {
                ui.pendingImages.map { ComposerChip(id = it.absolutePath, label = it.chipLabel) }
            }
            MinimaComposer(
                value = ui.draft,
                onValueChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onStop = viewModel::stop,
                isStreaming = ui.isStreaming,
                enabled = !ui.attaching,
                attachmentChips = chips,
                attaching = ui.attaching,
                onAttachClick = {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemoveAttachment = viewModel::removePendingImage,
                attachEnabled = !ui.attaching,
                placeholder = when {
                    !settings.hasApiKey -> "请先在设置中填写 API 密钥…"
                    ui.attaching -> "正在处理图片…"
                    settings.agentEnabled -> "问 Minima…"
                    else -> "输入消息…"
                },
            )
        }
    }
}

@Composable
private fun ChatRow(
    msg: Message,
    isStreamingRow: Boolean,
    showActions: Boolean,
    canRegenerate: Boolean,
    canEdit: Boolean,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    if (msg.kind == MessageKind.Tool) {
        ToolCard(
            name = msg.toolName ?: "tool",
            argsSummary = msg.toolArgs.orEmpty(),
            resultPreview = msg.toolResult.orEmpty(),
            status = when (msg.toolStatus) {
                ToolMessageStatus.Running -> ToolCardStatus.Running
                ToolMessageStatus.Ok -> ToolCardStatus.Ok
                ToolMessageStatus.Error -> ToolCardStatus.Error
                null -> ToolCardStatus.Running
            },
            agentStep = msg.agentStep,
            expandKey = msg.id,
        )
    } else if (msg.content.isNotEmpty() || isStreamingRow || !msg.attachmentPath.isNullOrBlank()) {
        MessageBubble(
            text = msg.content,
            isUser = msg.role == MessageRole.User,
            isStreaming = isStreamingRow,
            useMarkdown = msg.role == MessageRole.Assistant && !isStreamingRow,
            showActions = showActions,
            onRegenerate = if (canRegenerate) onRegenerate else null,
            onEdit = if (canEdit && msg.role == MessageRole.User) onEdit else null,
            onDelete = if (showActions && msg.kind == MessageKind.Text) onDelete else null,
            attachmentLabels = AttachmentCodec.labels(msg.attachmentPath),
        )
    }
}
