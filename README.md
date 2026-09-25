# Random Box（随机战利品）

Minecraft **Java 1.20.1 / NeoForge 47.x** 模组。把原版宝箱的「第一次开箱」变成一场不可跳过的抽奖，
并为宝箱引入五档稀有度、品质加权抽取、粒子与光柱效果，以及一个游戏内的战利品表编辑 GUI。

---

## 1. 随机箱子战利品

### 抽奖流程（不可跳过）

* 玩家右键一个**尚未开启过**（仍带有 `LootTable` 的）容器方块时，原版打开容器的行为被取消。
* 服务端立刻算出这次开箱的奖品，并把每一列的「滚动条」（每列 40 个物品）与每列的停止时间发给客户端。
* 客户端打开 `ReelScreen`：每个奖品一列，物品竖向滚动，中间白框为中奖位；列按顺序依次停下
  （参考 user_upload 中的抽奖界面）。
* 该界面 **无法跳过**：`ESC` 无效（`shouldCloseOnEsc() = false`）、所有按键与鼠标点击被吞掉、
  `isPauseScreen() = false`、`onClose()` 在动画结束前被拒绝。
* 动画结束后客户端回包，服务端把奖品放入箱子、清除箱子的战利品表、标记该箱已开启，并打开真正的容器界面。
* 若客户端掉线/崩溃，服务端会在超时后自动发放奖品（`ReelManager.tick`）。

### 稀有度

| 稀有度 | 概率 | 抽取物品数 | 品质加权 | 颜色 |
|---|---|---|---|---|
| 普通 Common | 50 % | 3 | ×(1 + Quality×0) | 白 |
| 稀有 Rare | 30 % | 4 | ×(1 + Quality×0.5) | 绿 |
| 史诗 Epic | 12 % | 5 | ×(1 + Quality×1.0) | 紫 |
| 传说 Legendary | 6.5 % | 6 | ×(1 + Quality×2.5) | 黄 |
| 绝世 Mythic | 1.5 % | 8 | ×(1 + Quality×10.0) | 红 |

> 需求里「提高值 0/50/100/250/500%」与公式里的 `0/50/100/250/1000%` 不一致，
> 代码采用**公式**的数值（最后一档 ×10），并且全部写在配置文件里可改：`config/randombox.json → qualityFactors`。

最终权重：`BaseWeight × (1 + Quality × factor)`（`BoxLootTable.Entry#adjustedWeight`）。

### 保留原有战利品列表

模组不替换任何原版/数据包战利品表，而是**读取**它（`LootExtractor`，按字段*类型*反射，
因此不依赖混淆名、也不需要 AccessTransformer），转换成内部模型：每个 pool 的条目、
权重、quality、以及原版的 loot function（`compositeFunction`）都被保留，
生成物品时仍会执行原版函数（附魔书、随机数量、药水等都正常）。

### 数量与物品池

* 设原表的战利品池总数为 `k`，则所有产出物品的堆叠数量 **× k/4**
  （`LootRoller#scaleCount`，小数部分按概率进位；可用 `scaleStackSizes` 关闭）。
* 决定某个物品属于哪个池时，以**每个池的平均物品数量**（平均 rolls × 平均堆叠数）为权重抽取
  （`BoxLootTable.Pool#expectedItems`）。
* 池内再按上面的品质加权抽取条目。

### 抽奖时长

每个物品平均 **1.2 秒，±50 % 浮动**（即 0.6 ~ 1.8 秒，服务端随机生成后下发）。
可配置：`secondsPerItem`、`timeSpread`。

### 粒子与光柱

* 服务端每秒把玩家周围（默认 32 格）的箱子数据同步给客户端。
* 客户端为每个箱子按稀有度颜色（白/绿/紫/黄/红）在四周环绕 `dust` 粒子。
* **未开启过**的箱子在靠近时（默认 24 格）额外渲染一道信标式光柱（`BeaconRenderer.renderBeaconBeam`）。

---

## 2. GUI 战利品表编辑

`LootEditorScreen`（`/RandomBox GUI` 打开，需要 OP 权限 2）：

