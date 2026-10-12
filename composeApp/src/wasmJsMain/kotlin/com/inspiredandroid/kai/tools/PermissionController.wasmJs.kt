package com.inspiredandroid.kai.tools

import androidx.compose.runtime.Composable

internal actual fun platformHasPermission(permission: AppPermission): Boolean = when (permission) {
    // Not implemented on web.
    AppPermission.CALENDAR,
    AppPermission.POST_NOTIFICATIONS,
    AppPermission.READ_SMS,
    AppPermission.SEND_SMS,
    -> false

    // The browser doesn't gate local network access as an app permission.
    AppPermission.LOCAL_NETWORK -> true

    // Shared storage is an Android permission.
    AppPermission.ALL_FILES -> false
}

internal actual fun platformCanRequest(permission: AppPermission): Boolean = false

internal actual fun platformOpenAppSettings() {
    // No app permission settings screen on web.
}

@Composable
actual fun SetupPermissionHandler(controller: PermissionController) = Unit
