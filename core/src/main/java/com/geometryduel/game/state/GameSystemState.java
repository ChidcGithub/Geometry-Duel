package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.render.Shapes;

/** 对局系统状态基类（还原 ServerGameSystemState / ClientGameSystemState）。 */
public abstract class GameSystemState {
    public final GameSystem system;
    public int properFrameCount;

    protected GameSystemState(GameSystem system) {
        this.system = system;
    }

    public final void update() {
        checkStateTransition();
        properFrameCount++;
        updateSystem();
    }

    protected abstract void updateSystem();

    protected abstract void checkStateTransition();

    /** 世界空间绘制（displaySystem）。 */
    public abstract void display(Shapes s);

    public float getScore(int groupId) {
        return 0f;
    }
}
