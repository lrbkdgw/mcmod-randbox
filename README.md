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

> **真的无法跳过**：`ReelScreen` 吞掉所有按键（Esc / 空格 / 背包键）与鼠标事件，
> `shouldCloseOnEsc()` 为 false；即便有任何途径关掉了界面（包括创造模式下的按键、
> 服务端换屏），`removed()` 与客户端每 tick 的守卫都会立刻把它重新放回最前面。
> 万一客户端断线，服务端在超时后会照常把奖品放进箱子；玩家再次打开同一个箱子时，
> 会**重播同一次抽奖**（奖品早已确定，不会重抽、也不会因此多拿一份）。

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

> **抽取物品数是硬保证的**：`LootRoller#roll` 会一直抽到凑满该稀有度要求的件数，
> 多了截断、少了用已抽到的物品补齐；即使某张表完全解析不出来（走原版兜底 roll），
> 也会重复 roll 并裁剪 / 补齐到同样的件数。
> 另外**大箱子（双联箱）会被当成一个整体**：两半的 `LootTable` 都会被清除、
> 奖品随机分布在两半的空格里，避免另一半在开界面时又生成一整套原版战利品
> 导致箱内物品数量与稀有度不符。

### 保留原有战利品列表

模组不替换任何原版/数据包战利品表，而是**读取**它（`LootExtractor`）：
先用原版的战利品表 Gson（`Deserializers.createLootTableSerializer()`）把 `LootTable`
序列化成 JSON —— 也就是数据包里那份 json —— 再解析这份 JSON。
这条路完全不依赖字段名 / 混淆映射 / AccessTransformer，并且能正确处理
`item`、`tag`（自动展开标签内所有物品）、`alternatives` / `group` / `sequence`（递归展开子条目）、
`loot_table`（递归解析被引用的表）等**全部条目类型**；
`weight`、`quality`、`set_count` 的最小/最大数量都会被读出来，
原版 loot function 也会用 `Deserializers.createFunctionSerializer()` 反序列化回来并合成执行
（附魔书、随机数量、药水、损伤值等都正常）。旧的反射读取作为兜底保留。

### Quality 修复（固定物品品质）

原版战利品表几乎从不填写 `quality` 字段，导致「稀有度 → 品质加权」形同虚设。
本模组因此给**游戏中的物品本身**赋予一个固定 Quality（`ItemQuality`），
（范围 0~4）并在读取**任何**战利品表时把它写进对应条目（`BoxLootTable#applyItemQuality`），
于是 `BaseWeight × (1 + Quality × factor)` 才真正生效：宝箱越高级，好东西出现得越多。

* **绝大多数物品固定为 0**，取值范围 **0 ~ 4**，内置清单共 **124 个**物品：

| Quality | 物品 |
|---|---|
| 4 | 龙蛋、下界之星、附魔金苹果、鞘翅、信标 |
| 3 | 下界合金锭、下界合金块、不死图腾、海洋之心、末影龙首 |
| 2 | 凋灵骷髅头颅、远古残骸、潮涌核心、下界合金碎片 / 升级模板 / 全套下界合金装备、钻石、钻石块、绿宝石、绿宝石块、潜影壳、回响碎片、追溯指针、嗅探兽蛋 |
| 1 | 附魔书、三叉戟、金苹果、全套钻石装备、末影水晶、龙息、鹦鹉螺壳、骷髅 / 僵尸 / 苦力怕头颅、海绵 / 湿海绵、磁石、**金块**、命名牌、鞍、钟、海晶灯、附魔之瓶、末影之眼、恶魂之泪、望远镜、弩、附魔台、锁链装备 ×4、马铠 ×4、唱片 ×15 + 唱片碎片、盔甲纹饰锻造模板 ×16、陶片 ×20 |

  其余物品（铁锭、金锭、黑曜石、TNT、萤石、青金石、紫水晶、末影珍珠、烈焰棒、食物……）全部为 0。
  配置文件带 `__version` 字段，模组更新内置表后会自动重写旧文件。

* 存储在 `config/randombox/item_quality.json`（首次启动自动生成，只写非 0 的项），可任意增删改。
* 也可以在游戏里改：
  * `/RandomBox Quality <物品ID>` 查询；
  * `/RandomBox Quality <物品ID> <0-4>` 设置（写回 json，设 0 表示移除）。
