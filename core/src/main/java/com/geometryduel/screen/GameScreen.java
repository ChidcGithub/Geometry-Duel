package com.geometryduel.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.geometryduel.GeometryDuelGame;
import com.geometryduel.game.GameSystem;
import com.geometryduel.game.InputData;
import com.geometryduel.game.actor.PlayerActor;
import com.geometryduel.game.engine.PlayerEngine;
import com.geometryduel.neat.Genome;
import com.geometryduel.neat.GhostRecorder;
import com.geometryduel.neat.MatchStats;
import com.geometryduel.neat.MatchTracker;
import com.geometryduel.neat.NeatEngine;
import com.geometryduel.game.state.PlayGameState;
import com.geometryduel.game.state.ResultGameState;
import com.geometryduel.game.state.StartGameState;
import com.geometryduel.ui.TextButton;

/**
 * 对战界面（还原 state0002.Game + DuelAndroidCtrl）：
 * - 固定 60fps 逻辑步进；世界 640×640 居中显示，支持震屏
 * - 桌面：方向键移动，Z 短弓，X 长弓，C 传送，P 暂停
 * - 安卓：左下虚拟摇杆移动/瞄准，右下 Z/X/C 触控按钮
 * - 流程：演示(AI 对战+说明窗) → 按 Z 开战 → 结算 → 按 X 回演示
 */
public class GameScreen extends ScreenAdapter {
    protected final GeometryDuelGame app;
    protected final InputData input = new InputData();
    protected GameSystem system;

    protected final FitViewport worldVp;
    protected final ScreenViewport uiVp;
    protected final GlyphLayout layout = new GlyphLayout();
    private final Vector3 touch = new Vector3();

    private static final float STEP = 1f / 60f;
    private float accumulator;
    protected boolean paused;
    private boolean matchReported;
    /** 敌方为 NEAT 冠军时逐帧统计其技能使用（供实战上报）。 */
    private MatchTracker aiTracker;
    /** 玩家行为录制（非演示局），结算时作为幽灵陪练上报训练器。 */
    private GhostRecorder ghostRecorder;

    // 触控
    private int joyPointer = -1, zPointer = -1, xPointer = -1, cPointer = -1;
    private float joyBaseX, joyBaseY, joyKnobX, joyKnobY, joyR;
    private float zX, zY, zR, xX, xY, xR, cX, cY, cR;
    protected TextButton backBtn, pauseBtn;

    public GameScreen(GeometryDuelGame app) {
        this.app = app;
        OrthographicCamera worldCam = new OrthographicCamera();
        worldCam.setToOrtho(true, 640, 640);
        worldVp = new FitViewport(640, 640, worldCam);
        uiVp = new ScreenViewport();
        backBtn = new TextButton("Back", 0, 0, 120, 48);
        pauseBtn = new TextButton("Pause", 0, 0, 120, 48);
        backBtn.style = TextButton.STYLE_CONTAINER;
        pauseBtn.style = TextButton.STYLE_CONTAINER;
        newGame(true, true);
    }

    /** 玩家对战进行中（非演示局）：供应用层判断息屏恢复后是否保持训练暂停。 */
    public boolean isBattleActive() {
        return system != null && !system.demoPlay;
    }

    /** 当前对局难度（教学模式覆盖）。 */
    protected float currentLevel() {
        return 1.0f;
    }

    /** 开新局钩子（教学模式用于难度递增）。 */
    protected void onNewGame(boolean demo) {
    }

    public void newGame(boolean demo, boolean instruction) {
        if (!demo) onNewGame(false);
        GameSystem.EngineFactory engineA = null, engineB = null;
        if (app.trainer != null) {
            if (demo) {
                // 演示：冠军（若有）vs 规则 AI
                final Genome champ = app.trainer.currentChampion();
                if (champ != null) engineA = neatFactory(champ);
            } else {
                // 玩家对战：按 style 选择对手风格；对局期间暂停后台训练
                app.trainer.setPaused(true);
                if (app.opponentStyle >= 0) {
                    final Genome styleGenome = app.trainer.styleChampion(app.opponentStyle);
                    if (styleGenome != null) engineB = neatFactory(styleGenome);
                }
            }
        }
        matchReported = false;
        system = new GameSystem(app, demo, instruction, currentLevel(), input,
                engineA, engineB, false, null);
        aiTracker = (!demo && engineB != null) ? new MatchTracker(system.otherGroup) : null;
        ghostRecorder = !demo ? new GhostRecorder() : null;
        input.isZPressed = input.isXPressed = input.isCPressed = false;
        accumulator = 0;
    }

