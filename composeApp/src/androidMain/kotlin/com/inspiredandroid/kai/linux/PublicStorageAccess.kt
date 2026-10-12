package com.inspiredandroid.kai.linux

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * True when this process can read and write the phone's shared storage.
 *
 * API 30+: [Environment.isExternalStorageManager] (All files access).
 * Older APIs: both legacy storage permissions. A denial returns false; it does not throw.
 */
internal fun Context.hasPublicStorageAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Environment.isExternalStorageManager()
} else {
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED
}

/** Primary shared-storage root when access is granted and the directory is usable. */
internal fun Context.publicStorageRoot(): File? {
    if (!hasPublicStorageAccess()) return null
    @Suppress("DEPRECATION")
    val dir = Environment.getExternalStorageDirectory()
    return runCatching { dir.canonicalFile }.getOrNull()?.takeIf { it.isDirectory }
}

/** True when [permission] is in the merged manifest. The Play flavor omits all-files access. */
internal fun Context.declaresPermission(permission: String): Boolean = try {
    packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions
        ?.contains(permission) == true
} catch (_: Exception) {
    false
}
