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
    public static final int GLOBAL_INPUTS = 15;  // +1 enemy longbow aim danger direction
    private static final float MAX_SIGHT = 860f;
    private static final float MAX_SPEED = 64f;

    public final int rayCount;
    private final float[] cos, sin;

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
                float t = rayCircle(px, py, dx, dy, enemy.pos.x, enemy.pos.y, 16f);
                if (t < best) {
                    best = t;
                    type = 1;
                    vDanger = -(enemy.vel.x * dx + enemy.vel.y * dy) / MAX_SPEED;
                }
            }
            for (int j = 0; enemy != null && j < enemy.group.arrows.size(); j++) {
                ArrowActor a = enemy.group.arrows.get(j);
                float t = rayCircle(px, py, dx, dy, a.pos.x, a.pos.y, a.collisionRadius + 4f);
                if (t < best) {
                    best = t;
                    type = a.isLethal() ? 3 : 2;
                    vDanger = -(a.vel.x * dx + a.vel.y * dy) / MAX_SPEED;
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
    }

    private static float rayCircle(float px, float py, float dx, float dy,
                                   float cx, float cy, float r) {
        float ox = cx - px, oy = cy - py;
        float proj = ox * dx + oy * dy;
        if (proj < 0f) return MAX_SIGHT;
        float d2 = ox * ox + oy * oy - proj * proj;
        float r2 = r * r;
        if (d2 > r2) return MAX_SIGHT;
        return Math.max(0f, proj - (float) Math.sqrt(r2 - d2));
    }
}
