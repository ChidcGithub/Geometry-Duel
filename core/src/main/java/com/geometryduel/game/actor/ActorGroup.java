package com.geometryduel.game.actor;

import java.util.ArrayList;

/** 还原 pama1234...util.actor.ActorGroup（Center 延迟增删语义）。 */
public class ActorGroup {
    public final int id;
    public int damageCount;
    public ActorGroup enemyGroup;

    public final ArrayList<PlayerActor> players = new ArrayList<PlayerActor>();
    public final ArrayList<ArrowActor> arrows = new ArrayList<ArrowActor>();
    private final ArrayList<PlayerActor> addPlayers = new ArrayList<PlayerActor>();
    private final ArrayList<PlayerActor> removePlayers = new ArrayList<PlayerActor>();
    private final ArrayList<ArrowActor> addArrows = new ArrayList<ArrowActor>();
    private final ArrayList<ArrowActor> removeArrows = new ArrayList<ArrowActor>();

    public ActorGroup(int id) {
        this.id = id;
    }

    public void update() {
        players.removeAll(removePlayers);
        players.addAll(addPlayers);
        removePlayers.clear();
        addPlayers.clear();
        for (int i = 0; i < players.size(); i++) players.get(i).update();

        arrows.removeAll(removeArrows);
        arrows.addAll(addArrows);
        removeArrows.clear();
        addArrows.clear();
        for (int i = 0; i < arrows.size(); i++) arrows.get(i).update();
    }

    public void act() {
        for (int i = 0; i < players.size(); i++) players.get(i).act();
        for (int i = 0; i < arrows.size(); i++) arrows.get(i).act();
    }

    /** addPlayer：先清空再添加（还原原作单玩家语义）。 */
    public void addPlayer(PlayerActor p) {
        removePlayers.addAll(players);
        addPlayers.add(p);
        p.group = this;
    }

    public void removePlayer(PlayerActor p) {
        removePlayers.add(p);
    }

    public void addArrow(ArrowActor a) {
        addArrows.add(a);
        a.group = this;
    }

    public void breakArrow(ArrowActor a) {
        removeArrows.add(a);
    }

    public PlayerActor firstPlayer() {
        return players.isEmpty() ? null : players.get(0);
    }

    public void displayPlayers(com.geometryduel.render.Shapes s) {
        for (int i = 0; i < players.size(); i++) players.get(i).display(s);
    }

    public void displayArrows(com.geometryduel.render.Shapes s) {
        for (int i = 0; i < arrows.size(); i++) arrows.get(i).display(s);
    }
}
