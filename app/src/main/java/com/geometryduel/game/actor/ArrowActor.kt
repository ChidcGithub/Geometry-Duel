package com.geometryduel.game.actor

/** 还原 AbstractArrowActor：越界（超出 -halfLength..640+halfLength）即移除。 */
abstract class ArrowActor(collisionRadius: Float, val halfLength: Float) : Actor(collisionRadius) {

    /** 长弓组件为致命（即杀），短弓箭为击退。 */
    abstract fun isLethal(): Boolean

    override fun update() {
        super.update()
        val x = pos.x
        val y = pos.y
        val h = halfLength
        if (x < -h || y < -h || x > h + 640f || y > h + 640f) {
            group?.breakArrow(this)
        }
    }
}