* 编辑器里 Quality 一列会显示这个固定值，并且默认**只读**（由物品决定，避免两套数据打架）。
  想手工逐条设置 Quality，把配置里的 `overrideQuality` 设为 `false` 即可，此列随即变为可编辑。

### 数量与物品池

* 设原表的战利品池总数为 `k`，则所有产出物品的堆叠数量 **× k/4**
  （`LootRoller#scaleCount`，小数部分按概率进位；可用 `scaleStackSizes` 关闭）。
* 决定某个物品属于哪个池时，以**每个池的平均 rolls 次数**为权重抽取
  （`BoxLootTable.Pool#expectedItems`）。例如猪灵堡垒藏宝室的三个池 rolls 为 3 / 5 / 0.5，
  那么它们贡献的物品比例就是 3 : 5 : 0.5（下界合金/钻石池 ≈ 3/8.5）。
  > 早期版本把「平均堆叠数量」也乘进了池权重，导致金锭 / 金块这种大堆叠的廉价池几乎吃掉所有抽取，
  > 现已修正：堆叠数量只参与 `k/4` 的数量缩放，不再影响选池概率。
* 池内再按上面的品质加权抽取条目。

### 转轮内容

* 每一列滚动的物品都是**该表真实可能产出的物品**，按与实际抽奖相同的权重
  （`BaseWeight × (1 + Quality × factor)`）随机生成，所以飞过去的东西的比例≈真实概率。
* 每列共 56 个物品，中奖物品**不在最后一格**：它后面还会再跟 **6~15** 个物品
  （即中奖格是倒数第 7 ~ 16 个），中奖下标由服务端下发（`StartReelPacket#prizeIndices`）。

### 抽奖时长

每个物品平均 **1.2 秒，±50 % 浮动**（即 0.6 ~ 1.8 秒，服务端随机生成后下发）。
可配置：`secondsPerItem`、`timeSpread`。

### 粒子与光柱

* 服务端每秒把玩家周围（默认 32 格）的箱子数据同步给客户端。
* 客户端为每个箱子按稀有度颜色（白/绿/紫/黄/红）在四周环绕 `dust` 粒子。
* **未开启过**的箱子在靠近时（默认 24 格）额外渲染一道信标式光柱（`BeaconRenderer.renderBeaconBeam`）。
* **未开箱就挖掉**：服务端会照常按稀有度抽一次奖，并把奖品直接从被破坏的方块里弹出来
  （创造模式破坏不掉落，与原版一致），不会像原版那样让未开启的战利品表凭空消失。
* 箱子被破坏后效果会立即消失：服务端监听 `BlockEvent.BreakEvent` 并在每次同步前校验方块是否还在
  （爆炸、`/setblock` 等任何方式都能清掉），客户端渲染前也会再确认一次方块仍是容器，
  不会再留下"鬼光柱"。

---

## 2. GUI 战利品表编辑

`LootEditorScreen`（`/RandomBox GUI` 打开，需要 OP 权限 2）：

* **左侧＝全部战利品表列表**：顶部是搜索框（输入任意片段即可过滤），
  下面列出服务器上的**所有**战利品表——已经被本模组保存/载入的表用 `*` + 金色标出，
  其余原版 / 数据包表为灰色。列表支持鼠标滚轮和 ▲▼ 翻页。
  * 点击金色（已载入）的表：直接进入编辑。
  * 点击灰色的表：客户端向服务端请求导入，服务端把原版表解析成可编辑结构后回传，
    界面**自动选中**刚导入的这张表；即使该表读不出内容，也会给出一张空表让你手工填写。
* **在游戏内新建战利品表**：左下角输入框填 ID（不写命名空间时自动补 `randombox:`），
  点击 `新建` 立即得到一张带一个池、一个条目的新表并进入编辑，点 `保存` 写入服务端。
  新表的 ID 会出现在 `/RandomBox SetNewBox <坐标> <品质> <表名>` 的自动补全里，可直接用于宝箱。
