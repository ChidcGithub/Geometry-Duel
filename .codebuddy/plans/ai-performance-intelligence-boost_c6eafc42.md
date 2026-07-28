---
name: ai-performance-intelligence-boost
overview: 全面提升 Geometry Duel NEAT AI 的实战智能度（感知补全、训练课程、进化算法）与双侧性能（训练吞吐 + 实战推理帧率），旧存档维度不兼容时自动从零进化。
todos:
  - id: senses-and-storage
    content: VisionSensor 新增 6 项感知输入，NeatStorage 版本升级与维度校验，适配调用点后编译推送
    status: completed
  - id: curriculum-and-sampling
    content: NeatTrainer 实现胜率驱动自适应课程与逐个随机对手采样，编译推送
    status: completed
    dependencies:
      - senses-and-storage
  - id: genome-network-perf
    content: Genome 距离/交叉降复杂度，NeatNetwork 循环复制优化，NeatEngine 网络缓存，编译推送
    status: completed
    dependencies:
      - senses-and-storage
  - id: sim-throughput
    content: VisionSensor 箭感知提举，无头模拟跳过倒计时与背景，编译推送
    status: completed
    dependencies:
      - genome-network-perf
  - id: dynamic-theme-color
    content: Android 壁纸种子色提取，ThemeData 种子色派生，设置页动态取色开关，编译推送
    status: completed
---

## 用户需求

- 原始请求："计划提升AI性能和智能度"
- 澄清结论 1：感知输入维度变化导致旧训练存档不兼容时，**直接归零重新进化**（实现简单，接受进度清零）
- 澄清结论 2：性能优化**两侧都要**——训练吞吐（单位时间跑更多代/模拟局，进化更快）与实战帧率（降低 AI 推理与感知的 CPU 开销）
- 追加需求："现在主题色提取好像没有，一起加上"——接入 Material You 动态取色（从系统壁纸提取主题种子色，派生全套 M3 配色）

## 产品概述

Geometry Duel 的 AI 基于自研 NEAT 神经进化系统：87 条射线视觉 + 15 项全局输入，循环神经网络输出移动/短弓/长弓/传送 5 路决策，后台多线程无头模拟持续进化。本次升级在不改变玩法的前提下，让 AI "看得更全、学得更快、打得更聪明"。

## 核心功能

1. **感知补全（智能度）**：新增 6 项关键输入——自身传送冷却、传送锚点相对向量、敌人速度向量（vx/vy）、敌人传送标记状态，消除 AI 决策盲区
2. **存档版本升级**：存档记录输入维度并校验，维度不符即自动从零进化；玩家幽灵录像格式未变，独立保留不清空
3. **自适应课程（智能度）**：规则 AI 难度不再按世代固定推进，改为按近期对战胜率动态升降（胜率高加速、胜率低回退）
4. **对手多样化防过拟合（智能度）**：冠军/幽灵/历史冠军对手由"全代统一"改为"每个体随机抽取"，避免全种群过拟合单一对手风格
5. **训练吞吐优化**：基因组兼容距离与交叉由 O(n²) 降为 O(n)；网络构建结果按基因组身份缓存复用；无头模拟跳过 180 帧倒计时与背景对象
6. **推理性能优化**：射线感知中箭矢数据每帧只提取一次（原每射线重复遍历）；循环网络每帧仅复制循环源节点（原全量复制）
7. **动态主题色（Material You）**：Android 端从系统壁纸提取种子色（API 31+ `system_accent1_500`，API 27+ 回退 `WallpaperColors`），core 端按种子色 HSV 派生全套 M3 色调板；设置页新增"动态取色"开关（默认开），桌面端无种子色保持默认紫

## 技术栈

- 沿用现有栈：Java + libGDX 1.13.5，core/android/desktop 三模块 Gradle，无新增依赖
- 自研 NEAT 实现（Genome/NeatNetwork/NeatEvolver/NeatTrainer），全部改动限定在 core 模块

## 实现方案

### 总体策略

分五批独立改动，每批编译验证后立即提交推送（遵循用户既定命令模板）。智能度改动（感知+课程+采样）、性能改动（算法复杂度+模拟吞吐+推理开销）与主题改动（动态取色）解耦，任一批出问题可单独回退。

### 1. 感知补全与存档版本（智能度）

