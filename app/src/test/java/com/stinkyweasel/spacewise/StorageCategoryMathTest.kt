package com.stinkyweasel.spacewise

import com.stinkyweasel.spacewise.data.models.RawStorageCategory
import com.stinkyweasel.spacewise.data.models.StorageCategoryMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageCategoryMathTest {

    @Test
    fun test_percentageOf() {
        assertEquals(50f, StorageCategoryMath.percentageOf(50, 100), 0.01f)
        assertEquals(0f, StorageCategoryMath.percentageOf(0, 100), 0.01f)
        assertEquals(0f, StorageCategoryMath.percentageOf(50, 0), 0.01f)
        assertEquals(0f, StorageCategoryMath.percentageOf(-10, 100), 0.01f)
        assertEquals(100f, StorageCategoryMath.percentageOf(150, 100), 0.01f)
    }

    @Test
    fun test_buildBreakdown_zeroUsedStorage() {
        val rawList = listOf(
            RawStorageCategory("Photos", 500, 1),
            RawStorageCategory("Videos", 500, 2)
        )
        val breakdown = StorageCategoryMath.buildBreakdown(0L, rawList)
        
        // All categories including system & other must be 0 size & 0%
        breakdown.forEach { category ->
            assertEquals(0L, category.bytes)
            assertEquals(0f, category.percentage, 0.01f)
        }
    }

    @Test
    fun test_buildBreakdown_negativeInputClamping() {
        val rawList = listOf(
            RawStorageCategory("Photos", -500, 1),
            RawStorageCategory("Videos", 300, 2)
        )
        val breakdown = StorageCategoryMath.buildBreakdown(1000L, rawList)
        
        val photosCategory = breakdown.first { it.name == "Photos" }
        assertEquals(0L, photosCategory.bytes)
        assertEquals(0f, photosCategory.percentage, 0.01f)
    }

    @Test
    fun test_buildBreakdown_remainderBucketSystemAndOther() {
        val rawList = listOf(
            RawStorageCategory("Photos", 300, 1),
            RawStorageCategory("Videos", 200, 2)
        )
        // Used space is 1000, sum of known categories is 500. Remainder should be 500.
        val breakdown = StorageCategoryMath.buildBreakdown(1000L, rawList)
        
        val systemCategory = breakdown.first { it.name == "System & Other" }
        assertEquals(500L, systemCategory.bytes)
        assertEquals(50f, systemCategory.percentage, 0.01f)
    }

    @Test
    fun test_buildBreakdown_scaleDownKnownCategoriesExceedUsed() {
        val rawList = listOf(
            RawStorageCategory("Photos", 800, 1),
            RawStorageCategory("Videos", 400, 2)
        )
        // Used space is 1000, sum of known is 1200. Must scale down.
        val breakdown = StorageCategoryMath.buildBreakdown(1000L, rawList)
        
        val totalBytes = breakdown.filter { it.name != "System & Other" }.sumOf { it.bytes }
        assertEquals(1000L, totalBytes)
        
        val systemCategory = breakdown.first { it.name == "System & Other" }
        assertEquals(0L, systemCategory.bytes)
        assertEquals(0f, systemCategory.percentage, 0.01f)
    }

    @Test
    fun test_buildBreakdown_percentagesNeverExceed100() {
        val rawList = listOf(
            RawStorageCategory("Photos", 8000000L, 1),
            RawStorageCategory("Videos", 4000000L, 2)
        )
        val breakdown = StorageCategoryMath.buildBreakdown(1000L, rawList)
        
        var totalPercent = 0f
        breakdown.forEach { category ->
            totalPercent += category.percentage
            assertTrue(category.percentage in 0f..100f)
        }
        assertEquals(100f, totalPercent, 0.5f)
    }
}
