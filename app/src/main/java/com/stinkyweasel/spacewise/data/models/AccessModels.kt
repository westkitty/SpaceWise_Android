package com.stinkyweasel.spacewise.data.models

enum class CategoryAccessState {
    AVAILABLE,
    PARTIAL,
    PERMISSION_REQUIRED,
    QUERY_FAILED,
    UNSUPPORTED
}

data class DeletionResult(
    val requestedCount: Int,
    val verifiedDeletedCount: Int,
    val stillPresentCount: Int,
    val skippedCount: Int,
    val verifiedReclaimedBytes: Long,
    val confirmationMayBeRequired: Boolean = false
) {
    val fullyDeleted: Boolean
        get() = requestedCount > 0 && verifiedDeletedCount == requestedCount

    val deletedAnything: Boolean
        get() = verifiedDeletedCount > 0
}
