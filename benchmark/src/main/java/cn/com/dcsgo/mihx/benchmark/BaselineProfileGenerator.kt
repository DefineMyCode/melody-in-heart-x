package cn.com.dcsgo.mihx.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成 Baseline Profile。
 *
 * 在真机/模拟器上运行（`.\gradlew.bat :app:generateBaselineProfile` 或
 * `:benchmark:connectedCheck`），产物写入 app/src/main/baselineProfiles/baseline-prof.txt，
 * release 构建会自动打包，配合 profileinstaller 提升冷启动与首屏滚动性能。
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(packageName = "cn.com.dcsgo.mihx") {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg("cn.com.dcsgo.mihx").depth(0)), 5_000)
            // 覆盖启动后的首页（播放页）与歌单页路径
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.7).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.3).toInt(),
                200,
            )
            device.swipe(
                device.displayWidth / 2,
                (device.displayHeight * 0.3).toInt(),
                device.displayWidth / 2,
                (device.displayHeight * 0.7).toInt(),
                200,
            )
        }
    }
}
