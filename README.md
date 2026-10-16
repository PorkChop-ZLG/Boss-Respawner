# Universal Boss Respawner（通用Boss重生笼）

将 灾变 模组的 Boss重生笼 机制做成一个独立模组，并且改造为数据驱动系统。

生物/Boss 死亡后，可按照 JSON 配置在指定锚点生成“重生笼”；玩家使用指定物品右键激活后，重生笼会重新召唤该生物。

本模组的目的，是将大部分模组的一次性Boss，改造成可再生的Boss，并且搜集特定的复活物品，增加一些冷门道具的用途。

同时优化多人游戏下的Boss战体验，使得每个玩家在找到Boss结构后，都有Boss可以打，不用担心Boss被挑战完了后，需要重新寻找。

- **Mod名：** `通用Boss重生笼`
- **ModID：** `boss_respawner`
- **环境：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21
- **定位：** 独立模组，不强依赖 Cataclysm 或任何具体 Boss 模组。

---

## 功能

- 生物死亡后按 JSON 规则放置未激活重生笼。
- **两种放置模式**：`death`（锚定死亡位置）与 `home`（锚定本模组登记的出生点）。
- 本模组自行观察 Boss 首次加入世界的时机并登记出生点，**不读取、不修改第三方实体的任何数据**。
- 方块实体保存实体类型、钥匙物品、生成参数快照，以及（home 模式下的）出生点锚点。
- 手持正确物品右键：消耗物品、点亮方块、播放动画。
- 到达延迟后尝试生成目标实体；失败后按间隔重试。
- 生成成功后自动销毁重生笼。
- 重生出来的 Boss 继承原始出生点，因此连续多轮重生不会发生位置漂移。
- 目标区块未加载时不会强加载区块，而是留下**持久化待放置任务**，等区块自然加载后再完成放置。
- 同一 Boss 可同时存在多套重生笼，也可以改为范围内只存在一个。
- 有一份**搜索避让列表**（`avoid_blocks`）：搜索的第一趟会避开这些方块，但避让**永远不会导致放不出笼子**——第一趟一无所获时会忽略避让再搜一遍，最坏的结果只是笼子坐在被避让的方块上。
- 重生笼可被原版发光描边高亮显示，玩家可透过方块看到；开关与距离在配置中调整。

## 获取方块

本模组将Boss重生笼物品放入原版“刷怪蛋”创造模式标签页。

生存模式下无法合成、破坏。**注意：重生笼方块在生存模式下不可破坏**，因此若因配置不当被放到不合适的位置，玩家无法自行清除（详见下方“强制放置”）。

也可通过命令获取：

```mcfunction
/give @s boss_respawner:boss_respawner
```

## 已完成的模组联动

以下内置规则全部使用 `home` 模式。详见 `src/main/resources/data/**/boss_respawner/entries/*.json`，每个文件都显式写出了全部可选字段，可直接作为模板抄改。

### 传奇怪物（Legendary Monsters）

| Boss | 实体 ID | 复活物品 | 物品 ID |
|---|---|---|---|
| 云筑魔像 | `legendary_monsters:cloud_golem` | 大气之眼 | `legendary_monsters:eye_of_air` |
| 堕落圣骑 | `legendary_monsters:posessed_paladin` | 鬼魂之眼 | `legendary_monsters:eye_of_ghost` |
| 湮灭猎影 | `legendary_monsters:annihilation_pursuer` | 湮灭之眼 | `legendary_monsters:eye_of_annihilation` |
| 无头骑士 | `legendary_monsters:beheaded_knight` | 鬼魂之眼 | `legendary_monsters:eye_of_ghost` |
| 复生骑士 | `legendary_monsters:resurrected_knight` | 鬼魂之眼 | `legendary_monsters:eye_of_ghost` |
| 沙丘哨兵 | `legendary_monsters:dune_sentinel` | 沙暴之眼 | `legendary_monsters:eye_of_sandstorm` |
| 霜冻傀儡 | `legendary_monsters:frostbitten_golem` | 冰冻之眼 | `legendary_monsters:eye_of_frost` |
| 蔓生巨像 | `legendary_monsters:overgrown_colossus` | 苔藓之眼 | `legendary_monsters:eye_of_moss` |
| 荒古守卫者 | `legendary_monsters:ancient_guardian` | 阜骨之眼 | `legendary_monsters:eye_of_many_ribs` |
| 噬焰蜥 | `legendary_monsters:lava_eater` | 岩浆之眼 | `legendary_monsters:eye_of_magma` |
| 骸骨巨龙 | `legendary_monsters:skeletosaurus` | 尸骨之眼 | `legendary_monsters:eye_of_bones` |
| 凋零恶煞 | `legendary_monsters:withered_abomination` | 灵魂之眼 | `legendary_monsters:eye_of_soul` |
| 紫颂遣使 | `legendary_monsters:endersent` | 紫颂之眼 | `legendary_monsters:eye_of_chorus` |
| 潜影拟态者 | `legendary_monsters:shulker_mimic` | 潜影之眼 | `legendary_monsters:eye_of_shulker` |
> 注：湮灭构造体 为召唤Boss，所以无需Boss重生笼。

### Mowzie's Mobs（Mowzie的生物）