- `VisionSensor.GLOBAL_INPUTS` 15 → 21，在现有 g+0..g+14 之后追加：
- g+15 自身传送冷却 `teleportCooldown/180`（0=可用，解决 AI 不知道传送何时就绪的盲区）
- g+16/g+17 传送锚点相对向量 `(anchor-pos)/320` clamp [-1,1]，未标记为 0（让 AI 学会"回传落点"决策）
- g+18 敌人传送标记 `teleportMarkRemaining/900`，未标记为 0（预判对手回传时机）
- g+19/g+20 敌人速度 `vel.x/10`、`vel.y/7` clamp [-1,1]（预判走位与箭路，当前只有射线危险度分量，缺全局速度）
- `NeatStorage`：VERSION 1→2，`SaveData` 增加 `inputCount` 字段；`load(rayCount, inputCount)` 校验版本+射线数+输入维度，任一不符返回 null（旧 v1 存档自动作废→从零进化，符合用户选择）。幽灵录像用独立 `GHOST_VERSION=1` 校验，**格式未变、保留不清空**。
- `NeatTrainer` 三处调用点（loadOrCreate/saveNow/verifySave）适配新签名，期望值 = `new VisionSensor(rayCount).inputSize()+1`。

### 2. 自适应课程与对手采样（智能度）

- `NeatTrainer` 新增 `curriculumLevel`（初始 0.3，读取存档后按 `progressiveLevel(generation)` 估计起点，`doReset` 归零 0.3）替代固定课程函数。主评估首局（vs 规则 AI）统计每代胜率：>0.65 则 +0.02，>0.5 则 +0.008，<0.3 则 −0.01，clamp [0.3, 1.0]，日志记录变化。
- 对手抽取从"代级统一"移到评估任务内部：`pickChampion/pickGhost/pickHistoricalChampion` 改为接收 `Random`（用 `rngPerThread`，避免共享 rng 争用）。`championPool`/`ghosts` 为 CopyOnWriteArrayList，`historicalChampions` 在 latch.await 后才被训练线程修改，跨线程只读安全。每个体面对不同对手 → 降低全种群相关性过拟合。

### 3. 基因组与网络算法性能（吞吐+推理）

- `Genome.distance()`：`hasInnovation` 线性扫描（被 o.conns 循环调用 → O(n²)）改为预建 `HashSet<Integer>` 查询 O(1)。物种划分每代约 2250 次 distance 调用，连接数数百时收益显著。
- `Genome.crossover()`：`ensureNode → child.node(id)/src.node(id)` 线性扫描改为构建期一次 `HashMap<Integer,NodeGene>`（child 节点 id 集 + b 节点表）。
- `NeatNetwork` 构建：输出节点定位由 O(V²) 双重循环改为预建 id→类型表一次扫描；`eval()` 每帧 `System.arraycopy(values→prevValues, orderLen)` 改为只复制**循环连接源节点**（构建时预计算去重的 `recSrcPositions[]`，`reset()` 同理只清零这些位置——prevValues 仅循环连接读取，语义等价）。循环连接通常极少，每帧复制从 orderLen 个浮点降到个位数。
- `NeatEngine`：新增 4 项 identity 网络缓存（`Genome[]+NeatNetwork[]` 环形置换），`setGenome` 命中即复用。覆盖同代对战 ga/gb 交替与冠军切换导致的重复构建（网络构建含 nodeCount² 可达性矩阵 + V 次 DFS，单次约 10 万级操作）。引擎实例为 ThreadLocal/局内独占，无线程安全问题；rayCount 变化时清空缓存。

### 4. 模拟与感知吞吐（吞吐+推理）

- `VisionSensor.sense()`：当前 87 条射线 × N 支箭重复执行 `rayCircle`（内部每射线每箭重算相对向量）。改为 sense 调用开头一次性将箭提取到实例级可复用数组（relX/relY/rel²/radius²/vx/vy/lethal，按需扩容），射线循环内联投影判定 `proj=relX*dx+relY*dy`，提前跳过 `proj<0`。每感知调用消除约 87×N 次相对向量重算，约省 40% 感知开销。VisionSensor 每引擎独占，无共享状态。
- 无头模拟跳过倒计时：`GameSystem` 构造时 `muted ? new PlayGameState(this) : new StartGameState(this)`（倒计时期间引擎不 act、玩家静止，跳过物理等价）；`simulate()` 中 `m.frames = sys.frameCount + COUNTDOWN_FRAMES` 补偿计数，循环上限同步改为纯对战帧数，fitness 语义不变。每局省 180 帧，约 2.4% 模拟帧。
- `GameSystem.background` muted 时不分配（20 个 Line 对象），display 加 null 保护（实战路径非 muted 不受影响）。

### 5. 动态主题色提取（Material You）

