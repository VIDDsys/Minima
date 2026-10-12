package com.inspiredandroid.kai.ui.chat.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.inspiredandroid.kai.ui.components.animatedGradientBorder
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.logo
import kai.composeapp.generated.resources.start_interactive_ui
import kai.composeapp.generated.resources.welcome_message
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun EmptyState(
    modifier: Modifier,
    onStartInteractiveMode: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.welcome_message),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (onStartInteractiveMode != null) {
            Spacer(Modifier.height(16.dp))
            AnimatedBorderButton(
                text = stringResource(Res.string.start_interactive_ui),
                onClick = onStartInteractiveMode,
            )
        }
    }
}

@Composable
private fun AnimatedBorderButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .handCursor()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .animatedGradientBorder(
                cornerRadius = 50.dp,
                borderWidth = 3.dp,
                backgroundColor = MaterialTheme.colorScheme.background,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
