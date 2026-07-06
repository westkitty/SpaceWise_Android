package com.example.data.models

data class RawStorageCategory(
    val name: String,
    val bytes: Long,
    val colorArgb: Long
)

data class NormalizedStorageCategory(
    val name: String,
    val bytes: Long,
    val colorArgb: Long,
    val percentage: Float
)

object StorageCategoryMath {

    fun percentageOf(part: Long, whole: Long): Float {
        val clampedPart = part.coerceAtLeast(0L)
        val clampedWhole = whole.coerceAtLeast(0L)
        if (clampedWhole <= 0L || clampedPart <= 0L) return 0f
        return ((clampedPart.toDouble() / clampedWhole.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
    }

    fun buildBreakdown(
        usedBytes: Long,
        rawCategories: List<RawStorageCategory>
    ): List<NormalizedStorageCategory> {
        val clampedUsedBytes = usedBytes.coerceAtLeast(0L)
        
        // 1. Clamp category sizes to >= 0
        val sanitizedRaw = rawCategories.map {
            it.copy(bytes = it.bytes.coerceAtLeast(0L))
        }
        
        val rawSum = sanitizedRaw.sumOf { it.bytes }
        
        val normalizedList = mutableListOf<NormalizedStorageCategory>()
        
        if (clampedUsedBytes == 0L) {
            // All zeros
            sanitizedRaw.forEach { raw ->
                normalizedList.add(NormalizedStorageCategory(raw.name, 0L, raw.colorArgb, 0f))
            }
            normalizedList.add(NormalizedStorageCategory("System & Other", 0L, 0xFF9C27B0L, 0f))
            return normalizedList
        }
        
        if (rawSum > clampedUsedBytes) {
            // Scale down known categories proportionally
            val scaleFactor = clampedUsedBytes.toDouble() / rawSum.toDouble()
            var currentSum = 0L
            
            sanitizedRaw.forEachIndexed { index, raw ->
                val scaledBytes = if (index == sanitizedRaw.lastIndex) {
                    clampedUsedBytes - currentSum
                } else {
                    val sb = (raw.bytes * scaleFactor).toLong()
                    currentSum += sb
                    sb
                }.coerceAtLeast(0L)
                
                val percentage = percentageOf(scaledBytes, clampedUsedBytes)
                normalizedList.add(NormalizedStorageCategory(raw.name, scaledBytes, raw.colorArgb, percentage))
            }
            
            // System & Other has 0 bytes here because known categories used up all space
            normalizedList.add(NormalizedStorageCategory("System & Other", 0L, 0xFF9C27B0L, 0f))
        } else {
            // Sum is less than or equal to usedBytes
            sanitizedRaw.forEach { raw ->
                val percentage = percentageOf(raw.bytes, clampedUsedBytes)
                normalizedList.add(NormalizedStorageCategory(raw.name, raw.bytes, raw.colorArgb, percentage))
            }
            
            val remainderBytes = clampedUsedBytes - rawSum
            val remainderPercentage = percentageOf(remainderBytes, clampedUsedBytes)
            normalizedList.add(
                NormalizedStorageCategory(
                    "System & Other",
                    remainderBytes,
                    0xFF9C27B0L,
                    remainderPercentage
                )
            )
        }
        
        return normalizedList
    }
}
