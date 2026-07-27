package com.geometryduel.neat;

import com.geometryduel.game.actor.ArrowActor;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.state.DamagedState;
import com.geometryduel.game.state.DoTeleportState;
import com.geometryduel.game.state.DrawLongbowState;

/**
 * 射线视觉感知：以 AI 为中心发出 rayCount 条射线，采样边界/敌人/来箭的
 * 最近距离并做类型编码；叠加 7 项全局状态。
 *
 * 每条射线输出一个 [-1,1] 的值：
 *   [-1,-0.5) 普通来箭（负值=危险，越近绝对值越大）
 *   [-0.5, 0) 致命来箭
 *   [ 0, 0.5] 场地边界
 *   ( 0.5, 1] 敌人
 *   0         空视野
 * 全局状态（7）：敌人方位 cos/sin、距离、受击进度、长弓蓄力、传送充能、瞄准角差。
 */
public class VisionSensor {
    public static final int GLOBAL_INPUTS = 7;
    private static final float MAX_SIGHT = 860f; // 640 场地的对角线级视野

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
        return rayCount + GLOBAL_INPUTS;
    }

    /** 感知当前局势写入 out（长度须 >= inputSize()）。 */
    public void sense(PlayerActor self, float[] out) {
        PlayerActor enemy = self.group.enemyGroup.firstPlayer();
        float px = self.pos.x, py = self.pos.y;

        for (int i = 0; i < rayCount; i++) {
            float dx = cos[i], dy = sin[i];

            // 边界（玩家活动范围 16..624）解析求交
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
            int type = 0; // 0 墙 1 敌 2 普通箭 3 致命箭

            if (enemy != null) {
                float t = rayCircle(px, py, dx, dy, enemy.pos.x, enemy.pos.y, 16f);
                if (t < best) {
                    best = t;
                    type = 1;
                }
            }
            for (int j = 0; enemy != null && j < enemy.group.arrows.size(); j++) {
                ArrowActor a = enemy.group.arrows.get(j);
                float t = rayCircle(px, py, dx, dy, a.pos.x, a.pos.y, a.collisionRadius + 4f);
                if (t < best) {
                    best = t;
                    type = a.isLethal() ? 3 : 2;
                }
            }

            float near = Math.max(0f, 1f - Math.min(best, MAX_SIGHT) / MAX_SIGHT);
            switch (type) {
                case 1: out[i] = near * 0.5f + 0.5f; break;
                case 2: out[i] = -(near * 0.5f + 0.5f); break;
                case 3: out[i] = -near * 0.5f; break;
                default: out[i] = near * 0.5f;
            }
        }

        int g = rayCount;
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
        out[g + 5] = self.state instanceof DoTeleportState
                ? Math.min(1f, self.teleportChargedFrameCount / (float) DoTeleportState.CHARGE_REQUIRED) : 0f;
    }

    /** 射线-圆求交，返回最近正距离；未命中返回 MAX_SIGHT。 */
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
