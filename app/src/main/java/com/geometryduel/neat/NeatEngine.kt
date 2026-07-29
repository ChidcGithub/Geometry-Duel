package com.geometryduel.neat

import com.geometryduel.game.actor.PlayerActor
import com.geometryduel.game.engine.PlayerEngine
import kotlin.math.abs

/**
 * NEAT 驱动的玩家引擎：每 skipFrames+1 帧推理一次，跳过帧复用上次决策。
 * 输出 5 路：moveX/moveY（连续，死区 0.05）、shot（>0.5/0.3）、
 * longShot（>0.3/0.15 低门槛鼓励探索大招）、teleport（>0.5/0.3）。
 */
class NeatEngine(genome: Genome, rayCount: Int) : PlayerEngine() {

    private var skipFrames = 2  // 可调：1=30Hz, 2=20Hz(default), 3=15Hz, 5=12Hz

    private var net: NeatNetwork
    private var sensor: VisionSensor
    private var inputs: FloatArray
    private val outputs = FloatArray(5)
    private var skipCounter = 0
    private var engineRayCount = rayCount
    private var currentGenome: Genome? = null
    /** 网络构建缓存（identity 命中即复用）：同代对战双方交替、冠军切换时避免重复拓扑排序+可达性矩阵。 */
    private val cacheGenomes = arrayOfNulls<Genome>(4)
    private val cacheNets = arrayOfNulls<NeatNetwork>(4)
    private var cacheNext = 0

    init {
        this.sensor = VisionSensor(rayCount)
        this.net = NeatNetwork(genome, sensor.inputSize() + 1, 5)
        this.currentGenome = genome
        this.inputs = FloatArray(sensor.inputSize() + 1)
        this.inputs[this.inputs.size - 1] = 1f
    }

    fun reset() {
        skipCounter = 0
        net.reset()
    }

    fun rayCount() = engineRayCount

    /** 当前网络对应的基因组（identity 比较，供调用方跳过重复构建）。 */
    fun genome(): Genome? = currentGenome

    fun setSkipFrames(n: Int) {
        this.skipFrames = n
    }

    fun skipFrames() = skipFrames

    /** 换基因组重建网络（缓存命中则复用），射线数变时重新分配数组并清空缓存 */
    fun setGenome(genome: Genome, rayCount: Int) {
        if (this.engineRayCount != rayCount) {
            this.engineRayCount = rayCount
            this.sensor = VisionSensor(rayCount)
            this.inputs = FloatArray(sensor.inputSize() + 1)
            this.inputs[this.inputs.size - 1] = 1f
            for (i in cacheGenomes.indices) { cacheGenomes[i] = null; cacheNets[i] = null }
            cacheNext = 0
        }
        for (i in cacheGenomes.indices) {
            if (cacheGenomes[i] === genome) {
                this.net = cacheNets[i]!!
                this.currentGenome = genome
                this.skipCounter = 0
                return
            }
        }
        val built = NeatNetwork(genome, sensor.inputSize() + 1, 5)
        cacheGenomes[cacheNext] = genome
        cacheNets[cacheNext] = built
        cacheNext = (cacheNext + 1) % cacheGenomes.size
        this.net = built
        this.currentGenome = genome
        this.skipCounter = 0
    }

    override fun run(player: PlayerActor) {
        if (skipCounter > 0) {
            skipCounter--
            return
        }
        skipCounter = skipFrames

        sensor.sense(player, inputs)
        net.eval(inputs, outputs)

        var mx = outputs[0]
        var my = outputs[1]
        mx = mx.coerceIn(-1f, 1f)
        my = my.coerceIn(-1f, 1f)
        if (abs(mx) < 0.05f) mx = 0f
        if (abs(my) < 0.05f) my = 0f
        operateMove(mx, my)
        operateShotButton(hysteresis(outputs[2], 0.5f, 0.3f, shotButtonPressed))
        operateLongShotButton(hysteresis(outputs[3], 0.3f, 0.15f, longShotButtonPressed))
        operateTeleportButton(hysteresis(outputs[4], 0.5f, 0.3f, teleportButtonPressed))
    }

    companion object {
        private fun hysteresis(v: Float, onThresh: Float, offThresh: Float, current: Boolean): Boolean =
            if (current) v >= offThresh else v > onThresh
    }
}
