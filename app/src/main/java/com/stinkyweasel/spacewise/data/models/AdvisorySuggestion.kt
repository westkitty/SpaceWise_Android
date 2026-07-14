package com.stinkyweasel.spacewise.data.models

data class AdvisorySuggestion(
    val id: String,
    val title: String,
    val description: String,
    val type: SuggestionType,
    val potentialSavingsBytes: Long,
    val actionText: String,
    val targetCategory: String? = null, // e.g. "Videos" or "Apps" to navigate
    val deepLinkAction: String? = null // settings activity if needed
) {
    val formattedSavings: String
        get() = ByteFormatting.formatByteCount(potentialSavingsBytes)
}

enum class SuggestionType {
    UNUSED_APP,
    LARGE_VIDEO,
    LARGE_DOCUMENT,
    CACHE_CLEANUP,
    SYSTEM_RECOMMENDATION
}
