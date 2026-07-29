package com.geometryduel.game.actor;

import com.geometryduel.game.GameSystem;
import com.geometryduel.render.Shapes;

/**
 * 长弓箭头（致命大招的尖端，位于第 6 节位置）。
 * 三角箭头：半长/半宽 24，填充 longbowArrow 色。
 */
public class LongbowArrowHead extends LongbowArrowShaft {
    public static final float HEAD_HALF_LEN = 24f, HEAD_HALF_W = 24f;

    public LongbowArrowHead(GameSystem sys) {
        super(sys);
    }

    @Override
    public void display(Shapes s) {
        s.strokeWeight(5f);
        s.stroke(sys.theme().stroke);
        s.doFill();
        s.fill(sys.theme().longbowArrow);
        s.push();
        s.translate(pos.x, pos.y);
        s.rotate(rotationAngle);
        s.line(-halfLength, 0, 0, 0);
        s.quad(0, 0, -HEAD_HALF_LEN, -HEAD_HALF_W, HEAD_HALF_LEN, 0, -HEAD_HALF_LEN, HEAD_HALF_W);
        s.pop();
    }
}
