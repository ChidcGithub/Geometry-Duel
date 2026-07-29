package com.geometryduel.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/** 音效管理（SoundPool 原生实现，替代 libGDX audio）。 */
class SoundManager(context: Context) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var sFire = 0
    private var lFire = 0
    private var longShotCharged = 0
    private var lFireHurt = 0

    init {
        sFire = load(context, "audio/GUNMech_Mechanical_12.ogg")
        lFire = load(context, "audio/LASRGun_Plasma Rifle Fire_03.ogg")
        longShotCharged = load(context, "audio/MECHClik_Mine Deploy_02.ogg")
        lFireHurt = load(context, "audio/HIT_METAL_WRENCH_HEAVIEST_02.ogg")
    }

    private fun load(context: Context, path: String): Int {
        return try {
            context.assets.openFd(path).use { pool.load(it, 1) }
        } catch (t: Throwable) {
            Log.e("SoundManager", "load failed: $path", t)
            0
        }
    }

    private fun play(id: Int, volume: Float) {
        if (id != 0) pool.play(id, volume, volume, 0, 0, 1f)
    }

    fun playSFire(volume: Float) = play(sFire, volume * 0.3f)
    fun playLFire(volume: Float) = play(lFire, volume)
    fun playLongShotCharged(volume: Float) = play(longShotCharged, volume)
    fun playHurt(volume: Float) = play(lFireHurt, volume)

    fun release() = pool.release()
}
