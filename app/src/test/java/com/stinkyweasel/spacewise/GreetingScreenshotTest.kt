package com.stinkyweasel.spacewise

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.stinkyweasel.spacewise.data.models.StorageSnapshot
import com.stinkyweasel.spacewise.ui.screens.StorageOverviewCard
import com.stinkyweasel.spacewise.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule 
    val composeTestRule = createComposeRule()

    @Test
    fun greeting_screenshot() {
        val fakeSnapshot = StorageSnapshot(
            totalBytes = 128_000_000_000L,
            usedBytes = 64_000_000_000L,
            freeBytes = 64_000_000_000L,
            categories = emptyList()
        )
        
        composeTestRule.setContent { 
            MyApplicationTheme { 
                StorageOverviewCard(snapshot = fakeSnapshot) 
            } 
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
