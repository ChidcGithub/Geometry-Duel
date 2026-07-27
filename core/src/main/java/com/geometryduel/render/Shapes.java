package com.geometryduel.render;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Processing 风格的 2D 绘制封装（对应原作 pama1234 框架中的 line/rect/arc/circle/quad 与矩阵栈）。
 * 所有图元统一以 Filled 模式绘制，粗线通过 rectLine 实现，保证线宽一致。
 */
public class Shapes {
    private final ShapeRenderer sr = new ShapeRenderer();
    private final Matrix4[] stack = new Matrix4[32];
    private int top = 0;
    private final Matrix4 curr = new Matrix4();
    private final Matrix4 tmp = new Matrix4();

    public final Color strokeColor = new Color(0, 0, 0, 1);
    public final Color fillColor = new Color(0, 0, 0, 1);
    public boolean doStroke = true;
    public boolean doFill = false;
    public float weight = 1f;

    public Shapes() {
        for (int i = 0; i < stack.length; i++) stack[i] = new Matrix4();
    }

    public void begin(Camera camera) {
        sr.setProjectionMatrix(camera.combined);
        curr.idt();
        top = 0;
        sr.begin(ShapeRenderer.ShapeType.Filled);
    }

    public void end() {
        sr.end();
    }

    private void applyTransform() {
        sr.flush();
        sr.setTransformMatrix(curr);
    }

    public void push() {
        if (top < stack.length) stack[top++].set(curr);
    }

    public void pop() {
        if (top > 0) {
            curr.set(stack[--top]);
            applyTransform();
        }
    }

    public void translate(float x, float y) {
        curr.translate(x, y, 0);
        applyTransform();
    }

    /** 旋转（弧度）。在 y 向下的投影中视觉方向与原作一致。 */
    public void rotate(float radians) {
        curr.rotate(Vector3.Z, (float) Math.toDegrees(radians));
        applyTransform();
    }

    public void stroke(float r, float g, float b, float a) {
        strokeColor.set(r, g, b, a);
        doStroke = true;
    }

    public void stroke(Color c) {
        stroke(c, 255);
    }

    public void stroke(Color c, int alpha) {
        strokeColor.set(c.r, c.g, c.b, clampAlpha(alpha));
        doStroke = true;
    }

    public void noStroke() {
        doStroke = false;
    }

    public void doStroke() {
        doStroke = true;
    }

    public void doFill() {
        doFill = true;
    }

    public void fill(float r, float g, float b, float a) {
        fillColor.set(r, g, b, a);
        doFill = true;
    }

    public void fill(Color c) {
        fill(c, 255);
    }

    public void fill(Color c, int alpha) {
        fillColor.set(c.r, c.g, c.b, clampAlpha(alpha));
        doFill = true;
    }

    public void noFill() {
        doFill = false;
    }

    public void strokeWeight(float w) {
        this.weight = Math.max(0.01f, w);
    }

    private static float clampAlpha(int a) {
        return Math.max(0, Math.min(255, a)) / 255f;
    }

    /** 直线（按 strokeWeight 加粗）。 */
    public void line(float x1, float y1, float x2, float y2) {
        if (!doStroke) return;
        sr.setColor(strokeColor);
        sr.rectLine(x1, y1, x2, y2, weight);
    }

    /** 矩形（左上角 + 宽高，y 向下）。 */
    public void rect(float x, float y, float w, float h) {
        if (doFill) {
            sr.setColor(fillColor);
            sr.rect(x, y, w, h);
        }
        if (doStroke) {
            sr.setColor(strokeColor);
            float t = weight;
            sr.rect(x, y, w, t);
            sr.rect(x, y + h - t, w, t);
            sr.rect(x, y + t, t, h - 2 * t);
            sr.rect(x + w - t, y + t, t, h - 2 * t);
        }
    }

    /** 四边形（两个三角形填充 + 四条边线）。 */
    public void quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4) {
        if (doFill) {
            sr.setColor(fillColor);
            sr.triangle(x1, y1, x2, y2, x3, y3);
            sr.triangle(x1, y1, x3, y3, x4, y4);
        }
        if (doStroke) {
            sr.setColor(strokeColor);
            sr.rectLine(x1, y1, x2, y2, weight);
            sr.rectLine(x2, y2, x3, y3, weight);
            sr.rectLine(x3, y3, x4, y4, weight);
            sr.rectLine(x4, y4, x1, y1, weight);
        }
    }

    /**
     * 圆弧（对应原作 arc(x,y,radius,startDeg,extentDeg)）。
     * 角度制与 y 向下坐标一致，extent 正值沿顺时针（视觉）。
     */
    public void arc(float cx, float cy, float radius, float startDeg, float extentDeg) {
        if (!doStroke || radius <= 0 || extentDeg == 0) return;
        int segments = Math.max(4, (int) (Math.abs(extentDeg) / 6f));
        sr.setColor(strokeColor);
        float prevX = cx + radius * cosDeg(startDeg);
        float prevY = cy + radius * sinDeg(startDeg);
        for (int i = 1; i <= segments; i++) {
            float a = startDeg + extentDeg * i / segments;
            float x = cx + radius * cosDeg(a);
            float y = cy + radius * sinDeg(a);
            sr.rectLine(prevX, prevY, x, y, weight);
            prevX = x;
            prevY = y;
        }
    }

    /** 圆（stroke 轮廓；d 为直径，对应原作 circle(x,y,d)）。 */
    public void circle(float cx, float cy, float d) {
        arc(cx, cy, d / 2f, 0, 360);
    }

    public void filledCircle(float cx, float cy, float d) {
        if (doFill) {
            sr.setColor(fillColor);
            sr.circle(cx, cy, d / 2f);
        }
        if (doStroke) circle(cx, cy, d);
    }

    /** 单像素点（对应原作 dot）。 */
    public void dot(float x, float y, Color c) {
        sr.setColor(c);
        sr.rect(x - 1f, y - 1f, 2f, 2f);
    }

    private static float cosDeg(float deg) {
        return (float) Math.cos(Math.toRadians(deg));
    }

    private static float sinDeg(float deg) {
        return (float) Math.sin(Math.toRadians(deg));
    }

    public void dispose() {
        sr.dispose();
    }
}
