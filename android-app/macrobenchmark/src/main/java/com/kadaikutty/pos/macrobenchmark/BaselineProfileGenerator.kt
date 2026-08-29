package com.kadaikutty.pos.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.kadaikutty.pos",
            profileBlock = {
                // Starts the default activity of the app
                startActivityAndWait()
                
                // We can add further interactions here (e.g., clicking on buttons, scrolling)
                // but just starting the activity covers a large portion of the initial load.
            }
        )
    }
}
