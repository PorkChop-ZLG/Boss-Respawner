# 暮色森林（Twilight Forest）BOSS → 激活物品映射表

**日期：** 2026-10-16
**用途：** 为 Universal Boss Respawner 与暮色森林联动时，配置 Boss 重生笼 JSON 条目提供参考。
**模组信息：** modId `twilightforest` · 版本 `4.8.3345` · Maven `curse.maven:the-twilight-forest-227639:7797302`
**数据来源：** 依赖对应的官方 jar（`build/tmp-mods/twilightforest-7797302.jar`）中的 `assets/twilightforest/lang/zh_cn.json`、`models/item/*.json`、`recipe/*.json` 与 `data/twilightforest/loot_table/**`

---

## 〇、钥匙选取原则

本模组的用途是：**一个 BOSS 已经被某位玩家打掉，但其他玩家还没打**。因此复活钥匙必须满足：

1. **在打该 BOSS 之前就能获得** —— BOSS 的掉落物天然不满足；
2. 来自同一模组；
3. 有一定获取难度（稀缺性）。

暮色森林给 8 个主线 BOSS **各做了一个专属奖杯**（`*_trophy`），主题上是最理想的钥匙——

| BOSS | 专属奖杯 | 能用吗 |
|---|---|---|
| 娜迦 | `naga_trophy` | ❌ 打完才有 |
| 巫妖 | `lich_trophy` | ❌ 打完才有 |
| 米诺菇 | `minoshroom_trophy` | ❌ 打完才有 |
| 九头蛇 | `hydra_trophy` | ❌ 打完才有 |
| 幻影骑士 | `knight_phantom_trophy` | ❌ 打完才有（走 `entities/knight_phantom_defeated.json` 结算） |
| 暮初恶魂 | `ur_ghast_trophy` | ❌ 打完才有 |
| 雪怪首领 | `alpha_yeti_trophy` | ❌ 打完才有 |
| 冰雪女王 | `snow_queen_trophy` | ❌ 打完才有 |

**但它们全部是 BOSS 掉落物，因此全部不能用。** 8 个 BOSS 想要一一对应到"打 BOSS 前"的专属物品很难，所以暂时统一使用下面这件。

---

## 一、统一钥匙：魔法地图核心

| BOSS（中文） | BOSS 实体 ID | 激活物品（中文） | 激活物品 ID |
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

> 谜题羊按约定计入 BOSS 列表，同样使用魔法地图核心。

### 为什么它满足条件（已核实）

| 条件 | 核实结果 |
|---|---|
| 打 BOSS 前可获取 | ✅ 有**无序合成配方**：乌鸦羽毛（`raven_feather`）+ 火炬浆果（`torchberries`）+ 荧石粉（`c:dusts/glowstone`） |
| 同模组 | ✅ `twilightforest`（前两种材料也来自暮色森林，必须先进入暮色森林才能凑齐） |
| 设定合理 | ✅ 是"魔法地图"的核心材料，暮色森林的标志性物品 |

另有一个次要来源：`death_tome`（死灵书，巫妖的召唤物）掉落。

### ⚠️ 诚实提醒：它的"稀缺性"弱于远古试炼钥匙

`magic_map_focus` 是**可合成**的，且材料（乌鸦羽毛、火炬浆果、荧石粉）都算不上稀有——**一旦凑齐材料就能批量合成**，不像首领崛起的远古试炼钥匙那样没有配方、只能开箱获取。

因此：

- 它的实际门槛是"**必须先进入暮色森林并收集到两种本地材料**"，而不是"每个 BOSS 一把限量的钥匙"；
- 9 个 BOSS 共用同一种钥匙，且钥匙可无限产出，**稀缺性主要由材料成本而非数量上限提供**；
- 一个附带的取舍：`magic_map`（魔法地图本体）的配方正是 **8 张纸 + 1 个魔法地图核心**，所以玩家要在"做地图"和"复活 BOSS"之间分配核心。

如果后续想加强稀缺性，可考虑的手工规则（本模组目前不支持）：限制每人持有量、或改用需要 BOSS 战前探索才能获得的结构物品。

---

## 二、BOSS 战利品箱：必须避开（v4：写入 `avoid_blocks`）

暮色森林的 BOSS 战利品**可能通过箱子发放**，而且它的结构（巫妖塔、迷宫、暗黑高塔、巨魔洞穴等）本身就遍布箱子。重生笼如果落在箱子位置，就会**直接顶掉箱子及其全部战利品**——而 `force_place` / `search_failure_fallback` 路径确实可能覆盖实心方块。

