package com.geometryduel.game.entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GameState {

    public enum State {
        NOT_STARTED,
        RUNNING,
        PAUSED,
        PLAYER1_WIN,
        PLAYER2_WIN,
        DRAW
    }

    private static final float KNOCKBACK_FORCE = 360f;

    private Player player1;
    private Player player2;
    private State state;
    private float gameTime;
    private float arenaWidth;
    private float arenaHeight;

    private final List<GameEventListener> listeners;
    private final List<ExplosionEffect> explosionEffects;

    public GameState(float arenaWidth, float arenaHeight) {
        this.arenaWidth = arenaWidth;
        this.arenaHeight = arenaHeight;
        this.state = State.NOT_STARTED;
        this.gameTime = 0;
        this.listeners = new ArrayList<>();
        this.explosionEffects = new ArrayList<>();
    }

    public void update(float deltaTime) {
        if (state != State.RUNNING) return;

        gameTime += deltaTime;

        if (player1 != null) player1.update(deltaTime);
        if (player2 != null) player2.update(deltaTime);
        updatePlayerVisuals(deltaTime);
        updateExplosionEffects(deltaTime);

        checkCollisions();

        checkEndCondition();
    }

    private void checkCollisions() {
        if (player1 == null || player2 == null) return;

        checkProjectileClashes(player1.getProjectiles(), player2.getProjectiles());

        List<Projectile> allProjectiles = getProjectiles();
        for (Projectile p : allProjectiles) {
            if (!p.isAlive()) continue;

            Player target = (p.getOwner() == player1) ? player2 : player1;

            Shield shield = target.getActiveShield();
            if (shield != null && shield.isAlive() && shield.blocks(p)) {
                addExplosion(p.getX(), p.getY(), 0xFFFFE08A);
                shield.consume();
                target.cancelLinkedProjectile();
                p.setAlive(false);
                continue;
            }

            if (p.collidesWith(target)) {
                addExplosion((p.getX() + target.getX()) * 0.5f, (p.getY() + target.getY()) * 0.5f, 0xFFFF5E5E);
                target.takeDamage(p.getDamage());
                target.applySlow();
                target.applyKnockback(p.getDirectionX(), p.getDirectionY(), KNOCKBACK_FORCE);
                target.cancelLinkedProjectile();
                p.setAlive(false);

                if (p.getOwner().getAIController() != null) {
                    p.getOwner().getAIController().onDamageDealt(p.getDamage(), this);
                }
                if (target.getAIController() != null) {
                    target.getAIController().onDamageTaken(p.getDamage(), this);
                }

                for (GameEventListener listener : listeners) {
                    listener.onProjectileHit(p, target);
                }
            }

            if (p.isOutOfBounds(arenaWidth, arenaHeight)) {
                p.setAlive(false);
            }
        }

        for (Iterator<Projectile> it = player1.getProjectiles().iterator(); it.hasNext(); ) {
            if (!it.next().isAlive()) it.remove();
        }
        for (Iterator<Projectile> it = player2.getProjectiles().iterator(); it.hasNext(); ) {
            if (!it.next().isAlive()) it.remove();
        }
    }

    private void checkProjectileClashes(List<Projectile> first, List<Projectile> second) {
        for (Projectile p1 : first) {
            if (!p1.isAlive()) continue;

            for (Projectile p2 : second) {
                if (!p2.isAlive()) continue;

                if (p1.collidesWith(p2)) {
                    float explosionX = (p1.getX() + p2.getX()) * 0.5f;
                    float explosionY = (p1.getY() + p2.getY()) * 0.5f;
                    addExplosion(explosionX, explosionY, 0xFFFFB347);
                    p1.setAlive(false);
                    p2.setAlive(false);
                    break;
                }
            }
        }
    }

    private void updatePlayerVisuals(float deltaTime) {
        if (player1 != null && player2 != null) {
            player1.updateVisualState(deltaTime, player2);
            player2.updateVisualState(deltaTime, player1);
        }
    }

    private void updateExplosionEffects(float deltaTime) {
        for (Iterator<ExplosionEffect> it = explosionEffects.iterator(); it.hasNext(); ) {
            ExplosionEffect effect = it.next();
            effect.update(deltaTime);
            if (!effect.isAlive()) {
                it.remove();
            }
        }
    }

    private void checkEndCondition() {
        if (!player1.isAlive() && !player2.isAlive()) {
            setState(State.DRAW);
        } else if (!player1.isAlive()) {
            setState(State.PLAYER2_WIN);
        } else if (!player2.isAlive()) {
            setState(State.PLAYER1_WIN);
        }
    }

    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    public void start() {
        if (player1 != null && player2 != null) {
            state = State.RUNNING;
            for (GameEventListener listener : listeners) {
                listener.onGameStart();
            }
        }
    }

    public void reset() {
        state = State.NOT_STARTED;
        gameTime = 0;
        player1 = null;
        player2 = null;
        explosionEffects.clear();
    }

    public List<Projectile> getProjectiles() {
        List<Projectile> all = new ArrayList<>();
        if (player1 != null) all.addAll(player1.getProjectiles());
        if (player2 != null) all.addAll(player2.getProjectiles());
        return all;
    }

    public void addExplosion(float x, float y, int color) {
        explosionEffects.add(new ExplosionEffect(x, y, color));
    }

    public Player getPlayer1() { return player1; }
    public void setPlayer1(Player player1) { this.player1 = player1; }
    public Player getPlayer2() { return player2; }
    public void setPlayer2(Player player2) { this.player2 = player2; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public float getGameTime() { return gameTime; }
    public float getArenaWidth() { return arenaWidth; }
    public float getArenaHeight() { return arenaHeight; }
    public List<ExplosionEffect> getExplosionEffects() { return explosionEffects; }

    public interface GameEventListener {
        void onGameStart();
        void onProjectileHit(Projectile projectile, Player target);
        void onPlayerDeath(Player player);
    }
}
