package com.geometryduel.neat;

import com.geometryduel.game.actor.ActorGroup;
import com.geometryduel.game.actor.ArrowActor;
import com.geometryduel.game.actor.LongbowArrowHead;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.actor.ShortbowArrow;
import com.geometryduel.game.state.DrawLongbowState;
import com.geometryduel.game.state.DamagedState;

import java.util.ArrayList;

public class MatchTracker {
    public static final int COMBO_WINDOW = 300;
    public static final float AIM_TOLERANCE = 0.12f;
    public static final float PERFECT_AIM_TOLERANCE = 0.052f;  // ~3deg

    private final ActorGroup group;
    private final ArrayList<ArrowActor> prevArrows = new ArrayList<ArrowActor>();
    private int prevRecallCount;
    private int frame;
    private int lastRecallFrame = -COMBO_WINDOW;
    private boolean enemyWasAlive;
    private int prevEnemyDamage;
    private int lastAttackType; // 0=none, 1=shortbow, 2=longbow

    // counts
    public int shotsFired;
    public int longShotsFired;
    public int teleportsUsed;
    public int teleportKills;
    public int aimedFrames;
    public int campFrames;
    // 1.3
    public int longShotsHit;
    public int teleportsDodged;
    public int perfectAimFrames;
    // 1.1
    public int teleportLongbowCombos;
    public int shortLongbowAlternate;
    public int teleportHitDefense;
    // 1.4
    public int activeMoveFrames;
    public int chaseFrames;
    public int centerFrames;
    private final boolean[] visitedZones = new boolean[9];  // 3x3区域访问记录

    public MatchTracker(ActorGroup group) {
        this.group = group;
    }

