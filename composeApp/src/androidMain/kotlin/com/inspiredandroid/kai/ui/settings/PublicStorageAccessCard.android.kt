package com.inspiredandroid.kai.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inspiredandroid.kai.permissionQualifier
import com.inspiredandroid.kai.tools.AppPermission
import com.inspiredandroid.kai.tools.PermissionController
import com.inspiredandroid.kai.ui.handCursor
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.settings_public_storage_description
import kai.composeapp.generated.resources.settings_public_storage_grant
import kai.composeapp.generated.resources.settings_public_storage_granted
import kai.composeapp.generated.resources.settings_public_storage_title
import kai.composeapp.generated.resources.settings_public_storage_unavailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
actual fun PublicStorageAccessCard() {
    val controller = koinInject<PermissionController>(permissionQualifier(AppPermission.ALL_FILES))
    var granted by remember { mutableStateOf(controller.hasPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = controller.hasPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val scope = rememberCoroutineScope()

    Spacer(Modifier.height(12.dp))
    SettingsCard {
        Text(
            text = stringResource(Res.string.settings_public_storage_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_public_storage_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        when {
            granted -> Text(
                text = stringResource(Res.string.settings_public_storage_granted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            !controller.canRequest() -> Text(
                text = stringResource(Res.string.settings_public_storage_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> OutlinedButton(
                onClick = {
                    scope.launch {
                        controller.requestPermission()
                        granted = controller.hasPermission()
                    }
                },
                modifier = Modifier.handCursor(),
            ) {
                Text(stringResource(Res.string.settings_public_storage_grant))
            }
        }
    }
}
