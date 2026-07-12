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
                val hasImages = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val hasVideos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                val hasAudio = isGranted(context, Manifest.permission.READ_MEDIA_AUDIO)
                val hasSelectedVisual = isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

                when {
                    hasImages && hasVideos && hasAudio -> MediaAccess.FULL
                    hasSelectedVisual -> MediaAccess.PARTIAL_VISUAL
                    else -> MediaAccess.NONE
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val hasImages = isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
                val hasVideos = isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
                val hasAudio = isGranted(context, Manifest.permission.READ_MEDIA_AUDIO)
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

    /**
     * Existing broad storage scans must only run with full media access.
     * Partial Android 14 visual access is deliberately not treated as complete device visibility.
     */
    fun hasMediaPermissions(context: Context): Boolean = getMediaAccess(context) == MediaAccess.FULL

    fun hasFullMediaPermissions(context: Context): Boolean = hasMediaPermissions(context)

    fun hasImagePermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
            else -> isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasVideoPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
            else -> isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

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