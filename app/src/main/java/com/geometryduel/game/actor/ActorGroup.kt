package com.geometryduel.game.actor

import com.geometryduel.render.GameRenderer

/** 还原 pama1234...util.actor.ActorGroup（Center 延迟增删语义）。 */
class ActorGroup(val id: Int) {
    var damageCount = 0
    var enemyGroup: ActorGroup? = null

    val players = ArrayList<PlayerActor>()
    val arrows = ArrayList<ArrowActor>()
    private val addPlayers = ArrayList<PlayerActor>()
    private val removePlayers = ArrayList<PlayerActor>()
    private val addArrows = ArrayList<ArrowActor>()
    private val removeArrows = ArrayList<ArrowActor>()

    fun update() {
        players.removeAll(removePlayers)
        players.addAll(addPlayers)
        removePlayers.clear()
        addPlayers.clear()
        for (p in players) p.update()

        arrows.removeAll(removeArrows)
        arrows.addAll(addArrows)
        removeArrows.clear()
        addArrows.clear()
        for (a in arrows) a.update()
    }

    fun act() {
        for (p in players) p.act()
        for (a in arrows) a.act()
    }

    /** addPlayer：先清空再添加（还原原作单玩家语义）。 */
    fun addPlayer(p: PlayerActor) {
        removePlayers.addAll(players)
        addPlayers.add(p)
        p.group = this
    }

    fun removePlayer(p: PlayerActor) {
        removePlayers.add(p)
    }

    fun addArrow(a: ArrowActor) {
        addArrows.add(a)
        a.group = this
    }

    fun breakArrow(a: ArrowActor) {
        removeArrows.add(a)
    }

    fun firstPlayer(): PlayerActor? = players.firstOrNull()

    fun displayPlayers(s: GameRenderer) {
        for (p in players) p.display(s)
    }

    fun displayArrows(s: GameRenderer) {
        for (a in arrows) a.display(s)
    }
}