- `AndroidLauncher`：`onCreate` 中提取壁纸种子色——API 31+ 读 `getColor(android.R.color.system_accent1_500)`；API 27~30 回退 `WallpaperManager.getWallpaperColors().getPrimary()`；更低版本或失败则 0（表示无种子色）。以 ARGB int 传入 `new GeometryDuelGame(true, seed)`。
- `GeometryDuelGame`：新增构造重载 `GeometryDuelGame(boolean isAndroid, int themeSeed)`（旧构造委托 seed=0）；新增偏好 `dynamicColor`（默认 true）与开关方法；`applyTheme()` 在 `dynamicColor && themeSeed != 0` 时调用带种子色版本派生主题。
- `ThemeData`：新增 `light(int seed)`/`dark(int seed)` 重载，无参版本委托默认紫 `0x6750A4`；内置 RGB↔HSV 转换工具，按 M3 色调角色近似派生：light 主题 primary=种子色压暗（B≈0.60）、primaryContainer=高明度低饱和（B≈0.95,S×0.35）、onPrimaryContainer=深色调（B≈0.25）、background/surfaceVariant 带轻微色相倾向；dark 主题 primary=提亮（B≈0.92,S×0.55）、primaryContainer=中暗（B≈0.38）等。`ring`/`player_a` 引用 primary 自动跟随；游戏元素其余配色保持现状。
- `SettingsScreen`：新增第 9 行 "Dynamic Color" 开关（等距 64unit 布局顺延），切换后 `applyTheme()` 即时生效并保存偏好。
- 桌面端 seed=0 → 恒走默认紫，行为不变。

### 性能与可靠性要点

- 复杂度：`Genome.distance` O(conns²)→O(conns)；物种划分整代从千万级降到十万级操作；感知 O(rays×arrows) 常数项减半。
- 线程安全：所有缓存（NeatEngine 网络缓存、VisionSensor 箭数组）均为线程/局独占实例；跨线程集合只读遍历；不触碰 evolverLock、requestReset/doReset、paused 防污染机制。
- 爆炸半径：不改 fitness 公式、不改输出维度/迟滞阈值、不改实战渲染路径；旧存档按用户确认自动作废；幽灵录像保留。
- 验证：每批用 `:desktop:compileJava --offline` 编译，通过后 `git add core/src && git commit && git push`（用户固定命令模板）。

## 目录结构

```
core/src/main/java/com/geometryduel/
├── neat/
│   ├── VisionSensor.java    # [MODIFY] GLOBAL_INPUTS 15→21：新增自身传送冷却、锚点向量、敌人传送标记、敌人速度 6 项输入（g+15..g+20）；sense() 箭矢数据提升到实例级可复用数组，射线循环内联投影判定，消除每射线重复计算
│   ├── NeatStorage.java     # [MODIFY] VERSION 1→2；SaveData 增加 inputCount 字段；load() 签名加 inputCount 校验（不符返回 null 触发从零进化）；幽灵存档独立 GHOST_VERSION=1 保留旧录像
│   ├── NeatTrainer.java     # [MODIFY] curriculumLevel 自适应课程（胜率驱动，替代固定 progressiveLevel 调用点）；pickChampion/pickGhost/pickHistoricalChampion 改为任务内逐个抽取（传入 rngPerThread）；loadOrCreate/saveNow/verifySave 适配新存档签名；doReset 重置 curriculumLevel
│   ├── Genome.java          # [MODIFY] distance() 预建 HashSet 消除 O(n²) hasInnovation；crossover() 预建节点 HashMap 消除 ensureNode 线性扫描
│   ├── NeatNetwork.java     # [MODIFY] 构建时预计算循环源节点去重列表 recSrcPositions 与节点类型表；eval()/reset() 仅复制/清零循环源节点；输出定位 O(V²)→O(V)
│   └── NeatEngine.java      # [MODIFY] 新增 4 项 identity 网络缓存（环形置换），setGenome 命中复用，rayCount 变化清空
├── ThemeData.java            # [MODIFY] 新增 light(seed)/dark(seed) 种子色派生重载 + RGB↔HSV 工具，按 M3 色调角色近似生成全套配色
├── GeometryDuelGame.java     # [MODIFY] 构造重载接收 themeSeed；dynamicColor 偏好与开关；applyTheme 按种子色派生
├── screen/
│   └── SettingsScreen.java   # [MODIFY] 新增第 9 行 Dynamic Color 开关，切换即时生效
└── game/
    └── GameSystem.java       # [MODIFY] muted 时直接进入 PlayGameState 跳过 180 帧倒计时、不分配 GameBackground；display 背景 null 保护（仅训练模拟路径，实战不受影响）

android/src/main/java/com/geometryduel/android/
└── AndroidLauncher.java      # [MODIFY] 启动时提取壁纸种子色（API31+ system_accent1_500 / API27+ WallpaperColors），传入 GeometryDuelGame
```

## 关键代码结构

```java
// VisionSensor.java —— 输入布局契约（inputSize = rayCount*2 + 21）
public static final int GLOBAL_INPUTS = 21;
// g+15: self teleportCooldown/180 | g+16,g+17: 锚点 (dx,dy)/320 clamp
// g+18: enemy teleportMarkRemaining/900 | g+19,g+20: enemy vel/MAX_V clamp

// NeatStorage.java —— 存档兼容契约
public static SaveData load(int rayCount, int inputCount);
// 校验: version==2 && rayCount 相等 && inputCount 相等 && population 非空，否则 null（调用方从零进化）
```