package com.geometryduel;

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;

/**
 * GPU / NPU 检测（GL 上下文就绪后调用 detect()）。
 * 桌面端 NPU 固定为 "None (CPU only)"；安卓端根据 SoC 平台与 GL 扩展推断。
 */
public class HardwareInfo {
    public String gpuRenderer;
    public String gpuVendor;
    public String npuInfo;

    private boolean detected;

    public void detect() {
        if (detected) return;
        try {
            gpuRenderer = Gdx.gl.glGetString(GL20.GL_RENDERER);
            gpuVendor = Gdx.gl.glGetString(GL20.GL_VENDOR);
        } catch (Exception e) {
            gpuRenderer = "Unknown";
            gpuVendor = "Unknown";
        }
        npuInfo = detectNpu();
        detected = true;
    }

    private String detectNpu() {
        if (Gdx.app.getType() != ApplicationType.Android) {
            return "None (CPU only)";
        }
        String platform = getProp("ro.board.platform");
        String soc = getProp("ro.soc.model");
        String hardware = getProp("ro.hardware");
        String cpu = getProp("ro.product.cpu.abi");

        String combined = (platform == null ? "" : platform) + " "
                + (soc == null ? "" : soc) + " "
                + (hardware == null ? "" : hardware) + " "
                + (cpu == null ? "" : cpu);

        if (anyContains(combined, "lahaina", "taro", "kalama", "pineapple",
                "sun", "waipio", "kona", "msm", "sdm", "sm", "qcs")) {
            return "Hexagon NPU (Qualcomm)";
        }
        if (anyContains(combined, "mt", "mediatek")) {
            return "APU (MediaTek)";
        }
        if (anyContains(combined, "exynos", "universal")) {
            return "NPU (Samsung Exynos)";
        }
        if (anyContains(combined, "kirin", "hi3", "hi6")) {
            return "Da Vinci NPU (HiSilicon)";
        }
        if (anyContains(combined, "gs", "whitechapel", "tensor")) {
            return "Edge TPU (Google Tensor)";
        }
        if (anyContains(combined, "unisoc", "spreadtrum", "sc")) {
            return "VDSP (Unisoc)";
        }

        String ext = Gdx.gl.glGetString(GL20.GL_EXTENSIONS);
        if (ext != null) {
            if (ext.contains("QCOM")) return "Hexagon NPU (Qualcomm)";
            if (ext.contains("IMG_")) return "PowerVR GPU / No NPU";
        }

        if (platform != null && !platform.isEmpty()) {
            return "Unknown (SoC: " + platform + ")";
        }
        return "Unknown";
    }

    private static String getProp(String key) {
        try {
            String v = System.getProperty(key);
            return v != null ? v.toLowerCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean anyContains(String haystack, String... needles) {
        if (haystack == null) return false;
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