    public void update() {
        frame++;

        ArrayList<ArrowActor> arrows = group.arrows;
        PlayerActor p = group.firstPlayer();
        PlayerActor enemy = group.enemyGroup != null ? group.enemyGroup.firstPlayer() : null;

        // ---- 箭发射检测 ----
        for (int i = 0; i < arrows.size(); i++) {
            ArrowActor a = arrows.get(i);
            if (!prevArrows.contains(a)) {
                if (a instanceof LongbowArrowHead) {
                    longShotsFired++;
                    // 传送+长弓连招：传送后30帧内放长弓
                    if (frame - lastRecallFrame <= 30) teleportLongbowCombos++;
                    // 攻击类型切换检测
                    if (lastAttackType == 1) shortLongbowAlternate++;
                    lastAttackType = 2;
                } else if (a instanceof ShortbowArrow) {
                    shotsFired++;
                    if (lastAttackType == 2) shortLongbowAlternate++;
                    lastAttackType = 1;
                }
            }
        }
        prevArrows.clear();
        prevArrows.addAll(arrows);

        // ---- 传送追踪 ----
        if (p != null) {
            int newRecalls = p.teleportRecallCount - prevRecallCount;
            if (newRecalls > 0) {
                lastRecallFrame = frame;
                teleportsUsed += newRecalls;

                // 传送后120帧内击杀=攻防一体
                if (enemyWasAlive && !isEnemyAlive(enemy)
                        && frame - lastRecallFrame <= 120) {
                    teleportHitDefense++;
                }

                // 传送躲避：传送时附近有敌方箭
                ArrayList<ArrowActor> enemyArrows = group.enemyGroup != null
                        ? group.enemyGroup.arrows : null;
                if (enemyArrows != null) {
                    for (int j = 0; j < enemyArrows.size(); j++) {
                        ArrowActor ea = enemyArrows.get(j);
                        float dx = ea.pos.x - p.pos.x;
                        float dy = ea.pos.y - p.pos.y;
                        if (dx * dx + dy * dy < 40000f) { // within 200px
                            teleportsDodged++;
                            break;
                        }
                    }
                }
            }
            prevRecallCount = p.teleportRecallCount;
        }

        // ---- 敌方击杀 = 长弓命中（长弓=致命） ----
        boolean enemyAliveNow = isEnemyAlive(enemy);
        if (enemyWasAlive && !enemyAliveNow && frame - lastRecallFrame <= COMBO_WINDOW) {
            teleportKills++;
        }
        if (enemyWasAlive && !enemyAliveNow) {
            longShotsHit++; // 敌方消失=被击杀（长弓是唯一一击必杀方式）
        }
        enemyWasAlive = enemyAliveNow;

        // ---- 受击追踪：敌人受击增加=传送躲闪反击 ----
        if (enemy != null && group.enemyGroup != null) {
            int nowDmg = group.enemyGroup.damageCount;
            if (nowDmg > prevEnemyDamage && frame - lastRecallFrame <= 120) {
                // 传送后反击敌人成功
            }
            prevEnemyDamage = nowDmg;
        }

        // ---- 大招瞄准追踪 ----
        if (p != null && enemy != null && p.state instanceof DrawLongbowState) {
            float want = (float) Math.atan2(enemy.pos.y - p.pos.y, enemy.pos.x - p.pos.x);
            float err = p.aimAngle - want;
            while (err > 3.1415927f) err -= 6.2831855f;
            while (err < -3.1415927f) err += 6.2831855f;
            float absErr = Math.abs(err);
            if (absErr < AIM_TOLERANCE) aimedFrames++;
            if (absErr < PERFECT_AIM_TOLERANCE) perfectAimFrames++;
        }

        // ---- 靠墙检测 ----
        if (p != null) {
            float x = p.pos.x, y = p.pos.y;
            // 缩小安全区域：从64-576改为100-540
            if (x < 100f || x > 540f || y < 100f || y > 540f) campFrames++;
            // 角落特别惩罚：距离任意角落<80px
            if (x < 80f || x > 560f) {
                if (y < 80f || y > 560f) campFrames += 2;  // 角落双倍计数
            }

            // 主动移动检测：速度>1.0
            float speed = (float) Math.sqrt(p.vel.x * p.vel.x + p.vel.y * p.vel.y);
            if (speed > 1.0f) activeMoveFrames++;

            // 追击检测：向敌人方向移动
            if (enemy != null && speed > 0.5f) {
                float toEnemyX = enemy.pos.x - x;
                float toEnemyY = enemy.pos.y - y;
                float dist = (float) Math.sqrt(toEnemyX * toEnemyX + toEnemyY * toEnemyY);
                if (dist > 0.0001f) {
                    float dot = (p.vel.x * toEnemyX + p.vel.y * toEnemyY) / dist;
                    if (dot > 0.5f) chaseFrames++;  // 向敌人方向移动
                }
            }

            // 中心区域检测：在200-440范围内
            if (x > 200f && x < 440f && y > 200f && y < 440f) centerFrames++;

            // 位置多样性：记录访问的3x3区域
            int zoneX = x < 213f ? 0 : (x < 426f ? 1 : 2);
            int zoneY = y < 213f ? 0 : (y < 426f ? 1 : 2);
            int zoneIndex = zoneY * 3 + zoneX;
            visitedZones[zoneIndex] = true;
        }
    }

    private static boolean isEnemyAlive(PlayerActor enemy) {
        return enemy != null && !(enemy.state instanceof DamagedState && enemy.damageRemainingFrameCount <= 1);
    }

    public void fill(MatchStats m) {
        m.shotsFired = shotsFired;
        m.longShotsFired = longShotsFired;
        m.teleportsUsed = teleportsUsed;
        m.teleportKills = teleportKills;
        m.aimedFrames = aimedFrames;
        m.campFrames = campFrames;
        m.longShotsHit = longShotsHit;
        m.teleportsDodged = teleportsDodged;
        m.perfectAimFrames = perfectAimFrames;
        m.teleportLongbowCombos = teleportLongbowCombos;
        m.shortLongbowAlternate = shortLongbowAlternate;
        m.teleportHitDefense = teleportHitDefense;
        m.activeMoveFrames = activeMoveFrames;
        m.chaseFrames = chaseFrames;
        m.centerFrames = centerFrames;

        // 计算位置多样性：访问的不同区域数量 / 9
        int visitedCount = 0;
        for (boolean visited : visitedZones) if (visited) visitedCount++;
        m.positionDiversity = visitedCount / 9f;
    }
}
