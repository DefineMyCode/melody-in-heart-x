package cn.com.dcsgo.mihx.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /** 对照：CompilationMode.None = 无预编译，模拟"没有 Baseline Profile"的冷启动 */
    @Test
    fun coldStartupWithoutBaselineProfile() {
        coldStartup(CompilationMode.None())
    }

    /** 对照：用 Baseline Profile 预编译（speed-profile）后的冷启动 */
    @Test
    fun coldStartupWithBaselineProfile() {
        coldStartup(
            CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
        )
    }

    private fun coldStartup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = "cn.com.dcsgo.mihx",
            metrics = listOf(StartupTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
            compilationMode = compilationMode,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }
}
