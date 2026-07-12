package com.stinkyweasel.spacewise.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

object PermissionUtils {

    enum class MediaAccess {
        NONE,
        PARTIAL_VISUAL,
        FULL
    }

    fun getMediaAccess(context: Context): MediaAccess {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                val hasImages = hasFullImagePermission(context)
                val hasVideos = hasFullVideoPermission(context)
                val hasAudio = hasAudioPermission(context)
                val hasSelectedVisual = hasSelectedVisualPermission(context)

                when {
                    hasImages && hasVideos && hasAudio -> MediaAccess.FULL
                    hasSelectedVisual || hasImages || hasVideos -> MediaAccess.PARTIAL_VISUAL
                    else -> MediaAccess.NONE
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasImages = hasFullImagePermission(context)
                val hasVideos = hasFullVideoPermission(context)
                val hasAudio = hasAudioPermission(context)
                if (hasImages && hasVideos && hasAudio) MediaAccess.FULL else MediaAccess.NONE
            }

            else -> {
                if (isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    MediaAccess.FULL
                } else {
                    MediaAccess.NONE
                }
            }
        }
    }

    fun hasMediaPermissions(context: Context): Boolean = getMediaAccess(context) != MediaAccess.NONE

    fun hasFullMediaPermissions(context: Context): Boolean = getMediaAccess(context) == MediaAccess.FULL

    fun hasFullImagePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
            else -> isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasFullVideoPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
            else -> isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasSelectedVisualPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    fun hasImageReadAccess(context: Context): Boolean =
        hasFullImagePermission(context) || hasSelectedVisualPermission(context)

    fun hasVideoReadAccess(context: Context): Boolean =
        hasFullVideoPermission(context) || hasSelectedVisualPermission(context)

    fun hasImagePermission(context: Context): Boolean = hasFullImagePermission(context)

    fun hasVideoPermission(context: Context): Boolean = hasFullVideoPermission(context)

    fun hasAudioPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                isGranted(context, Manifest.permission.READ_MEDIA_AUDIO)
            else -> isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    @Suppress("DEPRECATION")
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getUsageAccessSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
    }

    fun hasAllCriticalPermissions(context: Context): Boolean {
        return hasFullMediaPermissions(context) && hasUsageStatsPermission(context)
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