| Boss | 实体 ID | 复活物品 | 物品 ID |
|---|---|---|---|
| 钢铁守护者 | `mowziesmobs:ferrous_wroughtnaut` | 捕获的岩壳居蟹 | `mowziesmobs:captured_grottol` |
| 霜冻巨兽 | `mowziesmobs:frostmaw` | 荧光胶冻 | `mowziesmobs:glowing_jelly` |
| 太阳鸟-乌姆武提 | `mowziesmobs:umvuthi` | 飞蛇毒牙 | `mowziesmobs:naga_fang` |
| 雕刻家-通臂大师 | `mowziesmobs:sculptor` | 泥峭棒 | `mowziesmobs:bluff_rod` |
> 注：Mowzie的生物 没有对应的眼球物品，暂时使用其他生物的掉落物。

### 首领崛起（Bosses Rise）

| Boss | 实体 ID | 复活物品 | 物品 ID |
|---|---|---|---|
| 阿什洛德，炼狱飞龙 | `block_factorys_bosses:infernal_dragon` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| 斯洛克，巨型沙虫 | `block_factorys_bosses:sandworm` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| 赫尔瓦，冥界骑士 | `block_factorys_bosses:underworld_knight` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| 斯科尔，霜冻雪怪 | `block_factorys_bosses:yeti` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| Nerakyss，海怪 | `block_factorys_bosses:kraken` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
> 注：CurseForge 项目名叫 `bossesrise`，但**模组 ID 是 `block_factorys_bosses`**，数据包目录必须用它。
> 注：海怪在官方 `zh_cn` 中**完全没有翻译**（实体、物品、字幕都只有英文），游戏内会显示 `Nerakyss, The Kraken`。
> 注：本模组 5 个 Boss 的掉落物全部是"打完才有"，不满足复活钥匙要求，因此统一使用**无配方的远古试炼钥匙**（来自冥界竞技场的试炼刷怪笼宝箱）。

### 暮色森林（Twilight Forest）

