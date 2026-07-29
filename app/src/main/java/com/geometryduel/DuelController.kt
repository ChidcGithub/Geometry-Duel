package com.geometryduel

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geometryduel.audio.SoundManager
import com.geometryduel.neat.NeatEvolver
import com.geometryduel.neat.NeatStorage
import com.geometryduel.neat.NeatTrainer
import com.geometryduel.theme.GamePalette

/**
 * 应用级状态中枢（替代 libGDX GeometryDuelGame）：
 * 配置持久化（SharedPreferences，沿用原 "geometry-duel" 与键名）、
 * 主题/动态取色、音量、NEAT 训练器生命周期、音效。
 */
class DuelController(private val context: Context) {

    companion object {
        const val VERSION = "3.145.912"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("geometry-duel", Context.MODE_PRIVATE)

    /** Material You 种子色（ARGB，0=无，从壁纸/系统强调色提取）。 */
    val themeSeed: Int = extractThemeSeed()

    // ---- 配置状态（Compose 可观察）----
    var themeType by mutableStateOf(GamePalette.Type.Light)
        private set
    var dynamicColor by mutableStateOf(true)
        private set
    var volume by mutableFloatStateOf(0.5f)
    var tutorialDone by mutableStateOf(false)
    var visionRays by mutableIntStateOf(36)
        private set
    /** 对手风格：-1=经典规则AI，0=总冠军，1..N=各物种冠军 */
    var opponentStyle by mutableIntStateOf(0)
        private set
    var trainingEnabled by mutableStateOf(true)
        private set
    /** 每次对战后允许的后台训练模拟局数（sims）上限；达到后自动暂停，下一场实战重置预算。 */
    var trainSimLimit by mutableIntStateOf(10000)
        private set
    /** AI 决策速度：0=30Hz 1=20Hz 2=15Hz 3=12Hz (skipFrames = index+1)。训练线程也会读取。 */
    @Volatile var aiSpeed: Int = 1

    /** 当前游戏配色（随主题/动态取色重建）。 */
    var palette by mutableStateOf(GamePalette.light(themeSeedOrDefault()))
        private set

    /** 对战界面引用（用于息屏恢复后判断是否保持训练暂停）。 */
    var battleActiveChecker: (() -> Boolean)? = null

    val sounds = SoundManager(context)

    /** NEAT AI：后台训练器（持有冠军/种群/持久化）。 */
    lateinit var trainer: NeatTrainer
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        NeatStorage.init(context)
        HardwareInfo.detectAsync()
        loadConfig()
        trainer = NeatTrainer(this, visionRays)
        trainer.start()
        trainer.setPaused(!trainingEnabled)
    }

    fun shutdown() {
        if (!started) return
        started = false
        saveConfig()
        if (::trainer.isInitialized) trainer.shutdown()
        sounds.release()
    }

    /** 息屏：落盘并暂停训练，避免耗电。 */
    fun onAppPause() {
        saveConfig()
        if (::trainer.isInitialized) trainer.setPaused(true)
    }

    /** 亮屏：训练开关开且当前不在玩家对战中时恢复训练。 */
    fun onAppResume() {
        if (::trainer.isInitialized && trainingEnabled) {
            val inBattle = battleActiveChecker?.invoke() == true
            trainer.setPaused(inBattle)
        }
    }

    // ------------------------------------------------------------ 主题

    private fun themeSeedOrDefault(): Int = if (dynamicColor) themeSeed else 0

    fun applyTheme() {
        palette = if (themeType == GamePalette.Type.Dark) {
            GamePalette.dark(themeSeedOrDefault())
        } else {
            GamePalette.light(themeSeedOrDefault())
        }
    }

    fun toggleTheme() {
        themeType = if (themeType == GamePalette.Type.Dark) GamePalette.Type.Light else GamePalette.Type.Dark
        applyTheme()
    }

    /** 首次启动时按系统暗色初始化（仅当用户尚未显式选择过主题）。 */
    private fun initialThemeType(): GamePalette.Type {
        if (!prefs.contains("theme")) {
            val night = context.resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            return if (night) GamePalette.Type.Dark else GamePalette.Type.Light
        }
        return if (prefs.getString("theme", "light") == "dark") GamePalette.Type.Dark
        else GamePalette.Type.Light
    }

    /** 动态取色开关（无种子色的平台不可切换）。 */
    fun toggleDynamicColor() {
        if (themeSeed == 0) return
        dynamicColor = !dynamicColor
        applyTheme()
    }

