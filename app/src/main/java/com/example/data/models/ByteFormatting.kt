package com.example.data.models

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ByteFormatting {

    fun formatByteCount(bytes: Long): String {
        if (bytes < 0) return "0 B"
        if (bytes < 1000) return "$bytes B"
        
        val kb = bytes / 1000.0
        if (kb < 1000) {
            return String.format(Locale.US, "%.1f KB", kb)
        }
        
        val mb = kb / 1000.0
        if (mb < 1000) {
            return String.format(Locale.US, "%.1f MB", mb)
        }
        
        val gb = mb / 1000.0
        if (gb < 1000) {
            return String.format(Locale.US, "%.1f GB", gb)
        }
        
        val tb = gb / 1000.0
        return String.format(Locale.US, "%.2f TB", tb)
    }

    fun formatDate(seconds: Long): String {
        if (seconds <= 0) return "Unknown date"
        return try {
            val date = Date(seconds * 1000L)
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
            sdf.format(date)
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    fun formatLastUsed(timestamp: Long?, currentTimeMillis: Long = System.currentTimeMillis()): String {
        if (timestamp == null || timestamp <= 0) return "Last used: unavailable"
        
        val diffMs = currentTimeMillis - timestamp
        if (diffMs < 0) {
            return "Last used: today"
        }
        
        val diffDays = diffMs / (1000 * 60 * 60 * 24)
        return when {
            diffDays == 0L -> "Last used: today"
            diffDays == 1L -> "Last used: yesterday"
            diffDays < 7L -> "Last used: ${diffDays}d ago"
            diffDays < 30L -> {
                val weeks = diffDays / 7
                "Last used: ${weeks}w ago"
            }
            diffDays < 365L -> {
                val months = diffDays / 30
                "Last used: ${months}mo ago"
            }
            else -> {
                try {
                    val date = Date(timestamp)
                    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
                    "Last used: ${sdf.format(date)}"
                } catch (e: Exception) {
                    "Last used: unavailable"
                }
            }
        }
    }
}
