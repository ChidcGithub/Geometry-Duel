package com.geometryduel.neat

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * NEAT 存档：种群 + 冠军 + 世代号 + 创新计数 + 射线数，JSON 存内部存储。
 * 解析失败 / 版本不符 / 射线数不符 → 返回 null（调用方回退全新进化）。
 * （旧 libGDX Json 格式存档无法解析时同样安全回退。）
 */
object NeatStorage {
    private const val TAG = "NeatStorage"
    // v5: 修复无头对局 1 帧判负（players 延迟入列 + 先 transition 后 update），
    //     此前所有存档的适应度均为空对局噪声，训练成果无效，强制重训
    private const val VERSION = 5
    /** 幽灵录像独立版本号。 */
    private const val GHOST_VERSION = 2
    private const val FILE = "neat-ai.json"
    private const val GHOST_FILE = "neat-ghosts.json"

    private lateinit var filesDir: File

    fun init(context: Context) {
        filesDir = context.filesDir
    }

    class SaveData {
        var version = 0
        var rayCount = 0
        var inputCount = 0
        var generation = 0
        var bestFitness = 0f
        var simMatches = 0L
        var nextInnovation = 0
        var nextNode = 0
        var champion: Genome? = null
        var population: ArrayList<Genome>? = null
    }

    fun load(rayCount: Int, inputCount: Int): SaveData? {
        return try {
            val f = File(filesDir, FILE)
            if (!f.exists()) return null
            val root = JSONObject(f.readText())
            val d = SaveData()
            d.version = root.optInt("version")
            d.rayCount = root.optInt("rayCount")
            d.inputCount = root.optInt("inputCount")
            if (d.version != VERSION || d.rayCount != rayCount || d.inputCount != inputCount) {
                Log.i(TAG, "save incompatible, starting fresh")
                return null
            }
            d.generation = root.optInt("generation")
            d.bestFitness = root.optDouble("bestFitness").toFloat()
            d.simMatches = root.optLong("simMatches")
            d.nextInnovation = root.optInt("nextInnovation")
            d.nextNode = root.optInt("nextNode")
            d.champion = if (root.has("champion") && !root.isNull("champion"))
                genomeFromJson(root.getJSONObject("champion")) else null
            val pop = root.optJSONArray("population")
            if (pop == null || pop.length() == 0) {
                Log.i(TAG, "save has no population, starting fresh")
                return null
            }
            val list = ArrayList<Genome>(pop.length())
            for (i in 0 until pop.length()) list.add(genomeFromJson(pop.getJSONObject(i)))
            d.population = list
            d
        } catch (t: Throwable) {
            Log.e(TAG, "load failed, starting fresh", t)
            clear()
            null
        }
    }

    fun save(rayCount: Int, inputCount: Int, generation: Int, bestFitness: Float, simMatches: Long,
             evolver: NeatEvolver, champion: Genome?) {
        try {
            val root = JSONObject()
            root.put("version", VERSION)
            root.put("rayCount", rayCount)
            root.put("inputCount", inputCount)
            root.put("generation", generation)
            root.put("bestFitness", bestFitness.toDouble())
            root.put("simMatches", simMatches)
            root.put("nextInnovation", evolver.counter.nextInnovation)
            root.put("nextNode", evolver.counter.nextNode)
            if (champion != null) root.put("champion", genomeToJson(champion))
            val pop = JSONArray()
            for (g in evolver.population) pop.put(genomeToJson(g))
            root.put("population", pop)
            writeAtomic(File(filesDir, FILE), root.toString())
        } catch (t: Throwable) {
            Log.e(TAG, "save failed", t)
        }
    }

