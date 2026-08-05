package cn.com.dcsgo.mihx.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首页滚动流畅度基准：测量滚动期间的帧时间分布（P50/P90/P95），
 * 用于验证列表渲染优化（位置窄流/稳定性标注/缓存）的实际收益。
 * 需真机/模拟器运行（`:benchmark:connectedCheck`）。
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHomeScreen() {
        benchmarkRule.measureRepeated(
            packageName = "cn.com.dcsgo.mihx",
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.COLD,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg("cn.com.dcsgo.mihx").depth(0)), 5_000)

            val centerX = device.displayWidth / 2
            val fromY = (device.displayHeight * 0.7).toInt()
            val toY = (device.displayHeight * 0.3).toInt()
            // 上下各滚动 3 次，覆盖 LazyColumn 的项创建/复用路径
            repeat(3) {
                device.swipe(centerX, fromY, centerX, toY, 200)
            }
            repeat(3) {
                device.swipe(centerX, toY, centerX, fromY, 200)
            }
        }
    }
}
