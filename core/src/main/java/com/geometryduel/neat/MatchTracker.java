package com.geometryduel.neat;

import com.geometryduel.game.actor.ActorGroup;
import com.geometryduel.game.actor.ArrowActor;
import com.geometryduel.game.actor.LongbowArrowHead;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.actor.ShortbowArrow;

import java.util.ArrayList;

/**
 * 逐帧行为追踪器：统计某一阵营 AI 的技能使用次数（短箭 / 长箭 / 传送），
 * 供 NEAT 适应度的行为奖励（reward shaping），引导网络学会使用大招与闪避。
 *
 * 用法：对局开始时构造，GameSystem.update() 之后每帧调 update()，结束后 fill()。
 */
public class MatchTracker {
    private final ActorGroup group;
    private final ArrayList<ArrowActor> prevArrows = new ArrayList<ArrowActor>();
    private int prevRecallCount;

    public int shotsFired;
    public int longShotsFired;
    public int teleportsUsed;

    public MatchTracker(ActorGroup group) {
        this.group = group;
    }

    /** 每帧调用一次（在 GameSystem.update 之后）。 */
    public void update() {
        // 新出现的箭 = 本帧发射（长弓一次发射 5 节箭杆+1 箭头，只数箭头）
        ArrayList<ArrowActor> arrows = group.arrows;
        for (int i = 0; i < arrows.size(); i++) {
            ArrowActor a = arrows.get(i);
            if (!prevArrows.contains(a)) {
                if (a instanceof LongbowArrowHead) longShotsFired++;
                else if (a instanceof ShortbowArrow) shotsFired++;
            }
        }
        prevArrows.clear();
        prevArrows.addAll(arrows);

        // 成功回传（瞬移）次数：读取 PlayerActor 的累计计数差值
        PlayerActor p = group.firstPlayer();
        if (p != null) {
            teleportsUsed += p.teleportRecallCount - prevRecallCount;
            prevRecallCount = p.teleportRecallCount;
        }
    }

    public void fill(MatchStats m) {
        m.shotsFired = shotsFired;
        m.longShotsFired = longShotsFired;
        m.teleportsUsed = teleportsUsed;
    }
}