* 左侧：已有自定义战利品表列表（翻页）、新建（输入 `namespace:path`）、导入
  （把服务器上的原版/数据包表读进编辑器）。
* 右侧：池选择 `#1 #2 … +`、该池的 `rolls`，以及条目表格
  `物品 ID / 权重 / 品质 / 最小数量 / 最大数量`，可添加、删除、翻页。
* `保存` 把整张表发回服务端，存为 `config/randombox/loot_tables/<id>.json`；
  之后开箱时**自定义表会覆盖同 id 的原版表**。`删除` 则移除覆盖，恢复原版。

---

## 3. 指令

```
/RandomBox SetNewBox <坐标> <品质 0-5> <战利品表名>
/RandomBox GUI
```

* `SetNewBox`：在目标位置放置一个箱子，设置其战利品表并登记为随机宝箱。
  品质 `0` = 按原概率随机，`1..5` = 普通/稀有/史诗/传说/绝世。
* `GUI`：打开战利品表编辑界面。
* 两条指令都需要权限等级 2；同时注册了小写别名 `/randombox setnewbox|gui`，
  参数 `<战利品表名>` 有自动补全。

---

## 4. 配置 `config/randombox.json`

```jsonc
{
  "rarityChances": [0.5, 0.3, 0.12, 0.065, 0.015],
  "itemCounts":    [3, 4, 5, 6, 8],
  "qualityFactors":[0.0, 0.5, 1.0, 2.5, 10.0],
  "secondsPerItem": 1.2,
  "timeSpread": 0.5,
  "scaleStackSizes": true,
  "effectRadius": 32,
  "beamRadius": 24
}
```

---

## 5. 构建

正常构建（需要能访问 `maven.neoforged.net` 与 Mojang 的库）：

```bash
./gradlew build      # 或 gradle build
# 产物：build/libs/randombox-1.0.0.jar
```

> **本仓库的沙箱环境没有 JDK，且 Maven / Gradle 分发站点均被网络策略拦截**
> （`maven.neoforged.net`、`repo1.maven.org`、`services.gradle.org`、`libraries.minecraft.net` 全部不可达），
> 因此无法在这里跑真正的 ForgeGradle 构建（它必须下载 NeoForge 与反混淆后的 Minecraft 依赖）。
>
> 为了仍然能「编译出结果」，仓库内提供了离线类型检查：
>
> ```bash
> ./tools/offline-compile.sh
> ```
>
> 它会自动取一个 JRE（PyPI 的 `jdk4py`）和 Eclipse 批处理编译器（npm 包内自带的 jar），
> 用 `tools/apistubs/` 下手写的 Minecraft / NeoForge API 桩把 `src/main/java` 整体编译一遍，
> 输出 `build/offline-classes/`（167 个 class，0 error）。
> 这验证了整套源码的语法与内部一致性，但**不是可运行的模组 jar**：真正的 jar 必须由
> `./gradlew build` 在能联网的环境里生成（它还会做 reobf 重映射）。
> `tools/apistubs/` 只用于这项检查，不会打进 jar。

## 6. 代码结构

```
com.randombox
├── RandomBoxMod            入口
├── Rarity                  五档稀有度
├── config/RBConfig         JSON 配置
├── data/BoxData,BoxSavedData   每个箱子的稀有度/开启状态（按维度持久化）
├── loot/
│   ├── BoxLootTable        内部战利品表模型（pool / entry / weight / quality）
│   ├── LootExtractor       读取原版 LootTable（按字段类型反射）
│   ├── LootRoller          抽奖核心：池权重、品质加权、k/4 数量缩放、滚动条生成
│   └── CustomLootStore     GUI 编辑的表的持久化与覆盖
├── event/
│   ├── BoxEvents           右键拦截、区块加载登记、每秒同步、指令注册
│   └── ReelManager         进行中的抽奖、超时兜底、发奖与开容器
├── net/                    6 个数据包（SimpleChannel）
├── command/RandomBoxCommand
└── client/
    ├── ReelScreen          不可跳过的抽奖界面
    ├── LootEditorScreen    战利品表编辑 GUI
    ├── ClientEvents        粒子 + 光柱
    └── ClientBoxCache / ClientPacketHandler / ClientSetup
```
