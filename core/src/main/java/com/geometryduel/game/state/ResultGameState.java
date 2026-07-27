package com.geometryduel.game.state;

import com.geometryduel.game.GameSystem;
import com.geometryduel.render.Shapes;

/**
 * 结算状态（还原 ClientGameResultState）：
 * - 显示"你赢了/你输了"；60 帧后显示"按 X 键重新开始"
 * - 演示模式：180 帧后自动重开；对战模式：60 帧后按 X 重开（回到演示）
 */
public class ResultGameState extends GameSystemState {
    public static final int DURATION = 60;

    public final int winGroup;
    public final boolean playerWon;

    public ResultGameState(GameSystem system, int winGroup, boolean playerWon) {
        super(system);
        system.stateIndex = 3;
        this.winGroup = winGroup;
        this.playerWon = playerWon;
    }

    @Override
    protected void updateSystem() {
        system.myGroup.update();
        system.otherGroup.update();
        system.particles.update();
    }

    @Override
    protected void checkStateTransition() {
        if (system.demoPlay) {
            if (properFrameCount > DURATION * 3) system.requestRestart();
        } else if (properFrameCount > DURATION && system.restartPressed) {
            system.requestRestart();
        }
    }

    @Override
    public void display(Shapes s) {
        system.myGroup.displayPlayers(s);
        system.otherGroup.displayPlayers(s);
        system.particles.display(s);
    }

    @Override
    public float getScore(int groupId) {
        return winGroup == groupId ? 1f : 0f;
    }
}
