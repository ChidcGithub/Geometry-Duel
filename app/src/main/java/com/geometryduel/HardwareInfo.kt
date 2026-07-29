package com.geometryduel

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.util.Log

/**
 * GPU / NPU 检测。
 * GPU 通过临时 EGL14 pbuffer 上下文查询 GL_RENDERER/GL_VENDOR（后台线程一次性执行）；
 * NPU 根据 SoC 平台与 GL 扩展推断。
 */
object HardwareInfo {
    private const val TAG = "HardwareInfo"

    @Volatile var gpuRenderer: String = "Unknown"
        private set
    @Volatile var gpuVendor: String = "Unknown"
        private set
    @Volatile var npuInfo: String = "Unknown"
        private set

    @Volatile private var detected = false

    /** 后台线程执行一次性检测；多次调用只生效一次。 */
    @Synchronized
    fun detectAsync() {
        if (detected) return
        detected = true
        Thread({
            var extensions = ""
            try {
                val gl = queryGlStrings()
                if (gl != null) {
                    gpuRenderer = gl[0]
                    gpuVendor = gl[1]
                    extensions = gl[2]
                }
            } catch (t: Throwable) {
                Log.w(TAG, "GPU detect failed", t)
            }
            npuInfo = detectNpu(extensions)
        }, "hardware-detect").apply { isDaemon = true }.start()
    }

    /** 创建最小 EGL 上下文读取 GL 字符串；失败返回 null。 */
    private fun queryGlStrings(): Array<String>? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        if (!EGL14.eglInitialize(display, null, 0, null, 0)) return null
        var surface: android.opengl.EGLSurface? = null
        var context: android.opengl.EGLContext? = null
        try {
            val attrs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attrs, 0, configs, 0, 1, num, 0) || num[0] < 1) return null
            val config = configs[0]
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null
            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
            val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
            val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            return arrayOf(renderer, vendor, ext)
        } catch (t: Throwable) {
            return null
        } finally {
            EGL14.eglMakeCurrent(
                display,
                EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (surface != null) EGL14.eglDestroySurface(display, surface)
            if (context != null) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    private fun detectNpu(glExtensions: String): String {
        val platform = getProp("ro.board.platform")
        val soc = getProp("ro.soc.model")
        val hardware = getProp("ro.hardware")
        val cpu = getProp("ro.product.cpu.abi")

        val combined = listOfNotNull(platform, soc, hardware, cpu).joinToString(" ")

        if (anyContains(combined, "lahaina", "taro", "kalama", "pineapple",
                "sun", "waipio", "kona", "msm", "sdm", "sm", "qcs")) {
            return "Hexagon NPU (Qualcomm)"
        }
        if (anyContains(combined, "mt", "mediatek")) return "APU (MediaTek)"
        if (anyContains(combined, "exynos", "universal")) return "NPU (Samsung Exynos)"
        if (anyContains(combined, "kirin", "hi3", "hi6")) return "Da Vinci NPU (HiSilicon)"
        if (anyContains(combined, "gs", "whitechapel", "tensor")) return "Edge TPU (Google Tensor)"
        if (anyContains(combined, "unisoc", "spreadtrum", "sc")) return "VDSP (Unisoc)"

        if (glExtensions.contains("QCOM")) return "Hexagon NPU (Qualcomm)"
        if (glExtensions.contains("IMG_")) return "PowerVR GPU / No NPU"

        if (!platform.isNullOrEmpty()) return "Unknown (SoC: $platform)"
        return "Unknown"
    }

    private fun getProp(key: String): String? {
        return try {
            val sp = Class.forName("android.os.SystemProperties")
            val get = sp.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String)?.lowercase()?.ifEmpty { null }
        } catch (t: Throwable) {
            null
        }
    }

    private fun anyContains(haystack: String, vararg needles: String): Boolean {
        for (n in needles) if (haystack.contains(n)) return true
        return false
    }
}
