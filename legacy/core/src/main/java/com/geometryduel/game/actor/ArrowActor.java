package com.geometryduel.game.actor;

/** 还原 AbstractArrowActor：越界（超出 -halfLength..640+halfLength）即移除。 */
public abstract class ArrowActor extends Actor {
    public final float halfLength;

    protected ArrowActor(float collisionRadius, float halfLength) {
        super(collisionRadius);
        this.halfLength = halfLength;
    }

    /** 长弓组件为致命（即杀），短弓箭为击退。 */
    public abstract boolean isLethal();

    @Override
    public void update() {
        super.update();
        float x = pos.x, y = pos.y, h = halfLength;
        if (x < -h || y < -h || x > h + 640f || y > h + 640f) {
            group.breakArrow(this);
        }
    }
}
