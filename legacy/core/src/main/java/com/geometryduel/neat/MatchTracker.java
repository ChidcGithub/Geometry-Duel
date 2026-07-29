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

    private ActorGroup group;
    private final ArrayList<ArrowActor> prevArrows = new ArrayList<ArrowActor>();
    private int prevRecallCount;
    private int frame;
    private int lastRecallFrame = -COMBO_WINDOW;
    private boolean enemyWasAlive;
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
    // 1.5 混合回放：关键时刻追踪
    public int keyMomentFrames;  // 关键时刻总帧数
    private int lastKillFrame = -999;  // 上次击杀帧
    private int lastTeleportFrame = -999;
    public int enemyLongbowAimFrames;  // 敌人蓄长弓瞄准自己
    public int longbowChargeFrames;    // 自身蓄长弓帧数（鼓励尝试大招）

    public MatchTracker(ActorGroup group) {
        this.group = group;
    }

    /** 重置所有计数器供复用 */
    public void reset(ActorGroup newGroup) {
        this.group = newGroup;
        prevArrows.clear();
        prevRecallCount = 0;
        frame = 0;
        lastRecallFrame = -COMBO_WINDOW;
        enemyWasAlive = false;
        lastAttackType = 0;
        shotsFired = longShotsFired = teleportsUsed = teleportKills = aimedFrames = campFrames = 0;
        longShotsHit = teleportsDodged = perfectAimFrames = 0;
        teleportLongbowCombos = shortLongbowAlternate = teleportHitDefense = 0;
        activeMoveFrames = chaseFrames = centerFrames = 0;
        for (int i = 0; i < visitedZones.length; i++) visitedZones[i] = false;
        keyMomentFrames = 0;
        lastKillFrame = lastTeleportFrame = -999;
        enemyLongbowAimFrames = 0;
        longbowChargeFrames = 0;
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
                lastTeleportFrame = frame;  // 记录传送时刻 (1.5)
                teleportsUsed += newRecalls;

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
        if (enemyWasAlive && !enemyAliveNow && frame - lastRecallFrame <= 120) {
            teleportHitDefense++;
        }
        if (enemyWasAlive && !enemyAliveNow) {
            longShotsHit++; // 敌方消失=被击杀（长弓是唯一一击必杀方式）
            lastKillFrame = frame;  // 记录击杀时刻 (1.5)
        }
        enemyWasAlive = enemyAliveNow;

        // ---- 大招瞄准追踪 ----
        if (p != null && enemy != null && p.state instanceof DrawLongbowState) {
            longbowChargeFrames++;  // 只要蓄力就给基础奖励
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

        // ---- 关键时刻检测 (1.5) ----
        boolean isKeyMoment = false;

        // 击杀前后30帧
        if (frame - lastKillFrame <= 30) isKeyMoment = true;

        // 传送后30帧
        if (frame - lastTeleportFrame <= 30) isKeyMoment = true;

        // 长弓蓄力期间
        if (p != null && p.state instanceof DrawLongbowState) isKeyMoment = true;

        // 敌人蓄长弓瞄准自己 → 每帧扣分（角落=活靶子）
        if (p != null && enemy != null && enemy.state instanceof DrawLongbowState) {
            float want = (float) Math.atan2(p.pos.y - enemy.pos.y, p.pos.x - enemy.pos.x);
            float err = enemy.aimAngle - want;
            while (err > 3.1415927f) err -= 6.2831855f;
            while (err < -3.1415927f) err += 6.2831855f;
            if (Math.abs(err) < 0.12f) enemyLongbowAimFrames++;
        }

        // 敌方受击期间（反击机会）
        if (enemy != null && enemy.state.isDamaged()) isKeyMoment = true;

        if (isKeyMoment) keyMomentFrames++;
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
        m.keyMomentFrames = keyMomentFrames;
        m.enemyLongbowAimFrames = enemyLongbowAimFrames;
        m.longbowChargeFrames = longbowChargeFrames;

        // 计算位置多样性：访问的不同区域数量 / 9
        int visitedCount = 0;
        for (boolean visited : visitedZones) if (visited) visitedCount++;
        m.positionDiversity = visitedCount / 9f;
    }
}