| Boss | 实体 ID | 复活物品 | 物品 ID |
|---|---|---|---|
| 娜迦 | `twilightforest:naga` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 巫妖 | `twilightforest:lich` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 米诺菇 | `twilightforest:minoshroom` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 九头蛇 | `twilightforest:hydra` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 幻影骑士 | `twilightforest:knight_phantom` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 暮初恶魂 | `twilightforest:ur_ghast` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 雪怪首领 | `twilightforest:alpha_yeti` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 冰雪女王 | `twilightforest:snow_queen` | 魔法地图核心 | `twilightforest:magic_map_focus` |
| 谜题羊 | `twilightforest:quest_ram` | 魔法地图核心 | `twilightforest:magic_map_focus` |
> 注：暮色森林为 8 个主线 Boss 各做了一个**专属奖杯**，但它们全是"打完才有"的掉落物，不满足复活钥匙要求，因此统一使用可合成的**魔法地图核心**。
> 注：该模组的 Boss 战利品可能通过**箱子**发放（`darktower_boss.json` 就是一个装着暮初恶魂整套战利品的箱子表），且 Boss 场地遍布箱子，因此这 9 个条目把该模组全部 **17 种箱子**写进了 `avoid_blocks`（每条共 18 项，含 `cataclysm:boss_respawner`）。
> 注：`avoid_blocks` 的语义是**搜索第一趟强制避开**：第一趟会绕开箱子去找别的位置；万一第一趟一无所获，第二趟会忽略避让再搜一遍，因此**避让不会导致笼子放不出来**，最坏的结果只是笼子坐在箱子上面（候选位必须是空气或可替换方块，所以箱子本身永远不会被搜索选中）。详见下方 [`avoid_blocks` 的完整语义](#avoid_blocks搜索第一趟强制避开)。
> 注：谜题羊是一次性任务 NPC，被击杀后任务将永久无法完成（它死亡不掉落任何东西），因此同样纳入。其自带的 `EnforcedHomePoint` 本模组**不会读取**，只在首次观察时登记自己的 home。

### 钥匙选取原则

本模组的用途是「某个 Boss 已被打掉，但其他玩家还没打」。因此复活钥匙必须**在打该 Boss 之前就能获得**——Boss 的掉落物天然不满足这个条件。上面两个模组因为无法为每个 Boss 都找到战前专属物品，采用了「多个 Boss 共用一件稀缺物品」的方案。详见 [`docs/bosses-rise-mapping.md`](docs/bosses-rise-mapping.md) 与 [`docs/twilight-forest-mapping.md`](docs/twilight-forest-mapping.md)。

## Jade 联动

装了 [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) 时，鼠标指向**通用Boss重生笼**会额外显示两行（追加在方块名那行之后）：

```text
通用Boss重生笼
复活：米诺菇
[小图标] 魔法地图核心 ×100
```

- 第一行 `复活：<Boss名>`，Boss 名是**翻译后的实体名**并**硬编码为 `§e` 金色**（不跟随主题，故意与其余文字区分）；**不渲染任何生物模型**（没有覆盖图标，也没有幽灵实体）
- 第二行**没有前缀标签**，与 Jade 的箱子列表一致：左侧**半尺寸小图标**，右侧 `物品名 ×数量`。数量为 1 时**不显示数量**；数量写在**物品名之后**
- 图标走 `IElementHelper.smallItem`（0.5 缩放、10×9 方框），并依靠它内部传入的空字符串来**抑制原版数量角标** —— 角标无法表达超过一组的数量，与文字数量并存会出现"角标 64 + 文字 ×100"这种同一需求两个数字的错误。数量**绝不钳制**
- 物品名与数量合并在**同一个文本元素**里，避免两者脱节；物品名用 `IDisplayHelper.stripColor` 剥掉稀有度色
- **除 Boss 名外的文字都不设颜色**：Jade 的 `TextElement` 以主题的 `normal` 色（默认灰）作为默认色绘制，因此自动跟随玩家主题
- 无障碍：图标朗读堆叠（`clearCachedMessage`）、文本静音（`message(null)`），避免同一内容被读两遍
- 未配置的笼子不显示第一行；规则指向的模组被移除时显示原始 ID（便于排查）
- Jade 是**可选依赖**，没装时本模组行为完全不变；玩家可以用 Jade 自带的开关单独关掉它

> **两条已经踩过的坑（现已由测试守护）**
> 1. Jade 的主题助手（`IThemeHelper.title/info/...`）把**非 `MutableComponent` 参数当作字面文本**，所以把翻译键当 `String` 传进去会把键名直接印在屏幕上。所有标签必须用 `Component.translatable(...)`。
> 2. Jade 在配置界面按 `config.jade.plugin_<命名空间>.<路径>` 查每个 provider 的名称，而且**开发环境下缺键会直接抛 `AssertionError`**（见 `JadeClient#onGui`）。因此本模组提供 `config.jade.plugin_boss_respawner.cage_contents`。

实现位于 `src/main/java/com/zonlong/bossrespawner/compat/jade/`，由两个类组成：

| 类 | 作用 |
|---|---|
| `BossRespawnerJadePlugin` | `@WailaPlugin` 入口，注册方块组件 |
| `BossRespawnerJadeProvider` | 构造那两行 |

> **重要约束**：这两个类**不得**被任何「总会加载」的代码（`@Mod` 主类、事件处理器、配置、注册表）引用。
> Jade 靠注解扫描自行加载它们；没有 Jade 时它们永远不会被类加载，因此不会有 `NoClassDefFoundError`。
> 该约束由 `JadeOptionalityTest` 在源码层面强制守护。

构建配置为 `compileOnly` + `runtimeOnly`：编译需要 Jade 的 API，但不声明为必装依赖，也不打包 Jade 自身。

## JSON 配置

数据包目录：

```
data/<namespace>/boss_respawner/entries/<任意文件名>.json
```

- 监听目录会递归加载所有 `.json`。
- 执行 `/reload` 后生效；已放置的重生笼使用放置当时的规则快照，不会被 `/reload` 改变。
- **当前只支持 `schema_version: 4`**，`schema_version` 必须显式写出。
- 非法或未注册的条目会被跳过，不影响其他条目。

### 两种放置模式

| 模式 | 锚点 | 说明 |
|---|---|---|
| `home` | 本模组登记的出生点 | 内置规则全部使用此模式。重生笼会回到 Boss 原本所在的竞技场/结构，而不是它倒下的地方。 |
| `death` | 生物死亡位置 | 传统行为。适合没有固定竞技场的 Boss。 |

`placement.mode` **是必填字段，解析器不会隐式补默认值**。内置规则之所以表现为“默认 home”，是因为它们全部显式写了 `"mode": "home"`。

Home 模式的出生点来自本模组自己的数据库（见下方“Home 来源”），**不是**第三方实体的 `HomePos`、`getHomePos()` 或 `getRestrictCenter()`，也没有任何反射调用。

### Home 来源与登记时机

- 服务端监听实体加入世界的事件（含从存档加载的实体），当实体命中一条 `home` 规则且其 UUID 尚未登记时，把**当时的维度与方块坐标**记为出生点。
- 已登记的实体会**保留最初的出生点**，之后无论移动到哪里、区块如何反复加载，都不会被覆盖。
- 模组安装前就已存在的 Boss，会在其区块第一次被加载时按“首次观察位置”登记。这个位置未必是历史真实出生点，但它是稳定、可解释且不需要改动第三方实体的策略。
- `/reload` 之后会自动补登记一次：扫描各维度**当前已加载**的实体，为“命中 home 规则但尚无记录”的实体补上出生点。这就是为什么在运行中新增 home 规则也能生效。
- 重生笼召唤出新实体后，新 UUID 会在**加入世界之前**绑定到原始出生点，因此重生体不会把生成位置当成新的出生点。
- 数据库文件位于 `saves/<存档名>/data/boss_respawner_homes.dat`，随存档保存和迁移。

> **无记录的后果：** 若某实体在任何时刻都未被观察到（例如规则在其实体已加载之后才通过 `/reload` 添加，且补登记未覆盖），它死亡时**不会放置重生笼**，也不会回退到死亡位置。此时服务端日志会输出一条包含实体 ID、UUID、死亡维度与死亡坐标的警告，并提示检查规则加载顺序或执行 `/reload`。

### 未加载区块：持久化 defer

放置锚点所在的区块可能未加载——这正是 home 模式要处理的常见情况（Boss 被引到远处才被击杀）。此时：

- **不会**为了放置而同步加载/生成区块（那会卡住服务端主线程）；
- 改为写入一条**持久化待放置任务**，服务器重启后依然存在，且**不设置过期时间**；
- 目标区块之后自然加载时（区块加载事件只做标记，实际放置在之后的服务器 tick 执行），用当时保存的规则快照**重跑完整的放置流程**——也就是说 `search_failure_fallback: false` 的规则在延迟之后依然不会强制放置；
- 任务按 **放置目标 `(维度, 坐标)`** 去重，因此任务总量与“世界上出现过的放置目标数”成正比，而不是与死亡次数成正比；
- 若目标**维度本身不可用**（例如对应模组被移除），则**立即失败并不创建任务**（否则任务永远无法完成、又永不过期），同时清理该实体的出生点记录。

这套行为由 Java 固定，**没有对应的 JSON 开关**。

### 必填/选填总览

| 分类 | 字段 | 必填/选填 | 默认值 |
|---|---|---|---|
| 顶层 | `schema_version` | **必填** | 无（必须为 `4`） |
| 顶层 | `entity` | **必填** | 无 |
| 顶层 | `activation.item` | **必填** | 无 |
| 顶层 | `placement.mode` | **必填** | 无（`death` 或 `home`） |
| 顶层 | `placement.<mode>` 对应的配置块 | **必填** | 无 |
| 顶层 | `spawn.mode` | **必填** | 无（`summon` 或 `transform`） |
| 顶层 | `spawn.<mode>` 对应的配置块 | **必填** | 无 |
| 顶层 | `enabled` | 选填 | `true` |
| 顶层 | `priority` | 选填 | `0` |
| 激活 | `activation.amount` | 选填 | `1` |
| 激活 | `activation.consume` | 选填 | `true` |
| 死亡条件 | `death` 整个配置块 | 选填 | 使用默认值 |
| 生成 | `spawn.<mode>` 内的各字段 | 选填 | 见字段表 |
| 重复策略 | `duplicate` 整个配置块 | 选填 | 使用默认值 |
| 放置 | `placement.<mode>` 内的各字段 | 选填 | 见字段表 |

> 除上述必填项外，其他字段都可以省略并使用默认值。内置规则为了充当完整模板，把所有可选字段都显式写了出来。

---

### 最小 JSON 模板

只填写必须字段即可正常工作，其余全部使用默认值：

```json
{
  "schema_version": 4,
  "entity": "minecraft:warden",
  "activation": {
    "item": "minecraft:echo_shard"
  },
  "placement": {
    "mode": "home",
    "home": {}
  },
  "spawn": {
    "mode": "summon",
    "summon": {}
  }
}
```

该模板等效于：

- 监守者首次被观察到时登记其当前位置为出生点；
- 死亡后在其出生点附近放置重生笼；
- 手持 1 个回响碎片右键激活并消耗；
- 点亮后延迟 20 tick 尝试生成 1 只监守者；
- 生成时需要附近有玩家；
- 搜索失败时在该坐标强制放置（**会覆盖该处的任何方块，旧方块及其内容直接消失、不掉落**，详见下方警示）；
- 同一 Boss 允许同时存在多个重生笼。

---

### 完整示例：监守者

```json
{
  "schema_version": 4,
  "enabled": true,
  "priority": 0,
  "entity": "minecraft:warden",
  "activation": {
    "item": "minecraft:echo_shard",
    "amount": 1,
    "consume": true
  },
  "placement": {
    "mode": "home",
    "home": {
      "offset": [0, 0, 0],
      "place_delay_ticks": 0,
      "force_place": false,
      "search_failure_fallback": true,
      "search_down": 32,
      "search_up": 16,
      "horizontal_radius": 4,
      "require_ground": true,
      "avoid_fluids": false,
      "avoid_blocks": ["cataclysm:boss_respawner"]
    }
  },
  "death": {
    "player_kill_only": false,
    "dimensions": [],
    "biomes": []
  },
  "spawn": {
    "mode": "summon",
    "summon": {
      "delay_ticks": 20,
      "require_player_nearby": true,
      "player_range": 9.0,
      "allow_peaceful": false,
      "count": 1,
      "spawn_offset": [0, 0, 0],
      "nbt": "",
      "finalize_spawn": true,
      "max_attempts": 20,
      "retry_interval_ticks": 4
    }
  },
  "duplicate": {
    "mode": "allow_multiple",
    "search_radius": 16
  }
}
```

`death` 模式的结构完全相同，只需把 `mode` 换成 `"death"` 并把 `home` 换成 `death`：

```json
"placement": {
  "mode": "death",
  "death": {
    "offset": [0, 0, 0],
    "place_delay_ticks": 0,
    "force_place": false,
    "search_failure_fallback": true,
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": false,
    "avoid_blocks": []
  }
}
```

---

### 字段详细说明

#### 顶层字段

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `schema_version` | int | **必填** | 无 | 必须为 `4`。缺失、非数字或其他数值都会跳过该条目。 |
| `enabled` | bool | 选填 | `true` | 设为 `false` 时忽略整个条目。 |
| `priority` | int | 选填 | `0` | 多个条目匹配同一个实体时使用，数字越大越优先；同优先级时按条目 ID 字典序取较小者。 |
| `entity` | string 或 string[] | **必填** | 无 | 目标实体 ID，例如 `"minecraft:warden"`；也可写成数组共用同一套规则。数组中的实体必须全部已注册，否则整个条目被跳过。 |

#### `activation`：激活条件

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `item` | string | **必填** | 无 | 激活重生笼所需物品 ID。物品必须已注册，否则条目被跳过。 |
| `amount` | int | 选填 | `1` | 激活所需的物品**总数**，最小为 `1`。**按玩家背包总量计算，不是单组上限**：手持物品必须是钥匙物品，然后从主手 → 副手 → 快捷栏 → 背包依次扣除，直到扣满。因此可以设置超过单组上限的数值（例如 `100`）。护甲槽不计入；潜影盒、收纳袋等容器内部不打开。 |
| `consume` | bool | 选填 | `true` | 是否消耗钥匙物品。设为 `false` 时不消耗；创造模式本身也不会消耗。 |

#### `placement`：重生笼放置

`placement` 只包含模式选择与按模式嵌套的配置对象：

| 字段 | 类型 | 必填/选填 | 说明 |
|---|---|---|---|
| `mode` | string | **必填** | `"death"` 或 `"home"`。缺失或非法时跳过整条规则并输出警告。 |
| `death` | object | `mode: death` 时必填 | 选中模式对应的配置块，缺失时跳过整条规则并输出警告。 |
| `home` | object | `mode: home` 时必填 | 同上。 |

两个模式对象**共用同一套字段**，默认值也相同：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `offset` | int[3] | `[0, 0, 0]` | 相对模式基准点（死亡位置 / 出生点）的偏移，只应用一次 |
| `place_delay_ticks` | int | `0` | Boss 死后等待多少 tick 再放置重生笼，最小 `0`。用于**等过 Boss 的死亡动画与它的战利品箱子** —— 有些模组的宝箱会在死亡动画结束后才出现，若重生笼先放下去会被顶掉或被迫失败（暮色森林的米诺菇就是这种情况）。暮色森林条目默认 `30`，幻影骑士 `100` |
| `force_place` | bool | `false` | 是否跳过搜索，直接在 `基准点 + offset` 放置。**会覆盖该处的任何方块**，见下方警示 |
| `search_failure_fallback` | bool | `true` | 两趟搜索都失败后是否在该坐标强制放置。**同样会覆盖该处的任何方块**，见下方警示 |
| `search_down` | int | `32` | 从基准点向下搜索的最大格数，最小 `0` |
| `search_up` | int | `16` | 从基准点向上搜索的最大格数，最小 `0` |
| `horizontal_radius` | int | `4` | 垂直搜索失败后水平搜索的半径，最小 `0` |
| `require_ground` | bool | `true` | 搜索时是否要求下方有可站立的完整方块 |
| `avoid_fluids` | bool | `false` | 搜索时是否避开流体 |
| `avoid_blocks` | string[] | `[]` | **搜索第一趟强制避开**的方块 ID；第一趟无果时会忽略它再搜一遍，因此**避让永远不会导致放不出笼子** |

放置语义：

- `force_place: true`：目标区块已加载时直接放在该坐标，**完全不执行搜索**，因此也不看 `avoid_blocks`。
- `force_place: false`：以该坐标为搜索中心，顺序为**向下 → 向上 → 水平**，共两趟：
  - **第 1 趟**：遵守 `avoid_blocks`，候选位是它本身或其下方的方块时跳过该候选；
  - **第 2 趟**：第 1 趟一无所获时，**忽略 `avoid_blocks`** 用同样的顺序再搜一遍。候选位必须是空气或可替换方块，所以被避让的方块永远不会成为目标位——第 2 趟最坏的结果只是"笼子坐在箱子上面"。
- 搜索成功：放在搜索到的位置。
- 两趟搜索都失败且 `search_failure_fallback: true`：在该坐标强制放置（覆盖该处方块）。
- 两趟搜索都失败且 `search_failure_fallback: false`：**不放置**并输出警告。这是现在**唯一**一条会主动放弃放置的分支。
- **`home` 模式搜索失败绝不回退到死亡位置。**
- 无 Home 记录：不放置、不使用死亡点，并输出含实体 ID、UUID、死亡维度与坐标的警告。

> **⚠️ 明确警示（给整合包作者）：** `force_place: true` 与 `search_failure_fallback: true` 这两条路径**跳过搜索，并且会覆盖目标位置上的任何方块**——宝箱、基岩、别的模组的 BOSS 刷怪笼、灾变祭坛都照overwrite，**旧方块及其内容直接蒸发、不掉落**。这是刻意的设计：这两条路径的语义就是"无论如何都必须把笼子放出来"，不存在任何"保护方块"底线可以不覆盖它们。
>
> 由于重生笼方块在生存模式下不可破坏，误放的位置玩家无法自行清除，规则作者应据此谨慎设置 `offset`，并优先用 `avoid_blocks` + 正常的搜索路径让笼子自己找位置。

#### `avoid_blocks`：搜索第一趟强制避开

`avoid_blocks` 是 v4 里**唯一的**方块列表，三段式语义必须分清：

| 场景 | `avoid_blocks` 是否生效 |
|---|---|
| 普通搜索第 1 趟 | **生效**：候选位本身或其下方命中列表时跳过该候选 |
| 普通搜索第 2 趟（第 1 趟一无所获时才发生） | **不生效**：为了不丢笼子，忽略整个列表再搜一遍 |
| `force_place: true` | **完全不看**：这条路根本不执行搜索 |
| `search_failure_fallback: true` 的兜底写入 | **完全不看**：这条路是"两趟都失败"之后的强制写入 |

因此 **`avoid_blocks` 只能影响笼子放在哪里，永远不能阻止笼子被放出来**。想彻底避开某类方块，正确的做法是把它的 id 加进 `avoid_blocks`，而不是期待一个"不可覆盖"的底线——v3 的 `protected_blocks` 与全局 `compat.protectedBlockIds` 已在本版本**删除**（原因见 [`docs/home-respawn-design.md`](docs/home-respawn-design.md) §5.5）。

> 保护与搜索硬跳过叠加会造成一个致命失败模式：**搜索无候选 → 兜底 → 被"保护"拒绝 → 笼子完全不出现**。实测出现在暮色森林的米诺菇 / 雪怪首领 / 冰雪女王，因此 v4 改用「两趟搜索 + 只有写入路径能覆盖」取代了那条硬底线。

#### `death`：死亡触发条件

| 字段 | 类型 | 选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `player_kill_only` | bool | 选填 | `false` | 设为 `true` 时只有玩家直接或间接造成的击杀才会生成重生笼 |
| `dimensions` | string[] | 选填 | `[]` | 允许生成的维度 ID 列表，空数组表示不限制 |
| `biomes` | string[] | 选填 | `[]` | 允许生成的生物群系 ID 列表，空数组表示不限制 |

#### `spawn`：生成行为

`spawn` 与 `placement` 一样是**先选模式、再放子对象**的结构：

| 字段 | 类型 | 必填/选填 | 说明 |
|---|---|---|---|
| `mode` | string | **必填** | `"summon"`：直接生成 `entity`（原有行为）。`"transform"`：激活时把重生笼**变成另一个方块**，由那个方块产生 Boss（见下）。缺失或非法时跳过整条规则并输出警告。 |
| `summon` | object | `mode: summon` 时必填 | 直接生成实体时的配置块。 |
| `transform` | object | `mode: transform` 时必填 | 交给方块生成时的配置块。 |

> **v3 起 `spawn` 整体是必填的**：`spawn.mode` 与选中的那个子对象缺一不可，只写 `spawn: {}` 或完全不写 `spawn` 都会被拒绝。**不再有隐式 `"summon"` 默认值**，内置规则全部显式写了 `"mode": "summon"`。
>
> 两个子对象**不共用字段**：直接生成实体与交出方块几乎没有交集，因此它们是两套独立的字段表。

`spawn.summon`：直接生成实体

| 字段 | 类型 | 选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `delay_ticks` | int | 选填 | `20` | 右键点亮后等待多少 tick 再尝试生成，最小 `0` |
| `require_player_nearby` | bool | 选填 | `true` | 生成时是否要求附近有存活玩家 |
| `player_range` | double | 选填 | `9.0` | 附近玩家检测范围，最小 `1.0` |
| `allow_peaceful` | bool | 选填 | `false` | 是否允许在和平难度下生成 |
| `count` | int | 选填 | `1` | 一次生成的实体数量，最小 `1`。`count > 1` 时同一批实体继承同一个 Home |
| `spawn_offset` | int[3] | 选填 | `[0, 0, 0]` | 实体生成位置相对重生笼方块的偏移 |
| `nbt` | string | 选填 | `""` | 生成实体时额外写入的 SNBT。非空但无法解析时整条跳过。**不要在这里写 `Pos`**：实体的最终位置由重生笼方块坐标 + `spawn_offset` 决定，NBT 中的位置会被覆盖 |
| `finalize_spawn` | bool | 选填 | `true` | 对 `Mob` 是否调用 `finalizeSpawn` |
| `max_attempts` | int | 选填 | `20` | 最大尝试次数。`-1` 表示无限。达到上限后恢复未点亮并重置 |
| `retry_interval_ticks` | int | 选填 | `4` | 生成失败后的重试间隔，最小 `1` |

`spawn.transform`：交给方块生成

| 字段 | 类型 | 选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `transform_block` | string | **必填** | 无 | 要变成的方块 ID。**不写或无法解析则整条规则被跳过**。解析时不查注册表，因此可以写未安装模组的方块，那条规则只是永远不会触发 |
| `delay_ticks` | int | 选填 | `20` | 点亮后等待多少 tick 再替换方块，最小 `0`。用于让重生笼播完开启动画 |
| `allow_peaceful` | bool | 选填 | `false` | 是否允许在和平难度下转换 |
| `nbt` | string | 选填 | `""` | 写到新方块 BlockEntity 上的 SNBT，等同 `/setblock` 命令的尾随标签。留空表示不改动 BlockEntity |

#### `spawn.mode: "transform"`：交给别的方块生成

有些 Boss **不是一个实体**，直接生成会出错。最典型的是暮色森林的**幻影骑士**：它其实是**6 个实体**，靠 64 格内的距离互相判定为同组，编号（`MyNumber` 0..5）决定谁持有血条、谁拿什么武器。如果只生成 1 个：

- 落单骑士死时会走"最后一个"分支 → **掉落整套 Boss 战利品 + 奖杯 + 成就，并把骑士要塞标记为已攻克**，杀 1 个就能刷
- 若 64 格内还有其它幻影骑士，它的死亡动画**永远无法完成**（碰撞箱已变成 0×0×0 且持续上浮）→ 玩家看到"打不死、不消失的幽灵"
- 它会**凭空生成一个黑暗宝箱**

暮色森林自带 Boss 刷怪笼方块，那才是它生成 Boss 的正规方式（数量、编号、home 点、战利品全部正确）。因此：

```json
"spawn": {
  "mode": "transform",
  "transform": {
    "transform_block": "twilightforest:knight_phantom_boss_spawner",
    "delay_ticks": 20,
    "allow_peaceful": false,
    "nbt": ""
  }
}
```

玩家用钥匙右键 → 重生笼替换成暮色的刷怪笼 → 玩家在 9 格内时刷怪笼自动生成 Boss 并**销毁自身**。

注意事项：

- 目标方块由 ID 解析，**没有编译期依赖**，因此适用于任何模组
- 和平难度下默认**拒绝转换**（保留重生笼、不消耗钥匙），除非规则设了 `allow_peaceful: true`。原因：暮色的刷怪笼不会在和平难度触发，而它是 `strength(-1.0F, ...)` 即**不可破坏**，转换过去会留下一个拆不掉的方块
- `mode` 为 `transform` 时**只有 `transform` 子对象生效**（`transform_block` / `delay_ticks` / `allow_peaceful` / `nbt`）；`count` / `spawn_offset` / `player_range` / `require_player_nearby` / `finalize_spawn` / `max_attempts` / `retry_interval_ticks` 都不参与——只换一个方块，没有"数量"、没有偏移、`setBlock` 也不会失败

#### `duplicate`：重复重生笼策略

| 字段 | 类型 | 选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `mode` | string | 选填 | `allow_multiple` | `allow_multiple`：不检查附近已有笼；`keep_existing`：附近已有同种笼时不再放置；`replace_existing`：附近已有同种笼时先移除旧笼再放置 |
| `search_radius` | int | 选填 | `16` | 重复检查半径，最小 `0`，实际最大检查半径为 `32`。仅在 `mode` 不是 `allow_multiple` 时生效 |

> 重复检测只统计真实存在的 `boss_respawner` 方块，不会把残留的幽灵 BlockEntity 当作已有重生笼。
> 普通搜索时，已被本模组重生笼占据的方块本身不会成为候选位置，搜索会继续寻找其它位置。
> 在 `force_place: true` 且 `mode: allow_multiple` 时，固定坐标语义优先：同一坐标最终只能保留一个方块实体，后放置者会覆盖先放置者。

---

### 加载与校验规则

- 文件路径格式：`data/<namespace>/boss_respawner/entries/<任意文件名>.json`
- `schema_version` 必须显式存在且等于 `4`。
- `placement.mode` 必须显式存在且为 `death` 或 `home`。
- 选中的模式对象（`placement.death` 或 `placement.home`）必须存在。
- `spawn.mode` 必须显式存在且为 `summon` 或 `transform`。
- 选中的生成模式对象（`spawn.summon` 或 `spawn.transform`）必须存在；`transform` 还必须给出可解析的 `transform_block`。
- `entity` 必须至少包含一个有效实体 ID，且数组中的所有实体都必须已注册。
- `activation.item` 必须指向已注册物品。
- `spawn.summon.nbt` / `spawn.transform.nbt` 非空时必须是合法 SNBT。
- `enabled = false` 的条目直接忽略。
- 以上任一条件不满足，整条规则被跳过并输出警告，不影响其他条目。
- 多个数据包可以通过原版数据包优先级覆盖同名 JSON 文件。

### 从旧版本迁移

Schema v1、v2 与 v3 都已**不再被解析**（有意的破坏性变更）。当前版本是 **schema v4**：在 v3 的基础上**删除了 `protected_blocks` 字段与全局 `compat.protectedBlockIds` 配置项**，`avoid_blocks` 成为唯一的方块列表。三段迁移表如下，请**按顺序**读完再改文件。

日志会针对不同旧版本给出各自的提示：v3 提示 `protected_blocks` 已被 v4 删除、`avoid_blocks` 现在是唯一的方块列表；v2 提示 `spawn` 要改成模式 + 子对象；v1 提示扁平 `placement` 要改成 `placement.<mode>`。收到任意一条都说明该文件必须迁移。

#### v3 → v4

| v3 | v4 |
|---|---|
| `schema_version: 3` | `schema_version: 4` |
| `placement.<mode>.protected_blocks` | **字段被删除**。里面的方块 id **应整体并入同级的 `avoid_blocks`**（箱子、祭坛等都属于"希望避让"的方块） |
| 全局 `compat.protectedBlockIds` | **配置项被删除**。它默认含的 `cataclysm:boss_respawner` 应写进每条规则的 `avoid_blocks` |
| 搜索：`avoid_blocks` 与 `protected_blocks` 同时过滤候选，命中 `protected_blocks` 时**放弃放置** | 搜索：**两趟**。第 1 趟遵守 `avoid_blocks`；第 1 趟无果时第 2 趟忽略它再搜一遍，因此避让**永远不会**导致放不出笼子 |

> **不要只改版本号。** v4 的解析器**不读取** `protected_blocks`，残留的键不会报错、也不会生效——那些方块会立刻失去避让。必须把里面的 id 搬进 `avoid_blocks`。

#### v2 → v4

| v2 | v4 |
|---|---|
| `schema_version: 2` | `schema_version: 4` |
| `spawn.delay_ticks`、`spawn.count` 等字段**直接**放在 `spawn` 下 | 整体移进 `spawn.summon`，并补一行 `"mode": "summon"` |
| *(无)* | `spawn.mode` —— v3 新增必填，取 `"summon"` 或 `"transform"` |
| *(无)* | `spawn.transform` —— 选中 `"transform"` 时的子对象，`transform_block` 必填 |
| *(无)* | `placement.<mode>.place_delay_ticks` —— v3 新增，默认 `0` |
| *(无)* | 没有 `protected_blocks` 可迁移：v2 从来没有这个字段 |

扁平 `spawn`（字段直接放在 `spawn` 下、没有 `mode`）会被明确拒绝，日志里会指出"这是 v2 结构"。最省事的迁移方式：补上 `"mode": "summon"`，再把原来的字段整体挪进 `spawn.summon`：

```json
"spawn": {
  "mode": "summon",
  "summon": {
    "delay_ticks": 20,
    "require_player_nearby": true,
    "player_range": 9.0,
    "allow_peaceful": false,
    "count": 1,
    "spawn_offset": [0, 0, 0],
    "nbt": "",
    "finalize_spawn": true,
    "max_attempts": 20,
    "retry_interval_ticks": 4
  }
}
```

#### v1 → v4

v1 的扁平 `placement` 同样会被明确拒绝（日志会指出"这是 v1 结构"）：

| v1 | v4 |
|---|---|
| `schema_version: 1` | `schema_version: 4` |
| `placement.offset` | `placement.<mode>.offset` |
| `placement.search_down` | `placement.<mode>.search_down` |
| `placement.search_up` | `placement.<mode>.search_up` |
| `placement.horizontal_radius` | `placement.<mode>.horizontal_radius` |
| `placement.require_ground` | `placement.<mode>.require_ground` |
| `placement.avoid_fluids` | `placement.<mode>.avoid_fluids` |
| `placement.avoid_blocks` | `placement.<mode>.avoid_blocks`（v4 里它是唯一的方块列表） |
| `spawn.*`（扁平） | `spawn.summon.*`，并补上必填的 `spawn.mode` |
| *(无)* | `placement.mode` —— v2 新增必填，取 `"death"` 或 `"home"` |
| *(无)* | `placement.<mode>.force_place` —— v2 新增，默认 `false`（**会覆盖任何方块**） |
| *(无)* | `placement.<mode>.search_failure_fallback` —— v2 新增，默认 `true`（**同上**） |
| *(无)* | `placement.<mode>.place_delay_ticks` —— v3 新增，默认 `0` |
| 全局 `forcePlaceOnNoSafeSpot` | `placement.<mode>.search_failure_fallback`（按规则单独设置） |
| 全局 `foreignCageBlockIds` | `placement.<mode>.avoid_blocks`（v4 起唯一的方块列表） |
| 全局 `debug.logPlacement` | 现为 `debug.debugInfo` |

最省事的迁移方式：把原来的扁平 `placement` 内容整体挪进 `placement.death`，然后补上 `"mode": "death"`，并显式写出新增字段：

```json
"placement": {
  "mode": "death",
  "death": {
    "offset": [0, 0, 0],
    "place_delay_ticks": 0,
    "force_place": false,
    "search_failure_fallback": true,
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": false,
    "avoid_blocks": ["cataclysm:boss_respawner"]
  }
}
```

## TOML 全局配置

生成于 `config/boss_respawner-common.toml`：

```toml
[general]
# Boss重生笼功能总开关。关闭后不会生成或召唤任何重生笼。
enableMod = true
# 是否用原版发光描边高亮 Boss 重生笼（客户端显示效果，配置在 common）。
highlightBossRespawner = true
# 高亮显示距离，单位为格。默认 32。
highlightBossRespawnerRange = 32

[debug]
# 调试信息：在日志中打印 Boss 重生笼的调试信息。默认 false。
debugInfo = false
```

放置与兼容策略现在**全部**由 JSON 规则控制：全局配置只保留总开关、客户端高亮与调试开关。schema v4 已删除原先的 `[compat] protectedBlockIds` 保护底线（以及更早的 `forcePlaceOnNoSafeSpot` / `foreignCageBlockIds`），需要避让的方块请写进每条规则的 `avoid_blocks`。

## 开发验证

```bat
gradlew.bat compileJava
gradlew.bat test
gradlew.bat build
gradlew.bat runServer
gradlew.bat runClient
```

`gradlew.bat test` 运行的是不启动游戏的纯逻辑单元测试，涵盖：

- schema v4 的解析与各类拒绝路径（含 v3 结构、v1 扁平 `placement`、v2 扁平 `spawn`、缺失 `mode`、缺失模式对象、非法模式、版本门），以及"残留的 `protected_blocks` 键不产生任何效果"；
- `BossHomeSavedData` 的 NBT 往返、去重合并、区块索引、放置延迟与 `dataVersion` 拒绝策略；
- 全部 32 个内置 JSON 的真实解析验证、字段默认值与迁移后的 `avoid_blocks` 列表（暮色森林 9 条各 18 项、其余各 1 项）；
- 验收数据包（`docs/examples/acceptance-datapack`）的 6 条正向 / 4 条负向条目校验；
- 对已删除全局配置项（含 `protectedBlockIds`）与已删除保护分支的源码引用检查，以及 `PlacementResolver` 两趟搜索的接线检查。