    /**
     * Material You 动态取色：API 31+ 读系统动态色 system_accent1_500；
     * API 27~30 回退 WallpaperColors；更低版本或提取失败返回 0（使用默认紫）。
     */
    private fun extractThemeSeed(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= 31) {
                context.getColor(android.R.color.system_accent1_500)
            } else if (Build.VERSION.SDK_INT >= 27) {
                val wm = WallpaperManager.getInstance(context)
                val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                colors?.primaryColor?.toArgb() ?: 0
            } else 0
        } catch (t: Throwable) {
            0
        }
    }

    // ------------------------------------------------------------ 配置

    fun loadConfig() {
        themeType = initialThemeType()
        volume = prefs.getFloat("volume", 0.5f)
        tutorialDone = prefs.getBoolean("tutorialDone", false)
        visionRays = prefs.getInt("visionRays", 36)
        opponentStyle = prefs.getInt("opponentStyle", 0)
        trainingEnabled = prefs.getBoolean("trainingEnabled", true)
        trainSimLimit = prefs.getInt("trainSimLimit", 10000).coerceIn(1000, 10000)
        aiSpeed = prefs.getInt("aiSpeed", 1).coerceIn(0, 3)
        dynamicColor = prefs.getBoolean("dynamicColor", true)
        applyTheme()
    }

    fun saveConfig() {
        prefs.edit()
            .putString("theme", if (themeType == GamePalette.Type.Dark) "dark" else "light")
            .putFloat("volume", volume)
            .putBoolean("tutorialDone", tutorialDone)
            .putInt("visionRays", visionRays)
            .putInt("opponentStyle", opponentStyle)
            .putBoolean("trainingEnabled", trainingEnabled)
            .putInt("trainSimLimit", trainSimLimit)
            .putInt("aiSpeed", aiSpeed)
            .putBoolean("dynamicColor", dynamicColor)
            .apply()
    }

    // ------------------------------------------------------------ AI 训练

    fun toggleTraining() {
        trainingEnabled = !trainingEnabled
        if (::trainer.isInitialized) trainer.setPaused(!trainingEnabled)
    }

    fun resetAi() {
        if (::trainer.isInitialized) trainer.requestReset(visionRays)
    }

    /** 训练器是否已启动（UI 层查询用）。 */
    fun trainerStarted(): Boolean = ::trainer.isInitialized

    /** 设置界面显示的物种信息 */
    fun speciesInfoText(): String {
        if (!::trainer.isInitialized) return "AI not started"
        val ev: NeatEvolver? = trainer.evolver()
        if (ev == null || ev.currentSpecies.isEmpty()) {
            return "Gen ${trainer.generation()} | Species: 0 (training...)"
        }
        val labels = trainer.speciesStyleLabels()
        val sb = StringBuilder("Gen ${trainer.generation()} | Species: ${labels.size}")
        for (i in 0 until minOf(labels.size, 3)) sb.append(" ").append(labels[i])
        if (labels.size > 3) sb.append(" ...")
        return sb.toString()
    }

    fun opponentStyleLabel(): String {
        if (opponentStyle < 0) return "Classic"
        if (opponentStyle == 0) return "Champion"
        if (::trainer.isInitialized) {
            val labels = trainer.speciesStyleLabels()
            val idx = opponentStyle - 1
            if (idx >= 0 && idx < labels.size) return labels[idx]
        }
        return "Champion"
    }

    /** 循环切换对手风格 */
    fun cycleOpponentStyle() {
        val maxStyle = if (::trainer.isInitialized) trainer.speciesStyleLabels().size else 0
        opponentStyle++
        if (opponentStyle > maxStyle) opponentStyle = -1
    }

    /** 调整战后训练 sims 上限（1000~10000）。调大后若已因达限暂停会自动恢复训练。 */
    fun updateTrainSimLimit(limit: Int) {
        trainSimLimit = limit.coerceIn(1000, 10000)
        saveConfig()
    }

    /** 调整视野射线数（16~64）。输入维度变化 → 重置 AI 训练。 */
    fun updateVisionRays(rays: Int) {
        val r = rays.coerceIn(16, 64)
        if (r == visionRays) return
        visionRays = r
        saveConfig()
        if (::trainer.isInitialized) trainer.requestReset(r)
    }

    // ---- 音效（供 GameSystem 调用；无头模拟不经过这里）----
    fun playSFire() = sounds.playSFire(volume)
    fun playLFire() = sounds.playLFire(volume)
    fun playLongShotCharged() = sounds.playLongShotCharged(volume)
    fun playHurt() = sounds.playHurt(volume)
}
