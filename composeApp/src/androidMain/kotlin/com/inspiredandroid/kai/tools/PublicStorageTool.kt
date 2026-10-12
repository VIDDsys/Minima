package com.inspiredandroid.kai.tools

import android.content.Context
import com.inspiredandroid.kai.linux.PUBLIC_STORAGE_ALIASES
import com.inspiredandroid.kai.linux.PUBLIC_STORAGE_GUEST_EMULATED
import com.inspiredandroid.kai.linux.PUBLIC_STORAGE_GUEST_SDCARD
import com.inspiredandroid.kai.linux.PublicStorageSession
import com.inspiredandroid.kai.linux.hasPublicStorageAccess
import com.inspiredandroid.kai.linux.publicStorageRoot
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.permissionQualifier
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_public_storage_description
import kai.composeapp.generated.resources.tool_public_storage_name
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.inject
import kotlin.time.Duration.Companion.seconds

private const val PERMISSION_DENIED =
    "All files access is not granted. Ask the user to enable it for Minima: Settings → Sandbox, " +
        "or system Settings → Apps → Special app access → All files access. " +
        "Until then, paths under /storage/emulated/0 (Download, DCIM, Documents, Minima) are unavailable. " +
        "The Linux sandbox under /root still works and was not touched."

private const val PLAY_BUILD =
    "All files access is not in this build. The Play Store flavor does not declare " +
        "MANAGE_EXTERNAL_STORAGE (Play may reject that permission). Install the FOSS/sideload build " +
        "and grant All files access. The Linux sandbox under /root is unaffected."

/**
 * Native shared-storage operations. These do not go through proot.
 *
 * `/storage/emulated/0` (and `/sdcard`) here are the phone's public directories.
 * `/root` is the Linux sandbox on internal storage — a different tree, reached
 * with `execute_shell_command`. When All files access is granted, the sandbox
 * bind-mounts this same directory at [PUBLIC_STORAGE_GUEST_SDCARD] and
 * [PUBLIC_STORAGE_GUEST_EMULATED].
 */
object PublicStorageTool : Tool {
    const val ID = "public_storage"

    private val context: Context by inject(Context::class.java)
    private val permissionController: PermissionController by inject(
        PermissionController::class.java,
        permissionQualifier(AppPermission.ALL_FILES),
    )

    // The grant is a settings screen. Stay alive long enough for the user to flip it.
    override val timeout = 150.seconds

    override val schema = ToolSchema(
        name = ID,
        description = """Read and write the phone's shared storage (Download, DCIM, Documents, /storage/emulated/0/Minima) using Android file APIs. This is NOT the Linux sandbox.

Paths must stay under /storage/emulated/0. Aliases ${PUBLIC_STORAGE_ALIASES.joinToString(", ")} are the same directory. Traversal (../) and symlinks that leave that tree are rejected.

Do not use this for /root, ~/skills, or other sandbox paths. Those live on internal app storage and are reached with execute_shell_command. When All files access is granted, the sandbox also sees this same storage at $PUBLIC_STORAGE_GUEST_SDCARD and $PUBLIC_STORAGE_GUEST_EMULATED.

action is one of list, read, write, mkdir, delete. write requires content (UTF-8 text). read returns text and refuses binary files. write refuses to replace a file larger than the read cap (32768 bytes) with content that fits in one read — delete the file first if you mean to shrink it. delete of a non-empty directory requires recursive=true. Deleting or overwriting the storage root itself is refused.

If this returns a permission error, tell the user to grant All files access. Do not invent file contents. The Play Store build cannot grant this permission; the FOSS build can.""",
        parameters = mapOf(
            "action" to ParameterSchema(
                "string",
                "One of list, read, write, mkdir, delete",
                true,
            ),
            "path" to ParameterSchema(
                "string",
                "Absolute path under /storage/emulated/0, or an alias such as /sdcard/Download/note.txt",
                true,
            ),
            "content" to ParameterSchema(
                "string",
                "UTF-8 text to write. Required for action=write.",
                false,
            ),
            "recursive" to ParameterSchema(
                "boolean",
                "For action=delete, remove a non-empty directory. Default false.",
                false,
            ),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any = withContext(Dispatchers.IO) {
        val denied = ensureAccess()
        if (denied != null) return@withContext mapOf("success" to false, "error" to denied)

        val action = (args["action"] as? String)?.trim()?.lowercase()
            ?: return@withContext mapOf("success" to false, "error" to "action is required")
        val path = args["path"] as? String
            ?: return@withContext mapOf("success" to false, "error" to "path is required")
        val content = args["content"] as? String
        val recursive = args["recursive"] as? Boolean ?: false

        val root = runCatching { context.publicStorageRoot() }.getOrNull()
            ?: return@withContext mapOf(
                "success" to false,
                "error" to "Shared storage is not available. All files access may have been revoked.",
            )
        PublicStorageSession(root).execute(action, path, content, recursive)
    }

    private suspend fun ensureAccess(): String? {
        if (context.hasPublicStorageAccess()) return null
        if (!permissionController.canRequest()) return PLAY_BUILD
        val granted = runCatching { permissionController.requestPermission() }.getOrDefault(false)
        if (granted || context.hasPublicStorageAccess()) return null
        return PERMISSION_DENIED
    }

    val toolInfo = ToolInfo(
        id = ID,
        name = "Public storage",
        description = "List, read, and write the phone's shared storage",
        nameRes = Res.string.tool_public_storage_name,
        descriptionRes = Res.string.tool_public_storage_description,
        isEnabled = true,
    )
}
