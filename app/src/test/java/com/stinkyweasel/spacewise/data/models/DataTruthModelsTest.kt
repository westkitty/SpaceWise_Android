package com.stinkyweasel.spacewise.data.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataTruthModelsTest {
    @Test
    fun deletionIsFullyVerifiedOnlyWhenEveryRequestedItemDisappears() {
        val partial = DeletionResult(3, 2, 1, 0, 100L)
        val complete = DeletionResult(3, 3, 0, 0, 150L)

        assertFalse(partial.fullyDeleted)
        assertTrue(partial.deletedAnything)
        assertTrue(complete.fullyDeleted)
    }

    @Test
    fun unavailableAndPartialAreDistinctTruthStates() {
        assertTrue(DataConfidence.PARTIAL != DataConfidence.UNAVAILABLE)
        assertTrue(DataAvailability.PARTIAL != DataAvailability.PERMISSION_REQUIRED)
    }
}