* **右侧＝编辑区**：池选择 `#1 #2 … + -`（`+` 加池、`-` 删当前池）、该池的 `rolls`，
  以及条目表格 `物品 ID / 权重 / 品质 / 最小数量 / 最大数量`，每行带物品图标预览，
  可添加、删除、翻页。所有输入框**即时写回数据模型**（输入即生效，翻页/切池不会丢失改动）。
* `保存` 把整张表发回服务端，存为 `config/randombox/loot_tables/<id>.json`；
  之后开箱时**自定义表会覆盖同 id 的原版表**。`删除` 则移除覆盖，恢复原版。
* **搜索不会抢焦点**：过滤时只更新列表按钮的文字与显示状态，不重建界面，
  因此搜索后右侧的输入框照常可以点击和编辑。
* **自适应布局**：左侧列表行数、右侧条目行数、各列宽度、按钮栏位置全部由当前窗口
  `width/height` 实时计算，并对过长的 ID / 文本做像素级裁剪，
  因此任何分辨率和 GUI 缩放下都不会有控件跑到屏幕外。
* 底部状态栏会显示「正在编辑 / 已新建 / 已保存 / 非法 ID / 权限不足」等提示，
  不会再出现「点了没反应」的情况。

---

## 3. 指令

```
/RandomBox SetNewBox <坐标> <品质 0-5> <战利品表名>
/RandomBox GUI
/RandomBox Quality <物品ID> [0-4]
```

* `SetNewBox`：在目标位置放置一个箱子，设置其战利品表并登记为随机宝箱。
  品质 `0` = 按原概率随机，`1..5` = 普通/稀有/史诗/传说/绝世。
* `GUI`：打开战利品表编辑界面。
* `Quality`：查询 / 设置某个物品的固定 Quality（见上文「Quality 修复」），带物品 ID 补全。
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
  "overrideQuality": true,     // 用物品固定 Quality 覆盖战利品表里的 quality
  "effectRadius": 32,
  "beamRadius": 24
}
```

---

## 5. 构建

> **成品已经编译完成**：`dist/randombox-1.0.0.jar`（80 KB，49 个条目）
> 由 GitHub Actions（`.github/workflows/build.yml`）用官方 ModDevGradle + Forge `1.20.1-47.1.3`
> 工具链执行 `gradle build` 产出，已经过 `reobfJar` 重映射，**可以直接放进 `mods/` 进游戏**。
> 每次推送后 CI 会把新的 jar 和完整构建日志（`ci-logs/build.log`）提交回分支。
> 最近一次构建：`BUILD SUCCESSFUL in 3m 36s`，`:compileJava` / `:jar` / `:reobfJar` 全部通过，0 错误。

### 5.1 正常构建（推荐，需要联网到 maven.neoforged.net）

工程使用官方 NeoForge 1.20.1 MDK 的工具链（`net.neoforged.moddev.legacyforge`，
与 `NeoForged/MDK @ 1.20.1-legacy` 一致）：

```bash
./gradlew build      # 或 gradle build
# 产物：build/libs/randombox-1.0.0.jar（已自动 reobf，可直接放进 mods/）
```

> 注意：NeoForge 1.20.1 的包名仍然是 `net.minecraftforge.*`，mods.toml 依赖的 modId 是 `forge`，
> 依赖坐标为 `net.neoforged:forge:1.20.1-47.x`。（`net.neoforged.neoforge.*` 是 1.20.2+ 才改的。）

### 5.2 本仓库中的离线编译（已完成，产物在 `dist/`）

这个沙箱访问不了 Maven（`maven.neoforged.net`、`repo1.maven.org`、`libraries.minecraft.net`
全部被拦截），也没有预装 JDK，所以无法直接跑 ForgeGradle / ModDevGradle。
但**所有相关源码都能从 GitHub 取到**，于是这里用源码重建了一套「编译期 SDK」，
并用它完成了对全部模组源码的真实编译：

```bash
./tools/build-sdk.sh        # 生成编译期 SDK（约 5900 个签名桩）
./tools/offline-compile.sh  # 编译 + 打包（会在需要时自动调用 build-sdk.sh）
```

流水线：

| 步骤 | 说明 |
|---|---|
| JDK | PyPI 的 `jdk4py`（JRE 25） |
| 编译器 | Eclipse 批处理编译器（npm 包 `@ctxo/lang-java-analyzer` 内自带的 JDT jar），`-source/-target 17` |
| Minecraft 1.20.1 API | `Blackjack200/minecraft_client_1_20_1`（Mojang 名的反编译源码，4786 个文件） |
| NeoForge 47 API | `NeoForged/NeoForge @ 1.20.1` |
| FML / EventBus | `NeoForged/FancyModLoader @ 1.20.1`、`MinecraftForge/EventBus @ 6.2.x` |
| Brigadier / Gson / JOML / SLF4J | 各自上游仓库 |
| 其余第三方类型（guava、netty、fastutil…） | 只出现在签名里，由 `tools/mkplaceholders.py` 自动生成占位类型 |

`tools/stubgen.py` 用 tree-sitter 解析上述源码，去掉方法体、字段初始化和注解，
只保留**真实的类层次与方法签名**，因此这是一次针对真实 API 的编译，而不是对着手写桩自说自话。

结果：

```
>> compiling src/main/java against the Minecraft 1.20.1 / NeoForge 47 API
>> errors in mod sources: 0
   mod classes : 31
   jar         : build/libs/randombox-1.0.0-dev.jar  (同时复制到 dist/)