    private GameSystem.EngineFactory neatFactory(final Genome genome) {
        return new GameSystem.EngineFactory() {
            @Override
            public PlayerEngine create(GameSystem sys) {
                NeatEngine e = new NeatEngine(genome, app.visionRays);
                e.setSkipFrames(app.aiSpeed + 1);
                return e;
            }
        };
    }

    /** 对局结束：向训练器上报实战表现并恢复后台训练。 */
    private void reportMatchResult() {
        if (app.trainer == null) return;
        ResultGameState rs = (ResultGameState) system.currentState;
        MatchStats ms = new MatchStats();
        ms.aiWon = !rs.playerWon;
        ms.frames = system.frameCount;
        ms.hitsDealt = system.myGroup.damageCount;    // 人类受击 = AI 命中
        ms.hitsTaken = system.otherGroup.damageCount; // AI 受击
        if (aiTracker != null) aiTracker.fill(ms);    // AI 技能使用统计
        app.trainer.reportRealMatch(ms);
        // 玩家行为录像入库：成为后续训练的幽灵陪练
        if (ghostRecorder != null) app.trainer.addGhost(ghostRecorder.build());
        app.trainer.setPaused(false);
    }

    @Override
    public void show() {
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new DuelInput());
    }

    // ------------------------------------------------------------ 输入

    private class DuelInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            touch.set(screenX, screenY, 0);
            uiVp.unproject(touch);
            float x = touch.x, y = touch.y;

            if (backBtn.contains(x, y)) { back(); return true; }
            if (pauseBtn.contains(x, y)) { paused = !paused; return true; }
            if (dist2(x, y, zX, zY) <= zR * zR) { zPointer = pointer; input.isZPressed = true; return true; }
            if (dist2(x, y, xX, xY) <= xR * xR) { xPointer = pointer; input.isXPressed = true; return true; }
            if (dist2(x, y, cX, cY) <= cR * cR) { cPointer = pointer; input.isCPressed = true; return true; }

            // 演示中轻触其他区域：显示/隐藏说明窗（还原 Game.mousePressed）
            if (system.demoPlay) {
                system.showInstruction = !system.showInstruction;
                return true;
            }
            // 左半屏：摇杆
            if (x < uiVp.getWorldWidth() / 2f) {
                joyPointer = pointer;
                joyKnobX = joyBaseX;
                joyKnobY = joyBaseY;
                updateJoystick(x, y);
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (pointer == joyPointer) {
                touch.set(screenX, screenY, 0);
                uiVp.unproject(touch);
                updateJoystick(touch.x, touch.y);
                return true;
            }
            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (pointer == joyPointer) { joyPointer = -1; input.clearTouch(); }
            else if (pointer == zPointer) { zPointer = -1; input.isZPressed = false; }
            else if (pointer == xPointer) { xPointer = -1; input.isXPressed = false; }
            else if (pointer == cPointer) { cPointer = -1; input.isCPressed = false; }
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { back(); return true; }
            return false;
        }
    }

    private void back() {
        if (app.trainer != null) app.trainer.setPaused(false);
        app.setScreen(new MenuScreen(app));
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joyBaseX, dy = y - joyBaseY;
        float mag = (float) Math.sqrt(dx * dx + dy * dy);
        float cl = Math.min(mag, joyR);
        if (mag > 0.0001f) {
            joyKnobX = joyBaseX + dx / mag * cl;
            joyKnobY = joyBaseY + dy / mag * cl;
        }
        // 还原 targetTouchMoved：方向归一（UI 为 y 向上，世界 y 向下，故 dy 取反）
        input.targetTouchMoved(dx, -dy, cl);
    }

    private void pollKeyboard() {
        input.isUpPressed = Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W);
        input.isDownPressed = Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S);
        input.isLeftPressed = Gdx.input.isKeyPressed(Input.Keys.LEFT) || Gdx.input.isKeyPressed(Input.Keys.A);
        input.isRightPressed = Gdx.input.isKeyPressed(Input.Keys.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.D);
        if (joyPointer < 0) {
            input.dx = 0;
            input.dy = 0;
        }
        if (zPointer < 0) input.isZPressed = Gdx.input.isKeyPressed(Input.Keys.Z) || Gdx.input.isKeyPressed(Input.Keys.J);
        if (xPointer < 0) input.isXPressed = Gdx.input.isKeyPressed(Input.Keys.X) || Gdx.input.isKeyPressed(Input.Keys.K);
        if (cPointer < 0) input.isCPressed = Gdx.input.isKeyPressed(Input.Keys.C) || Gdx.input.isKeyPressed(Input.Keys.L);
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) paused = !paused;
    }

    // ------------------------------------------------------------ 逻辑

    private void step() {
        pollKeyboard();
        if (system.demoPlay && input.isZPressed) {
            newGame(false, false);
            return;
        }
        system.restartPressed = input.isXPressed;
        system.update();
        if (aiTracker != null) aiTracker.update();
        // 逐帧录制玩家操作（仅对战状态，与 act 调用时机对齐）
        if (ghostRecorder != null && system.currentState instanceof PlayGameState) {
            PlayerActor human = system.myGroup.firstPlayer();
            if (human != null) ghostRecorder.frame(human.engine);
        }
        if (!system.demoPlay && !matchReported && system.currentState instanceof ResultGameState) {
            matchReported = true;
            reportMatchResult();
        }
        if (system.consumeRestart()) {
            newGame(true, true);
        }
    }

    // ------------------------------------------------------------ 渲染

    @Override
    public void render(float delta) {
        if (!paused) {
            accumulator += Math.min(delta, 0.1f);
            int steps = 0;
            while (accumulator >= STEP && steps < 3) {
                step();
                accumulator -= STEP;
                steps++;
            }
            if (steps == 3) accumulator = 0;
        }

        layoutTouchControls();

        Gdx.gl.glClearColor(app.theme.background.r, app.theme.background.g, app.theme.background.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        renderWorld();
        renderUiShapes();
        renderUiText();
    }

    private void renderWorld() {
        worldVp.apply();
        OrthographicCamera cam = (OrthographicCamera) worldVp.getCamera();
        // 屏幕震动：随机偏移，随帧衰减 0.8333（还原 ClientGameSystem.display）
        float shake = system.screenShakeValue;
        if (shake > 0f) {
            cam.position.x = 320f + system.random(-shake, shake);
            cam.position.y = 320f + system.random(-shake, shake);
            cam.update();
            system.screenShakeValue -= 0.8333333f;
        }
        app.shapes.begin(cam);
        system.display(app.shapes);
        app.shapes.end();
        if (shake > 0f) {
            cam.position.set(320f, 320f, 0f);
            cam.update();
        }
    }

    private void renderUiShapes() {
        uiVp.apply();
        app.shapes.begin(uiVp.getCamera());
        backBtn.draw(app.shapes, app.theme);
        pauseBtn.draw(app.shapes, app.theme);
        drawTouchControls(app.shapes);
        if (system.demoPlay && system.showInstruction) drawInstructionPanel(app.shapes);
        drawExtraUiShapes(app.shapes);
        app.shapes.end();
    }

    /** 附加 UI 形状层钩子（教学模式跳过按钮等）。 */
    protected void drawExtraUiShapes(com.geometryduel.render.Shapes s) {
    }

    private void drawTouchControls(com.geometryduel.render.Shapes s) {
        if (!app.isAndroid) return;
        // 摇杆
        s.noFill();
        s.stroke(app.theme.stroke, 100);
        s.strokeWeight(2f);
        s.circle(joyBaseX, joyBaseY, joyR * 2f);
        s.doFill();
        s.fill(app.theme.stroke, 140);
        s.noStroke();
        s.filledCircle(joyKnobX, joyKnobY, joyR);
        s.doStroke();
        // Z/X/C 按钮
        drawCircleButton(s, zX, zY, zR, input.isZPressed);
        drawCircleButton(s, xX, xY, xR, input.isXPressed);
        drawCircleButton(s, cX, cY, cR, input.isCPressed);
        // C 键外圈：传送标记倒计时环（冷却中显示灰色冷却环）
        PlayerActor human = system.myGroup.firstPlayer();
        if (human != null && human.teleportMarked) {
            float progress = human.teleportMarkRemaining / (float) PlayerActor.TELEPORT_MARK_DURATION;
            s.noFill();
            s.stroke(human.teleportMarkRemaining < 180 ? app.theme.longbowEffect : app.theme.teleportEffect);
            s.strokeWeight(6f);
            s.arc(cX, cY, cR + 8f, 90f, progress * 360f);
        } else if (human != null && human.teleportCooldown > 0) {
            float progress = human.teleportCooldown / (float) PlayerActor.TELEPORT_COOLDOWN;
            s.noFill();
            s.stroke(app.theme.stroke, 120);
            s.strokeWeight(6f);
            s.arc(cX, cY, cR + 8f, 90f, progress * 360f);
        }
    }

    private void drawCircleButton(com.geometryduel.render.Shapes s, float cx, float cy, float r, boolean pressed) {
        s.noFill();
        s.stroke(app.theme.stroke, pressed ? 255 : 140);
        s.strokeWeight(2f);
        s.circle(cx, cy, r * 2f);
    }

    private void renderUiText() {
        app.batch.begin();
        app.batch.setProjectionMatrix(uiVp.getCamera().combined);
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float unit = h / 640f;

        backBtn.drawText(app.batch, app.font, app.theme, 1.2f * unit);
        pauseBtn.drawText(app.batch, app.font, app.theme, 1.2f * unit);
        if (app.isAndroid) {
            drawCircleLabel("Z", zX, zY, zR, 2.4f * unit);
            drawCircleLabel("X", xX, xY, xR, 2.4f * unit);
            drawCircleLabel("C", cX, cY, cR, 1.6f * unit);
        }

        if (system.currentState instanceof StartGameState) {
            StartGameState st = (StartGameState) system.currentState;
            int n = st.displayNumber();
            if (n > 0) {
                app.font.getData().setScale(6f * unit);
                layout.setText(app.font, String.valueOf(n));
                app.font.setColor(app.theme.ring);
                app.font.draw(app.batch, String.valueOf(n), (w - layout.width) / 2f, (h + layout.height) / 2f);
            }
        } else if (system.currentState instanceof PlayGameState) {
            PlayGameState st = (PlayGameState) system.currentState;
            if (st.properFrameCount < PlayGameState.MESSAGE_DURATION) {
                float alpha = 1f - st.properFrameCount / (float) PlayGameState.MESSAGE_DURATION;
                app.font.getData().setScale(4f * unit);
                layout.setText(app.font, "Go!!");
                app.font.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, alpha);
                app.font.draw(app.batch, "Go!!", (w - layout.width) / 2f, (h + layout.height) / 2f);
            }
        } else if (system.currentState instanceof ResultGameState && !system.demoPlay) {
            ResultGameState st = (ResultGameState) system.currentState;
            String msg = st.playerWon ? "You Win!" : "You Lose!";
            app.font.getData().setScale(4f * unit);
            layout.setText(app.font, msg);
            app.font.setColor(app.theme.text);
            app.font.draw(app.batch, msg, (w - layout.width) / 2f, h / 2f - unit * 10f);
            if (st.properFrameCount > ResultGameState.DURATION) {
                String hint = "Press X to Restart";
                app.font.getData().setScale(1.6f * unit);
                layout.setText(app.font, hint);
                app.font.draw(app.batch, hint, (w - layout.width) / 2f, h / 2f + unit * 40f);
            }
            // 左下角：AI 训练进度（冠军胜率/代速率为可比指标，替代失真的原始适应度）
            if (app.trainer != null) {
                float wr = app.trainer.championWinRate();
                String prog = "AI Gen " + app.trainer.generation()
                        + "  WR " + (wr < 0 ? "--" : Math.round(wr * 100) + "%")
                        + "  " + String.format("%.1f", app.trainer.genRate()) + " g/s"
                        + "  Ghosts " + app.trainer.ghostCount();
                app.font.getData().setScale(1.2f * unit);
                layout.setText(app.font, prog);
                app.font.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 0.55f);
                app.font.draw(app.batch, prog, 8f * unit, h - 8f * unit);
                app.font.setColor(app.theme.text);
            }
        }

        if (paused) {
            app.font.getData().setScale(4f * unit);
            layout.setText(app.font, "Paused");
            app.font.setColor(app.theme.text);
            app.font.draw(app.batch, "Paused", (w - layout.width) / 2f, (h + layout.height) / 2f);
        }

        if (system.demoPlay && system.showInstruction) {
            drawInstructionText(w, h, unit);
        }

        drawExtraHud(w, h, unit);

        app.font.getData().setScale(1f);
        app.font.setColor(app.theme.text);
        app.batch.end();
    }

    /** 说明窗（还原 DemoInfo 中文版内容）。 */
    private void drawInstructionPanel(com.geometryduel.render.Shapes s) {
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float pw = Math.min(w, h) * 0.9f, ph = pw * 460f / 576f;
        float px = (w - pw) / 2f, py = (h - ph) / 2f;
        s.doFill();
        s.fill(app.theme.background, 230);
        s.stroke(app.theme.stroke);
        s.strokeWeight(2f);
        s.rect(px, py, pw, ph);
    }

    private void drawInstructionText(float w, float h, float unit) {
        float pw = Math.min(w, h) * 0.9f;
        float px = (w - pw) / 2f, py = (h - pw * 460f / 576f) / 2f;
        float u = pw / 576f; // DemoInfo 以 576x460 绘制
        app.font.setColor(app.theme.primary);
        text("Geometry Duel!", px + 180 * u, py + 50 * u, 3f * u);
        app.font.setColor(app.theme.text);
        if (app.isAndroid) {
            text("   Z Button:", px + 60 * u, py + 120 * u, 2f * u);
            text("   X Button:", px + 60 * u, py + 160 * u, 2f * u);
            text("Left Touch:", px + 60 * u, py + 210 * u, 2f * u);
            text("Normal Attack", px + 300 * u, py + 120 * u, 2f * u);
            text("Deadly Attack", px + 300 * u, py + 160 * u, 2f * u);
            text("Move or Aim", px + 300 * u, py + 210 * u, 2f * u);
            text("- Press Z to Start -", px + 160 * u, py + 320 * u, 2f * u);
            text("(Tap to Show/Hide)", px + 160 * u, py + 360 * u, 2f * u);
            app.font.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 192 / 255f);
            text("Made by FAL! Android port by Pama1234!", px + 20 * u, py + 400 * u, 1f * u);
            text("Unofficial Remake", px + 20 * u, py + 420 * u, 1f * u);
        } else {
            text("    Z Key:", px + 180 * u, py + 120 * u, 1.4f * u);
            text("    X Key:", px + 180 * u, py + 190 * u, 1.4f * u);
            text("  Arrows:", px + 180 * u, py + 265 * u, 1.4f * u);
            text("Normal Attack\n (Auto-aim)", px + 300 * u, py + 120 * u, 1.4f * u);
            text("Deadly Attack\n (Manual aim,\n  charge required)", px + 300 * u, py + 190 * u, 1.4f * u);
            text("Move\n (or aim while charging)", px + 300 * u, py + 265 * u, 1.4f * u);
            text("- Press Z to Start -", px + 192 * u, py + 350 * u, 1.4f * u);
            text("(Click to Show/Hide)", px + 192 * u, py + 380 * u, 1.4f * u);
            app.font.setColor(app.theme.text.r, app.theme.text.g, app.theme.text.b, 192 / 255f);
            text("Made by FAL! Android port by Pama1234!", px + 20 * u, py + 420 * u, 1f * u);
            text("Unofficial Remake", px + 20 * u, py + 440 * u, 1f * u);
        }
        app.font.setColor(app.theme.text);
    }

    private void text(String s, float x, float y, float scale) {
        app.font.getData().setScale(scale);
        app.font.draw(app.batch, s, x, y);
    }

    /** 教学模式等附加 HUD（本类为空实现）。 */
    protected void drawExtraHud(float w, float h, float unit) {
    }

    private void drawCircleLabel(String label, float cx, float cy, float r, float scale) {
        app.font.getData().setScale(scale);
        layout.setText(app.font, label);
        app.font.setColor(app.theme.text);
        app.font.draw(app.batch, label, cx - layout.width / 2f, cy + layout.height / 2f);
    }

    private void layoutTouchControls() {
        float w = uiVp.getWorldWidth(), h = uiVp.getWorldHeight();
        float m = Math.min(w, h);
        joyR = m * 0.14f;
        joyBaseX = joyR * 1.5f;
        joyBaseY = joyR * 1.5f;
        if (joyPointer < 0) {
            joyKnobX = joyBaseX;
            joyKnobY = joyBaseY;
        }
        zR = m * 0.085f;
        zX = w - zR * 1.4f;
        zY = zR * 1.6f;
        xR = zR * 0.8f;
        xX = w - zR * 3.6f;
        xY = zR * 1.3f;
        cR = zR * 0.6f;
        cX = w - zR * 5.2f;
        cY = zR * 1.1f;
        float bw = 120f * (h / 640f), bh = 48f * (h / 640f);
        backBtn.w = bw;
        backBtn.h = bh;
        backBtn.setCenter(bw / 2f + 8f, h - bh / 2f - 8f);
        pauseBtn.w = bw;
        pauseBtn.h = bh;
        pauseBtn.setCenter(w - bw / 2f - 8f, h - bh / 2f - 8f);
    }

    private static float dist2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    @Override
    public void resize(int width, int height) {
        worldVp.update(width, height, true);
        uiVp.update(width, height, true);
    }

    @Override
    public void dispose() {
    }
}