    /** 原子写入：先写临时文件再重命名，防止进程中断留下半个 JSON 损毁全部训练成果。 */
    private fun writeAtomic(target: File, content: String) {
        val tmp = File(filesDir, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    fun clear() {
        try {
            File(filesDir, FILE).delete()
            File(filesDir, GHOST_FILE).delete()
        } catch (ignored: Throwable) {
        }
    }

    // ------------------------------------------------------------ Genome <-> JSON

    private fun genomeToJson(g: Genome): JSONObject {
        val o = JSONObject()
        o.put("mutationPower", g.mutationPower.toDouble())
        o.put("weightMutProb", g.weightMutProb.toDouble())
        o.put("addNodeProb", g.addNodeProb.toDouble())
        o.put("addConnProb", g.addConnProb.toDouble())
        o.put("toggleProb", g.toggleProb.toDouble())
        o.put("resetProb", g.resetProb.toDouble())
        o.put("activationProb", g.activationProb.toDouble())
        o.put("removeProb", g.removeProb.toDouble())
        val nodes = JSONArray()
        for (n in g.nodes) {
            val no = JSONObject()
            no.put("id", n.id)
            no.put("type", n.type)
            no.put("activation", n.activation)
            nodes.put(no)
        }
        o.put("nodes", nodes)
        val conns = JSONArray()
        for (c in g.conns) {
            val co = JSONObject()
            co.put("in", c.`in`)
            co.put("out", c.out)
            co.put("weight", c.weight.toDouble())
            co.put("enabled", c.enabled)
            co.put("innovation", c.innovation)
            conns.put(co)
        }
        o.put("conns", conns)
        return o
    }

    private fun genomeFromJson(o: JSONObject): Genome {
        val g = Genome()
        g.mutationPower = o.optDouble("mutationPower", 0.3).toFloat()
        g.weightMutProb = o.optDouble("weightMutProb", 0.9).toFloat()
        g.addNodeProb = o.optDouble("addNodeProb", 0.25).toFloat()
        g.addConnProb = o.optDouble("addConnProb", 0.14).toFloat()
        g.toggleProb = o.optDouble("toggleProb", 0.06).toFloat()
        g.resetProb = o.optDouble("resetProb", 0.05).toFloat()
        g.activationProb = o.optDouble("activationProb", 0.05).toFloat()
        g.removeProb = o.optDouble("removeProb", 0.04).toFloat()
        val nodes = o.getJSONArray("nodes")
        for (i in 0 until nodes.length()) {
            val no = nodes.getJSONObject(i)
            val n = Genome.NodeGene(no.getInt("id"), no.getInt("type"))
            n.activation = no.optInt("activation", Genome.TANH)
            g.nodes.add(n)
        }
        val conns = o.getJSONArray("conns")
        for (i in 0 until conns.length()) {
            val co = conns.getJSONObject(i)
            val c = Genome.ConnectionGene(
                co.getInt("in"), co.getInt("out"),
                co.getDouble("weight").toFloat(), co.getInt("innovation")
            )
            c.enabled = co.optBoolean("enabled", true)
            g.conns.add(c)
        }
        return g
    }

    // ------------------------------------------------------------ 玩家幽灵录像

    fun loadGhosts(): ArrayList<GhostData> {
        val out = ArrayList<GhostData>()
        try {
            val f = File(filesDir, GHOST_FILE)
            if (!f.exists()) return out
            val root = JSONObject(f.readText())
            if (root.optInt("version") != GHOST_VERSION) return out
            val arr = root.optJSONArray("ghosts") ?: return out
            for (i in 0 until arr.length()) {
                try {
                    val go = arr.getJSONObject(i)
                    val g = GhostData()
                    g.frames = go.getInt("frames")
                    g.moveX = bytesFromJson(go.getJSONArray("moveX"))
                    g.moveY = bytesFromJson(go.getJSONArray("moveY"))
                    g.buttons = bytesFromJson(go.getJSONArray("buttons"))
                    // 过滤损坏/不完整条目，绝不崩溃
                    if (g.frames > 0 && g.moveX.size >= g.frames
                        && g.moveY.size >= g.frames && g.buttons.size >= g.frames
                    ) {
                        out.add(g)
                    }
                } catch (ignored: Throwable) {
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "load ghosts failed", t)
        }
        return out
    }

    fun saveGhosts(ghosts: List<GhostData>) {
        try {
            val root = JSONObject()
            root.put("version", GHOST_VERSION)
            val arr = JSONArray()
            for (g in ghosts) {
                val go = JSONObject()
                go.put("frames", g.frames)
                go.put("moveX", bytesToJson(g.moveX))
                go.put("moveY", bytesToJson(g.moveY))
                go.put("buttons", bytesToJson(g.buttons))
                arr.put(go)
            }
            root.put("ghosts", arr)
            writeAtomic(File(filesDir, GHOST_FILE), root.toString())
        } catch (t: Throwable) {
            Log.e(TAG, "save ghosts failed", t)
        }
    }

    private fun bytesToJson(b: ByteArray): JSONArray {
        val a = JSONArray()
        for (v in b) a.put(v.toInt())
        return a
    }

    private fun bytesFromJson(a: JSONArray): ByteArray {
        val b = ByteArray(a.length())
        for (i in 0 until a.length()) b[i] = a.getInt(i).toByte()
        return b
    }
}
