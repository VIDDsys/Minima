package com.minima.chat.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.minima.chat.core.ui.theme.LocalMinimaColors
import com.minima.chat.core.ui.theme.MinimaTypography

/**
 * First-open welcome when no API key is configured.
 * Ink-minimal: title, quiet checklist, Settings / Skip — nothing extra.
 */
@Composable
fun OnboardingScreen(
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = LocalMinimaColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp)
            .semantics { contentDescription = "Onboarding" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Minima", style = MinimaTypography.headlineMedium, color = colors.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "安静的本机 Agent。密钥、对话与工具都留在这台手机上。",
            style = MinimaTypography.bodyMedium,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(32.dp))
        Step(1, "打开设置 → 服务商")
        Step(2, "填写 Base URL、模型与 API 密钥")
        Step(3, "新建对话 — Agent 默认可操作本机文件与多种工具")
        Spacer(Modifier.height(12.dp))
        Text(
            "可随时跳过。之后点齿轮图标仍可完成设置。",
            style = MinimaTypography.bodySmall,
            color = colors.textTertiary,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Open Settings" },
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
        ) {
            Text("打开设置")
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .semantics { contentDescription = "Skip onboarding" },
        ) {
            Text("暂时跳过", color = colors.textSecondary)
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val colors = LocalMinimaColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .semantics { contentDescription = "Step $number" },
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.bgContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = MinimaTypography.labelSmall,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MinimaTypography.bodyMedium,
            color = colors.text,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
