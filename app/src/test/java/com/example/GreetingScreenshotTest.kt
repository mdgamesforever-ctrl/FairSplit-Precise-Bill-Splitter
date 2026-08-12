package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.calculator.BillCalculator
import com.example.model.BillState
import com.example.ui.components.HeroResultCard
import com.example.ui.theme.MyApplicationTheme
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

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun heroCard_screenshot() {
    val state = BillState(billInput = "100.00", tipPercentage = 18.0)
    val calcResult = BillCalculator.calculate(state)

    composeTestRule.setContent {
      MyApplicationTheme {
        HeroResultCard(
          currencySymbol = "$",
          result = calcResult,
          peopleCount = 2,
          isRoundUp = false,
          onToggleRoundUp = {},
          onOpenWhoPays = {},
          onShare = {},
          onOpenHistory = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
