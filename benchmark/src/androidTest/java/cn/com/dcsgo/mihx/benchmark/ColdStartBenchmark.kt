package cn.com.dcsgo.mihx.benchmark

import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColdStartBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStart() {
        // Phase 6: parametrize startup mode (COLD) + iterations >= 5.
        // benchmarkRule.measureRepeated(...) lands here.
    }
}
