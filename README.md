# Geometry Duel 几何决斗

对 `pama1234.gdx.game.app.duel.pft01`（几何决斗安卓原型版，原作 FAL / 移植 Pama1234）的完整复刻。
~~基于 libGDX~~ 3.0 起全面重写为 **原生 Android + Jetpack Compose（Kotlin）**，Material 3 Expressive 设计、Material You 动态取色，targetSdk/compileSdk 37（AGP 9.2）。游戏逻辑与 NEAT AI 训练系统逐项原样迁移。

## 攻击模式

| 攻击 | 触发 | 机制 | 判定 |
|------|------|------|------|
| 短弓（普通攻击） | 按住 Z | 自动瞄准敌人，每 12 帧一箭，箭速 24→8 衰减 | 半径 8 圆形判定，命中击退（冲量 20，±45° 随机）并进入 45 帧受击硬直 |
| 长弓（致命大招） | 按住 X 蓄力 30 帧后松开 | 手动转瞄（0.6°/帧），射出 5 节箭杆 + 1 箭头（间距 24，速度 64） | 半径 16 圆形判定，命中即击杀 |
| 传送 | 按住 C 蓄力 30 帧后松开 | 记录进入时位置为锚点，期间全速移动，松开后瞬移回锚点 | 不造成伤害（原作为调试功能，本复刻默认开放） |

- 箭-箭相撞：双方碎裂（10 个方块粒子）
- 击杀：50 个方块粒子 + 屏幕震动 50；击退：震动 +10；长弓放箭/传送：震动 +10
- 蓄力特效：半径 40 充能环（线宽 8）；蓄满：环状粒子（0.5s）+ 音效；长弓放箭：800 长激光线粒子（2s）
- 玩家：32×32 旋转方块，速度上限 vx±10/vy±7，摩擦 0.92，边界反弹 -0.5
- AI：Move/Jab/Kill 三计划状态机，每 10 帧重选，支持闪避与狙杀（教学模式难度 0.02→1.0 递增）
- NEAT AI：后台多线程进化训练（物种划分/新颖性搜索/幽灵回放/自适应课程），支持经典规则 AI、总冠军与各物种风格对手

## 操作

- **触屏**：左下虚拟摇杆移动/瞄准，右下 Z/X/C 触控按钮
- **键盘**：方向键/WASD 移动，Z 短弓，X 长弓，C 传送，P 暂停，Esc 返回

## 技术栈

- **UI**：Jetpack Compose + Material 3 Expressive（`material3:1.5.0-alpha`），动态取色（API 31+ 壁纸色）
- **渲染**：Compose Canvas 自绘游戏世界（60fps 固定步长逻辑，vsync 驱动）
- **音频**：SoundPool（4 个 ogg 音效）
- **持久化**：SharedPreferences（沿用旧版键名，无缝升级）+ JSON（NEAT 种群/幽灵录像，原子写入）
- **工具链**：AGP 9.2（内置 Kotlin）/ Gradle 9.4 / Kotlin 2.3 / JDK 17，compileSdk 37，minSdk 26

## 模块结构

```
app/        全部源码（游戏逻辑、NEAT AI、Compose UI、资源）
legacy/     旧 libGDX 实现归档（core/android/desktop，不参与构建）
```

## 构建

```bash
./gradlew :app:assembleDebug     # 安卓 APK (debug)
./gradlew :app:assembleRelease   # 安卓 APK (release, 已签名)
```

推送 main 后由 GitHub Actions 自动构建 APK 与桌面发行包（见 Actions 产物）。