因此暮色森林的 9 个条目把该模组的**所有 17 种箱子**写入 `placement.home.avoid_blocks`（连同迁移过来的 `cataclysm:boss_respawner`，每条共 18 项）。

> **⚠️ 本节在 schema v4 被改写。** v3 时代这些箱子写的是 `placement.home.protected_blocks`（一个"任何情况下都不得覆盖"的字段，并额外带一条全局 `compat.protectedBlockIds` 底线）。**v4 已把该字段与全局配置项一并删除**，因为"保护 + 搜索硬跳过"的叠加会造成 ABANDON：搜索把所有候选都当成受保护而跳过 → 无候选 → 兜底 → 写入前又被保护拒绝 → **笼子完全不出现**。实测就发生在米诺菇 / 雪怪首领 / 冰雪女王。完整推翻理由见 [Home 出生点模式设计 §5.5](home-respawn-design.md#55-avoid_blocks搜索第一趟强制避开以及为什么-v4-删除了-protected_blocks)。

### 现在的语义：两趟搜索，避让只是偏好

`PlacementResolver.findPlacementPos` 对同一个基准点搜**两趟**：

1. **第 1 趟**遵守 `avoid_blocks`：候选位本身或其下方命中列表时跳过该候选，换个位置继续找（这正是"把笼子放在箱子正上方"会被拦下的原因）；
2. 第 1 趟一无所获时，**第 2 趟忽略 `avoid_blocks`** 再搜一遍。

关键性质：搜索候选必须是**空气或可替换方块**，所以箱子**永远不可能成为目标位**。第 2 趟最坏的结果只是"笼子坐在箱子上面"，而不是"笼子不出现"。

| 场景 | `avoid_blocks` 是否生效 | 结果 |
|---|---|---|
| 搜索第 1 趟 | **生效** | 换个位置继续找 |
| 搜索第 2 趟（第 1 趟无果时） | **不生效** | 最坏情况：笼子落在箱子上面，箱子与内容原样保留 |
| `force_place: true` | **完全不看**（不执行搜索） | **覆盖箱子**，内容蒸发不掉落 |
| `search_failure_fallback: true` 的兜底写入 | **完全不看**（两趟都失败之后） | **覆盖箱子**，内容蒸发不掉落 |

因此：

- **不会出现"笼子静默不生成"**——两趟搜索保证只要存在任何空气/可替换候选就会放下去；
- 17 个箱子只需维护在 `avoid_blocks` **一处**；
- 若某条规则设了 `force_place: true`，避让列表对它毫无作用：这是刻意的"无论如何都要放下笼子"语义（见 §5.4）。

> 该行为的回归防护见 `src/test/java/com/zonlong/bossrespawner/placement/PlacementResolverTest.java`（按**值**匹配而非对象身份）、`BuiltinEntriesTest.twilightForestEntriesAvoidChests`（17 个箱子必须在 `avoid_blocks` 里）与 `BuiltinEntriesTest.avoidListsAreExact`（9 条各 18 项）。

### 核实到的证据

| 发现 | 证据 |
|---|---|
| 存在"BOSS 箱子" | `loot_table/darktower_boss.json` 是 **`"type": "minecraft:chest"`**，内含 `twilightforest:ur_ghast_trophy` + `carminite` + `fiery_tears` —— 正是暮初恶魂的整套战利品 |
| 其余 7 个 BOSS | 战利品表是 `"type": "minecraft:entity"`（直接掉物品），其中只有 `knight_phantom_defeated.json` 标为 `empty` 但仍在 pools 里给出奖杯 |
| 结构箱子很多 | 全模组共 **44 个 `type: chest` 的战利品表**，含迷宫（`labyrinth_vault` / `labyrinth_vault_jackpot`）、巨魔洞穴（`troll_vault`）、暗黑高塔（`chests/tower_*`）、极光宫殿（`aurora_cache`）等 |

### 17 种箱子方块（写入 `avoid_blocks`）

| 箱子（中文） | 方块 ID |
|---|---|
| 暮色橡木箱子 / 陷阱箱 | `twilightforest:twilight_oak_chest` / `twilightforest:twilight_oak_trapped_chest` |
| 苍穹木箱子 / 陷阱箱 | `twilightforest:canopy_chest` / `twilightforest:canopy_trapped_chest` |
| 暮色红树木箱子 / 陷阱箱 | `twilightforest:mangrove_chest` / `twilightforest:mangrove_trapped_chest` |
| 黑木箱子 / 陷阱箱 | `twilightforest:dark_chest` / `twilightforest:dark_trapped_chest` |
| 矿石木箱子 / 陷阱箱 | `twilightforest:mining_chest` / `twilightforest:mining_trapped_chest` |
| 分类木箱子 / 陷阱箱 | `twilightforest:sorting_chest` / `twilightforest:sorting_trapped_chest` |
| 时光木箱子 / 陷阱箱 | `twilightforest:time_chest` / `twilightforest:time_trapped_chest` |
| 变化木箱子 / 陷阱箱 | `twilightforest:transformation_chest` / `twilightforest:transformation_trapped_chest` |
| 头颅箱子 | `twilightforest:skull_chest`（**注意：它没有陷阱箱版本**） |

> 这 17 个 ID 来自 `assets/twilightforest/blockstates/*chest*.json`、`models/block/*chest*.json`、`loot_table/blocks/*chest*.json` 与 `zh_cn.json` 四处交叉核对，一致。

### ⚠️ 仍有一个缺口：原版箱子

暮色森林的**部分结构也可能使用原版 `minecraft:chest`**。原版箱子不在上面这 17 个 ID 里，因此当前配置**不会**避开它们。

若要堵住，正确做法是把 `minecraft:chest` 与 `minecraft:trapped_chest` 加进**每条相关规则**的 `placement.home.avoid_blocks`（v4 起没有"全局保护列表"这种东西：`compat.protectedBlockIds` 已随 `protected_blocks` 一并删除，而且任何保护语义都无法阻止 `force_place` / 兜底写入覆盖方块）。这一步尚未执行，等你确认。
## 三、谜题羊（Quest Ram）的特殊说明

谜题羊是本次唯一一个"不是战斗 BOSS"的条目，但它恰好最契合本模组的设计初衷：

| 项目 | 核实结果 |
|---|---|
| 能否被杀 | ✅ 可以。它是 `twilightforest/entity/passive/QuestRam.class`，继承 `net.minecraft.world.entity.animal.Animal`，**没有重写 `isInvulnerableTo`**，且有 `getHurtSound` / `getDeathSound` |
| 死亡掉落 | **什么都不掉**。其战利品表 `loot_table/entities/quest_ram.json` 是空的（只有一个 `random_sequence`，没有 pools） |
| 奖杯来源 | `loot_table/entities/questing_ram_rewards.json` —— 是**喂满 16 色羊毛完成任务的奖励**，不是击杀掉落 |
| 为什么需要复活 | 它是一次性任务 NPC。被击杀后玩家**永久无法完成任务**，这正是本模组要解决的问题 |
| 自带归属点 | 它实现了 `EnforcedHomePoint`（有 `HOME_POINT` / `getRestrictionPoint` / `getHomeRadius`）。本模组**不读取**这个字段，而是在首次观察到它时登记自己的 home，符合设计文档 §2 的非目标 |

---

## 四、其它可击杀目标（本期不做）

这些不是主线 BOSS，但同样是一次性、有专属掉落的可击杀目标。列出来供后续扩展参考。

| 目标（中文） | 实体 ID | 专属掉落 | 掉落可否当钥匙 |
|---|---|---|---|
| 武装巨人 | `twilightforest:armored_giant` | `giant_sword` 巨人剑 | ❌ 打完才有 |
| 巨人矿工 | `twilightforest:giant_miner` | `giant_pickaxe` 巨人镐 | ❌ 打完才有 |
| 巨魔 | `twilightforest:troll` | `magic_beans` 魔豆 | ❌ 打完才有 |
| 雪怪 | `twilightforest:yeti` | `arctic_fur` 极地毛皮 | ❌ 打完才有（且极地生物普遍掉落，不专属） |
| 国王蜘蛛 | `twilightforest:king_spider` | 无掉落表 | — |

---

## 五、明确不适合的实体

| 实体 | 中文 | 原因 |
|---|---|---|
| `twilightforest:snow_guardian` | 冰雪守卫 | 冰雪女王的召唤物 |
| `twilightforest:death_tome` | 死灵书 | 巫妖的召唤物 |
| `twilightforest:harbinger_cube` / `cube_of_annihilation` / `roving_cube` | 立方体系 | 结构陷阱 / 分身 |
| `twilightforest:lich_minion` / `adherent` / `loyal_zombie` | 巫妖仆从 / 信徒 / 忠诚的僵尸 | BOSS 战产物 |
| `twilightforest:upper_goblin_knight` / `lower_goblin_knight` | 哥布林骑士（上/下身） | 组合实体的两半，非独立目标 |
| `twilightforest:slider` | 滑动陷阱 | 方块型陷阱，非生物 |

---

## 六、掉落表核对依据

来源：`data/twilightforest/loot_table/entities/*.json`

```text
naga            -> naga_scale, naga_trophy
lich            -> lich_trophy, 4 种权杖, exanimate_essence, 金装备
minoshroom      -> minoshroom_trophy, diamond_minotaur_axe, meef_stroganoff
hydra           -> hydra_trophy, hydra_chop, fiery_blood
knight_phantom  -> knightmetal_axe/pickaxe/sword, phantom_chestplate, phantom_helmet
                   （奖杯在 entities/knight_phantom_defeated.json，击败结算时给）
ur_ghast        -> ur_ghast_trophy, carminite, fiery_tears
alpha_yeti      -> alpha_yeti_trophy, alpha_yeti_fur, ice_bomb
snow_queen      -> snow_queen_trophy, seeker_bow, triple_bow
quest_ram       -> （空战利品表，什么都不掉）
```

8 个奖杯的物品模型均存在于 `assets/twilightforest/models/item/<boss>_trophy.json`，中文名均存在于 `zh_cn.json`（`block.twilightforest.<boss>_trophy`）。奖杯在本模组里注册为**方块物品**（BlockItem），所以它们的显示名来自 `block.*` 键——但它们仍然全部是 BOSS 掉落物，故不能用作钥匙。

---

## 七、特殊规则建议

### 暮初恶魂（Ur-Ghast）

生活在暗黑高塔顶部的封闭塔楼。内置条目使用 `mode: "home"` 锚定首次观察位置，避免玩家把它引出塔外后笼子出现在荒野。

### 九头蛇（Hydra）

巢穴是火焰沼泽的大型洞穴，地板常被熔岩占据。`avoid_fluids` 保持默认 `false`，避免因熔岩判定导致搜索失败。

### 冰雪女王（Snow Queen）

宫殿由冰/浮冰构成。`require_ground` 判定用的是 `isFaceSturdy(UP)`，冰类方块满足；即便失败也有 `search_failure_fallback: true` 兜底。

### 娜迦（Naga）

娜迦庭院是开阔的露天场地，默认参数即可。

---

## 八、内置 JSON 条目

已生成 9 个条目，目录：

```text
src/main/resources/data/twilightforest/boss_respawner/entries/
├── naga.json
├── lich.json
├── minoshroom.json
├── hydra.json
├── knight_phantom.json
├── ur_ghast.json
├── alpha_yeti.json
├── snow_queen.json
└── quest_ram.json
```

全部使用 `mode: "home"` + `activation.item = twilightforest:magic_map_focus`，并把本模组的 **17 种箱子**与 `cataclysm:boss_respawner` 一起写入 `avoid_blocks`（见第二节），其余字段沿用现有内置条目的完整模板写法。骨架（**v4 形态**，实际内置条目还含 `place_delay_ticks`）：

```json
{
  "schema_version": 4,
  "enabled": true,
  "priority": 0,
  "entity": "twilightforest:naga",
  "activation": { "item": "twilightforest:magic_map_focus", "amount": 1, "consume": true },
  "placement": {
    "mode": "home",
    "home": {
      "offset": [0, 0, 0],
      "force_place": false,
      "search_failure_fallback": true,
      "search_down": 32,
      "search_up": 16,
      "horizontal_radius": 4,
      "require_ground": true,
      "avoid_fluids": false,
      "avoid_blocks": [
        "cataclysm:boss_respawner",
        "twilightforest:canopy_chest",
        "twilightforest:canopy_trapped_chest",
        "twilightforest:dark_chest",
        "twilightforest:dark_trapped_chest",
        "twilightforest:mangrove_chest",
        "twilightforest:mangrove_trapped_chest",
        "twilightforest:mining_chest",
        "twilightforest:mining_trapped_chest",
        "twilightforest:skull_chest",
        "twilightforest:sorting_chest",
        "twilightforest:sorting_trapped_chest",
        "twilightforest:time_chest",
        "twilightforest:time_trapped_chest",
        "twilightforest:transformation_chest",
        "twilightforest:transformation_trapped_chest",
        "twilightforest:twilight_oak_chest",
        "twilightforest:twilight_oak_trapped_chest"
      ],
      "place_delay_ticks": 30
    }
  },
  "death": { "player_kill_only": false, "dimensions": [], "biomes": [] },
  "spawn": {
    "mode": "summon",
    "summon": {
      "delay_ticks": 20, "require_player_nearby": true, "player_range": 9.0, "allow_peaceful": false,
      "count": 1, "spawn_offset": [0, 0, 0], "nbt": "", "finalize_spawn": true,
      "max_attempts": 20, "retry_interval_ticks": 4
    }
  },
  "duplicate": { "mode": "allow_multiple", "search_radius": 16 }
}
```