```

这次真实编译顺带抓出了几个只有对着真 API 才能发现的问题，并已修复：

1. **包名错误**：NeoForge 1.20.1 用的是 `net.minecraftforge.*` / `MinecraftForge.EVENT_BUS`，
   而不是 1.20.2+ 的 `net.neoforged.neoforge.*`；mods.toml 的依赖 modId 应为 `forge`。
2. `BeaconRenderer.renderBeaconBeam(PoseStack, MultiBufferSource, float, long, int, int, float[])`
   在 1.20.1 里是 **private**，必须用公开的 11 参数重载（带 `BEAM_LOCATION`、`beamRadius`、`glowRadius`）。
3. `LootPoolSingletonContainer` 的前两个 `int` 字段其实是 `DEFAULT_WEIGHT`/`DEFAULT_QUALITY`
   两个 **static** 常量，按序号反射会读错；已改为跳过静态字段（同时确认 `LootPool.rolls`
   声明在 `bonusRolls` 之前，`LootDataManager.getKeys` 返回 `Collection`）。

### 5.3 离线产物 vs. 正式产物

离线流水线产出的 `build/libs/randombox-1.0.0-dev.jar` 是 **reobf 之前**的产物：
类文件按官方（Mojang）名字引用 Minecraft，而 1.20.1 运行时使用 SRG 成员名，因此它只用于验证编译。

正式可用的产物是 CI 构建的 **`dist/randombox-1.0.0.jar`**，
由 ModDevGradle 完成 Mojang → SRG 重映射（`:reobfJar`），可直接安装。

### 5.4 CI

`.github/workflows/build.yml`：checkout → JDK 17 → Gradle 8.8 → `gradle build`，
成功后上传 `randombox-jar` artifact，并把 jar 复制到 `dist/`、把构建日志写入 `ci-logs/build.log`
后以 `CI: build output [skip ci]` 提交回当前分支。

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
├── net/                    6 个数据包（SimpleChannel，net.minecraftforge.network）
├── command/RandomBoxCommand
└── client/
    ├── ReelScreen          不可跳过的抽奖界面
    ├── LootEditorScreen    战利品表编辑 GUI
    ├── ClientEvents        粒子 + 光柱
    └── ClientBoxCache / ClientPacketHandler / ClientSetup
```

## 7. 工具脚本

| 文件 | 作用 |
|---|---|
| `tools/build-sdk.sh` | 从 GitHub 拉取 MC / NeoForge / FML / EventBus / 库源码，生成编译期 SDK |
| `tools/stubgen.py` | tree-sitter 源码 → 签名桩（保留类层次与签名，去掉方法体） |
| `tools/mkplaceholders.py` | 为仅出现在签名中的第三方类型生成占位类型 |
| `tools/sdkoverrides/` | 少量闭源/生成式库的手写桩（authlib `GameProfile`、distmarker `Dist`、fastutil `ObjectArrayList`…） |
| `tools/offline-compile.sh` | 离线编译 + 打包 `dist/randombox-1.0.0-dev.jar` |
