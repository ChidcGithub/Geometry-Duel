package com.geometryduel.neat;

import com.geometryduel.game.actor.ArrowActor;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.state.DamagedState;
import com.geometryduel.game.state.DrawLongbowState;

/**
 * 射线视觉感知：以 AI 为中心发出 rayCount 条射线，跟随瞄准角旋转。
 * 每条射线 2 个值：编码距离+类型 + 危险度（靠近AI的速度分量）。
 * 叠加 9 项全局状态。
 */
public class VisionSensor {
    // +6: 自身传送冷却、传送锚点相对向量(x2)、敌人传送标记、敌人速度向量(x2)
    public static final int GLOBAL_INPUTS = 21;
    private static final float MAX_SIGHT = 860f;
    private static final float MAX_SPEED = 64f;

    public final int rayCount;
    private final float[] cos, sin;

    // ---- 箭矢数据复用缓冲：sense 每帧填充一次，射线循环内联投影判定 ----
    private float[] aRelX = new float[16], aRelY = new float[16], aR2 = new float[16];
    private float[] aVX = new float[16], aVY = new float[16];
    private boolean[] aLethal = new boolean[16];

    private void ensureArrowCapacity(int n) {
        if (n <= aRelX.length) return;
        aRelX = new float[n]; aRelY = new float[n]; aR2 = new float[n];
        aVX = new float[n]; aVY = new float[n]; aLethal = new boolean[n];
    }

    public VisionSensor(int rayCount) {
        this.rayCount = rayCount;
        cos = new float[rayCount];
        sin = new float[rayCount];
        for (int i = 0; i < rayCount; i++) {
            float a = i * 6.2831855f / rayCount;
            cos[i] = (float) Math.cos(a);
            sin[i] = (float) Math.sin(a);
        }
    }

    public int inputSize() {
        return rayCount * 2 + GLOBAL_INPUTS;
    }

