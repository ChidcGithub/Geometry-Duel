package com.geometryduel.neat;

import com.geometryduel.game.actor.ArrowActor;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.state.DamagedState;
import com.geometryduel.game.state.DrawLongbowState;

/**
 * 射线视觉感知：以 AI 为中心发出 rayCount 条射线，射线方向跟随 AI 瞄准角旋转，
 * 采样边界/敌人/来箭的最近距离并做类型编码；叠加 9 项全局状态。
 *
 * 每条射线输出一个 [-1,1] 的值：
 *   [-1,-0.5) 普通来箭（负值=危险，越近绝对值越大）
 *   [-0.5, 0) 致命来箭
 *   [ 0, 0.5] 场地边界
 *   ( 0.5, 1] 敌人
 *   0         空视野
 *
 * 全局状态（9）：
 *   0-1  敌人方位 cos/sin
 *   2    敌人距离（归一化）
 *   3    自身受击进度
 *   4    自身长弓蓄力进度
 *   5    自身传送充能剩余
 *   6    瞄准角差（当前瞄准 vs 敌人方向，归一化）
 *   7    敌人是否在蓄长弓（二进制）
 *   8    敌人是否处于受击状态（归一化）
 */
public class VisionSensor {
    public static final int GLOBAL_INPUTS = 9;
    private static final float MAX_SIGHT = 860f;

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

    public void sense(PlayerActor self, float[] out) {
        PlayerActor enemy = self.group.enemyGroup.firstPlayer();
        float px = self.pos.x, py = self.pos.y;

        float cosAim = (float) Math.cos(self.aimAngle);
        float sinAim = (float) Math.sin(self.aimAngle);

        for (int i = 0; i < rayCount; i++) {
            // 射线方向 = 基础方向 旋转 aimAngle（正前方向量与射线对准→开火更有用）
            float dx = cos[i] * cosAim - sin[i] * sinAim;
            float dy = sin[i] * cosAim + cos[i] * sinAim;

            // 边界求交
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

            if (enemy != null) {
                float t = rayCircle(px, py, dx, dy, enemy.pos.x, enemy.pos.y, 16f);
                if (t < best) { best = t; type = 1; }
            }
            for (int j = 0; enemy != null && j < enemy.group.arrows.size(); j++) {
                ArrowActor a = enemy.group.arrows.get(j);
                float t = rayCircle(px, py, dx, dy, a.pos.x, a.pos.y, a.collisionRadius + 4f);
                if (t < best) { best = t; type = a.isLethal() ? 3 : 2; }
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
        // 全局 0-6：与旧版兼容位置不变
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

        // 新增全局 7-8：敌人状态感知
        out[g + 7] = (enemy != null && enemy.state instanceof DrawLongbowState) ? 1f : 0f;
        out[g + 8] = (enemy != null && enemy.state.isDamaged())
                ? enemy.damageRemainingFrameCount / (float) DamagedState.DURATION : 0f;
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
