package com.minima.chat

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class AboutAndLabelsTest {
    private fun read(rel: String): String {
        val candidates = listOf(
            File(rel),
            File("/workspace/minima-chat/$rel"),
            File("../$rel"),
            File("../../$rel"),
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun firstRunCnMatchesSettingsLabels() {
        val cn = read("docs/FIRST_RUN_CN.md")
        val settings = read(
            "feature/settings/src/main/java/com/minima/chat/feature/settings/ui/SettingsScreen.kt",
        )
        for (label in listOf(
            "API 密钥",
            "Base URL",
            "开源说明",
            "设置",
            "服务商",
            "全局 MD",
        )) {
            assertThat(settings).contains(label)
            assertThat(cn).contains(label)
        }
        assertThat(cn).contains("2.42.0")
        assertThat(cn).contains("设置 → 服务商")
        assertThat(cn).contains("暂时跳过")
        assertThat(settings).doesNotContain("仅沙箱")
        assertThat(settings).doesNotContain("启用工具 Agent")
        assertThat(settings).doesNotContain("SettingsQueryMatcher")
    }

    @Test
    fun appVersionIs2420() {
        val gradle = read("app/build.gradle.kts")
        assertThat(gradle).contains("versionName = \"2.42.0\"")
        assertThat(gradle).contains("versionCode = 55")
    }

    @Test
    fun onboardingHasNumberedChecklistNoSandbox() {
        val onboarding = read(
            "app/src/main/java/com/minima/chat/ui/onboarding/OnboardingScreen.kt",
        )
        assertThat(onboarding).contains("打开设置 → 服务商")
        assertThat(onboarding).contains("填写 Base URL、模型与 API 密钥")
        assertThat(onboarding).contains("暂时跳过")
        assertThat(onboarding).contains("contentDescription = \"Onboarding\"")
        assertThat(onboarding).doesNotContain("沙箱")
    }

    @Test
    fun settingsHasProviderGlobalMdAndAbout() {
        val settings = read(
            "feature/settings/src/main/java/com/minima/chat/feature/settings/ui/SettingsScreen.kt",
        )
        assertThat(settings).contains("SettingsGroup(title = \"服务商\")")
        assertThat(settings).contains("SettingsGroup(title = \"全局 MD\")")
        assertThat(settings).contains("SettingsGroup(title = \"关于\")")
        assertThat(settings).contains("SettingsFieldValues.apiKeyValue")
        assertThat(settings).contains("flushAllPending")
        assertThat(settings).contains("PasswordVisualTransformation()")
        assertThat(settings).contains("onSystemPromptChange")
        assertThat(settings).doesNotContain("value = draftKey ?: \"\"")
        assertThat(settings).doesNotContain("sandboxOnly")
        assertThat(settings).doesNotContain("Temperature")
        assertThat(settings).doesNotContain("MCP")
    }

    @Test
    fun emptyStatesOfferFirstOpenPaths() {
        val sessions = read(
            "feature/chat/src/main/java/com/minima/chat/feature/chat/ui/SessionsScreen.kt",
        )
        val chat = read(
            "feature/chat/src/main/java/com/minima/chat/feature/chat/ui/ChatScreen.kt",
        )
        assertThat(sessions).contains("新建对话")
        assertThat(sessions).contains("打开设置")
        assertThat(chat).contains("ChatEmptyTips.suggestions")
        assertThat(chat).contains("还差一步")
        assertThat(chat).doesNotContain("Sandbox only")
        assertThat(chat).doesNotContain("沙箱")
    }

    @Test
    fun settingsPersistShowsApiKeyDots() {
        val settings = read(
            "feature/settings/src/main/java/com/minima/chat/feature/settings/ui/SettingsScreen.kt",
        )
        assertThat(settings).doesNotContain("value = draftKey ?: \"\"")
        assertThat(settings).contains("PasswordVisualTransformation()")
    }

    @Test
    fun navHostColdStartsOnSessionsNeverChat() {
        val nav = read("app/src/main/java/com/minima/chat/ui/MinimaNavHost.kt")
        assertThat(nav).contains("rememberFreshNavController")
        assertThat(nav).doesNotContain("rememberNavController()")
        assertThat(nav).contains("startDestination = Routes.Sessions")
        assertThat(nav).contains("EnterTransition.None")
        assertThat(nav).doesNotContain("Routes.Onboarding")
        assertThat(nav).contains("showOnboarding")
        assertThat(nav).contains("sessionsSettled")
        assertThat(nav).contains("withFrameNanos")
        assertThat(nav).contains("onSessionsFirstFrame")
        assertThat(nav).contains("forceSessionsTick")
        assertThat(nav).contains("popBackStack(Routes.Sessions")
    }

    @Test
    fun mainActivityIgnoresLauncherStickySession() {
        val main = read("app/src/main/java/com/minima/chat/MainActivity.kt")
        val deep = read("app/src/main/java/com/minima/chat/deeplink/DeepLinks.kt")
        assertThat(main).contains("sessionIdForLaunch")
        assertThat(deep).contains("sessionIdForLaunch")
        assertThat(deep).contains("must NOT open chat")
        assertThat(main).contains("super.onCreate(null)")
        assertThat(main).contains("setRecentsScreenshotEnabled(false)")
        assertThat(main).contains("installSplashScreen")
        assertThat(main).contains("setKeepOnScreenCondition")
        assertThat(main).contains("forceSessionsTick")
        assertThat(main).contains("isLauncherMain")
        assertThat(main).doesNotContain("maybeRequestAllFilesAccess")
        assertThat(main).doesNotContain("delay(800)")
    }

    @Test
    fun systemPromptHasNoSandboxTalk() {
        val vm = read(
            "feature/chat/src/main/java/com/minima/chat/feature/chat/vm/ChatViewModel.kt",
        )
        assertThat(vm).contains("full device file access")
        assertThat(vm).contains("max tool permissions")
        assertThat(vm.lowercase()).doesNotContain("sandbox restriction")
        assertThat(vm.lowercase()).doesNotContain("no sandbox")
        // Tool API names may still say list_sandbox (compat) but persona must not lecture about 沙箱.
        assertThat(vm).doesNotContain("沙箱")
        assertThat(vm).contains("hardCeilingMs")
        assertThat(vm).contains("withTimeout(hardCeilingMs)")
        assertThat(vm).contains("TimeoutCancellationException")
    }

    @Test
    fun composerNestsCircularSend() {
        val composer = read(
            "core/ui/src/main/java/com/minima/chat/core/ui/components/Composer.kt",
        )
        assertThat(composer).contains("ComposerCircleButton")
        assertThat(composer).contains("RoundedCornerShape(28.dp)")
        assertThat(composer).contains("contentDescription = \"发送\"")
        assertThat(composer).contains("contentDescription = \"停止\"")
        assertThat(composer).contains("LEFT control")
        assertThat(composer).contains("RIGHT control")
    }

    @Test
    fun messageBubbleHasNoActionChips() {
        val bubble = read(
            "core/ui/src/main/java/com/minima/chat/core/ui/components/MessageBubble.kt",
        )
        val chat = read(
            "feature/chat/src/main/java/com/minima/chat/feature/chat/ui/ChatScreen.kt",
        )
        assertThat(bubble).doesNotContain("Copy message")
        assertThat(bubble).doesNotContain("contentDescription = \"Regenerate\"")
        assertThat(bubble).doesNotContain("contentDescription = \"Delete\"")
        assertThat(chat).contains("showActions = false")
    }

    @Test
    fun agentTimelineSegmentsAroundTools() {
        val vm = read(
            "feature/chat/src/main/java/com/minima/chat/feature/chat/vm/ChatViewModel.kt",
        )
        assertThat(vm).contains("awaitingNewSegment")
        assertThat(vm).contains("closeSegmentBeforeTools")
        assertThat(vm).contains("ensureAssistantSegment")
    }

    @Test
    fun toolCardKeepsExpandKillsRipple() {
        val tool = read(
            "core/ui/src/main/java/com/minima/chat/core/ui/components/ToolCard.kt",
        )
        assertThat(tool).contains("animateContentSize()")
        assertThat(tool).contains("indication = null")
    }

    @Test
    fun messageBubbleHasNoRippleIndication() {
        val bubble = read(
            "core/ui/src/main/java/com/minima/chat/core/ui/components/MessageBubble.kt",
        )
        assertThat(bubble).contains("indication = null")
    }

    @Test
    fun chatAttachUsesPickMultipleVisualMedia() {
        val chat = read(
            "feature/chat/src/main/java/com/minima/chat/feature/chat/ui/ChatScreen.kt",
        )
        assertThat(chat).contains("ActivityResultContracts.PickMultipleVisualMedia")
        assertThat(chat).contains("PickVisualMediaRequest")
        assertThat(chat).contains("messagesReady")
        assertThat(chat).contains("ChatListAutoscroll")
        assertThat(chat).contains("reverseLayout = true")
        assertThat(chat).doesNotContain("SnackbarHost")
    }

    @Test
    fun visionPatchWiredInSendPaths() {
        val repo = read(
            "core/data/src/main/java/com/minima/chat/core/data/repository/Repositories.kt",
        )
        val vm = read(
            "feature/chat/src/main/java/com/minima/chat/feature/chat/vm/ChatViewModel.kt",
        )
        assertThat(repo).contains("VisionHistoryPatch.ensureLastUserImages")
        assertThat(repo).contains("attachmentBase64")
        assertThat(vm).contains("VisionHistoryPatch.ensureLastUserImages")
        assertThat(vm).contains("attachmentBase64 = pending.firstOrNull()?.base64")
        assertThat(vm).contains("pendingImages")
        assertThat(vm).contains("attachImages")
    }

    @Test
    fun launcherIconUsesUserPurpleFg() {
        val colors = read("app/src/main/res/values/colors.xml")
        val adaptive = read("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        assertThat(colors).contains("#8F59F9")
        assertThat(adaptive).contains("@drawable/ic_launcher_fg")
        assertThat(adaptive).contains("@color/launcher_bg")
    }

    @Test
    fun messageBubbleHasNoImagePreview() {
        val bubble = read(
            "core/ui/src/main/java/com/minima/chat/core/ui/components/MessageBubble.kt",
        )
        assertThat(bubble).doesNotContain("BitmapFactory")
        assertThat(bubble).doesNotContain("asImageBitmap")
        assertThat(bubble).contains("attachmentLabels")
        // Chips rendered above the user bubble, not inside black background together with text only.
        assertThat(bubble).contains("Attachment chips ABOVE")
    }

    @Test
    fun composerChipsHaveNoThumbnailEmoji() {
        val composer = read(
            "core/ui/src/main/java/com/minima/chat/core/ui/components/Composer.kt",
        )
        assertThat(composer).doesNotContain("🖼")
        assertThat(composer).contains("ComposerChip")
        assertThat(composer).contains("horizontalScroll")
    }

}