    public void sense(PlayerActor self, float[] out) {
        PlayerActor enemy = self.group.enemyGroup.firstPlayer();
        float px = self.pos.x, py = self.pos.y;
        float cosAim = (float) Math.cos(self.aimAngle);
        float sinAim = (float) Math.sin(self.aimAngle);

        // ---- 每帧一次：提举敌人与箭矢相对数据（射线循环内只做投影判定）----
        final float eRelX, eRelY, evx, evy;
        int arrowCount = 0;
        if (enemy != null) {
            eRelX = enemy.pos.x - px; eRelY = enemy.pos.y - py;
            evx = enemy.vel.x; evy = enemy.vel.y;
            int n = enemy.group.arrows.size();
            ensureArrowCapacity(n);
            for (int j = 0; j < n; j++) {
                ArrowActor a = enemy.group.arrows.get(j);
                aRelX[j] = a.pos.x - px;
                aRelY[j] = a.pos.y - py;
                float r = a.collisionRadius + 4f;
                aR2[j] = r * r;
                aVX[j] = a.vel.x;
                aVY[j] = a.vel.y;
                aLethal[j] = a.isLethal();
            }
            arrowCount = n;
        } else {
            eRelX = eRelY = evx = evy = 0f;
        }

        for (int i = 0; i < rayCount; i++) {
            float dx = cos[i] * cosAim - sin[i] * sinAim;
            float dy = sin[i] * cosAim + cos[i] * sinAim;

            float tWall;
            if (dx > 0.0001f) tWall = (624f - px) / dx;
            else if (dx < -0.0001f) tWall = (16f - px) / dx;
            else tWall = Float.MAX_VALUE;
            float tY;
            if (dy > 0.0001f) tY = (624f - py) / dy;
            else if (dy < -0.0001f) tY = (16f - py) / dy;
            else tY = Float.MAX_VALUE;
            if (tY < tWall) tWall = tY;

            float best = tWall;
            int type = 0;
            float vDanger = 0f; // 靠近AI的速度分量（正=迫近）

            if (enemy != null) {
                float proj = eRelX * dx + eRelY * dy;
                if (proj >= 0f) {
                    float d2 = eRelX * eRelX + eRelY * eRelY - proj * proj;
                    if (d2 <= 256f) { // 敌方玩家判定半径 16
                        float t = proj - (float) Math.sqrt(256f - d2);
                        if (t < 0f) t = 0f;
                        if (t < best) {
                            best = t;
                            type = 1;
                            vDanger = -(evx * dx + evy * dy) / MAX_SPEED;
                        }
                    }
                }
            }
            for (int j = 0; j < arrowCount; j++) {
                float proj = aRelX[j] * dx + aRelY[j] * dy;
                if (proj < 0f) continue;
                float d2 = aRelX[j] * aRelX[j] + aRelY[j] * aRelY[j] - proj * proj;
                float r2 = aR2[j];
                if (d2 > r2) continue;
                float t = proj - (float) Math.sqrt(r2 - d2);
                if (t < 0f) t = 0f;
                if (t < best) {
                    best = t;
                    type = aLethal[j] ? 3 : 2;
                    vDanger = -(aVX[j] * dx + aVY[j] * dy) / MAX_SPEED;
                }
            }

            float near = Math.max(0f, 1f - Math.min(best, MAX_SIGHT) / MAX_SIGHT);
            int base = i * 2;
            switch (type) {
                case 1: out[base] = near * 0.5f + 0.5f; break;
                case 2: out[base] = -(near * 0.5f + 0.5f); break;
                case 3: out[base] = -near * 0.5f; break;
                default: out[base] = near * 0.5f;
            }
            out[base + 1] = Math.max(-1f, Math.min(1f, vDanger * near));
        }

        int g = rayCount * 2;
        if (enemy != null) {
            float ex = enemy.pos.x - px, ey = enemy.pos.y - py;
            float dist = (float) Math.sqrt(ex * ex + ey * ey);
            out[g] = dist > 0.0001f ? ex / dist : 0f;
            out[g + 1] = dist > 0.0001f ? ey / dist : 0f;
            out[g + 2] = Math.min(1f, dist / 905f);
            float diff = (float) Math.atan2(ey, ex) - self.aimAngle;
            while (diff > 3.1415927f) diff -= 6.2831855f;
            while (diff < -3.1415927f) diff += 6.2831855f;
            out[g + 6] = diff / 3.1415927f;
        } else {
            out[g] = out[g + 1] = out[g + 6] = 0f;
            out[g + 2] = 1f;
        }
        out[g + 3] = self.state.isDamaged()
                ? self.damageRemainingFrameCount / (float) DamagedState.DURATION : 0f;
        out[g + 4] = self.state instanceof DrawLongbowState
                ? Math.min(1f, self.chargedFrameCount / (float) DrawLongbowState.CHARGE_REQUIRED) : 0f;
        out[g + 5] = self.teleportMarked
                ? self.teleportMarkRemaining / (float) PlayerActor.TELEPORT_MARK_DURATION : 0f;
        out[g + 7] = (enemy != null && enemy.state instanceof DrawLongbowState) ? 1f : 0f;
        out[g + 8] = (enemy != null && enemy.state.isDamaged())
                ? enemy.damageRemainingFrameCount / (float) DamagedState.DURATION : 0f;

        // ---- 1.5：新增感知信息 ----
        // 自身位置归一化（0-1）
        out[g + 9] = (px - 16f) / 608f;  // x位置
        out[g + 10] = (py - 16f) / 608f;  // y位置

        // 距离最近墙壁的距离（归一化，越小越危险）
        float distLeft = px - 16f;
        float distRight = 624f - px;
        float distTop = py - 16f;
        float distBottom = 624f - py;
        float minWallDist = Math.min(Math.min(distLeft, distRight), Math.min(distTop, distBottom));
        out[g + 11] = minWallDist / 320f;  // 归一化到0-1

        // 自身速度大小（归一化）
        float speed = (float) Math.sqrt(self.vel.x * self.vel.x + self.vel.y * self.vel.y);
        out[g + 12] = Math.min(1f, speed / 10f);  // 最大速度约10

        // 中心区域标记（1=在中心区域，0=不在）
        // 中心区域定义为200-440范围内
        boolean inCenter = px > 200f && px < 440f && py > 200f && py < 440f;
        out[g + 13] = inCenter ? 1f : 0f;

        // 敌人长弓瞄准危险度：0=不蓄力，>0=敌人正在瞄自己
        if (enemy != null && enemy.state instanceof DrawLongbowState) {
            float want = (float) Math.atan2(py - enemy.pos.y, px - enemy.pos.x);
            float err = enemy.aimAngle - want;
            while (err > 3.1415927f) err -= 6.2831855f;
            while (err < -3.1415927f) err += 6.2831855f;
            out[g + 14] = 1f - Math.abs(err) / 3.1415927f;
        } else {
            out[g + 14] = 0f;
        }

        // ---- 2.0：传送与速度感知补全 ----
        // 自身传送冷却（0=就绪，1=刚回传）——消除"不知道传送何时可用"的盲区
        out[g + 15] = Math.min(1f, self.teleportCooldown / (float) PlayerActor.TELEPORT_COOLDOWN);

        // 传送锚点相对向量（未标记为 0）——让 AI 学会评估回传落点
        // /640 与场地尺度对齐：/320 会让半场外锚点饱和在 ±1，丢失远锚点分辨力
        if (self.teleportMarked) {
            out[g + 16] = clamp1((self.teleportAnchorX - px) / 640f);
            out[g + 17] = clamp1((self.teleportAnchorY - py) / 640f);
        } else {
            out[g + 16] = out[g + 17] = 0f;
        }

        // 敌人传送标记剩余时间比（0=未标记）——预判对手回传时机
        out[g + 18] = (enemy != null && enemy.teleportMarked)
                ? enemy.teleportMarkRemaining / (float) PlayerActor.TELEPORT_MARK_DURATION : 0f;

        // 敌人速度向量（预判走位与箭路，射线危险度只有径向分量）
        if (enemy != null) {
            out[g + 19] = clamp1(enemy.vel.x / PlayerActor.MAX_VX);
            out[g + 20] = clamp1(enemy.vel.y / PlayerActor.MAX_VY);
        } else {
            out[g + 19] = out[g + 20] = 0f;
        }
    }

    private static float clamp1(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }
}
