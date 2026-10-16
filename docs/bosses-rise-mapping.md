# 首领崛起（Bosses Rise）BOSS → 激活物品映射表

**日期：** 2026-10-16
**用途：** 为 Universal Boss Respawner 与首领崛起联动时，配置 Boss 重生笼 JSON 条目提供参考。
**模组信息：** modId **`block_factorys_bosses`** · 版本 `2.1.2` · Maven `curse.maven:bossesrise-1314084:8123167`
**数据来源：** 依赖对应的官方 jar（`bossesrise-1314084-8123167.jar`）中的 `assets/block_factorys_bosses/lang/{zh_cn,en_us}.json`、`models/item/*.json`、`recipe/*.json` 与 `data/block_factorys_bosses/loot_table/**`

---

## 〇、钥匙选取原则

本模组的用途是：**一个 BOSS 已经被某位玩家打掉，但其他玩家还没打**。因此复活钥匙必须满足：

1. **在打该 BOSS 之前就能获得** —— BOSS 的掉落物天然不满足（你打完才有，而那时已经不需要复活了）；
2. 来自同一模组；
3. 有一定获取难度（稀缺性）。

这条原则直接决定了下面这个结果：**首领崛起的 5 个 BOSS 掉落物全部不能用作钥匙。**

| BOSS | 它的专属掉落 | 能用吗 |
|---|---|---|
| 阿什洛德 | `dragon_bone` 飞龙之骨 / `dragon_skull` 飞龙头骨 | ❌ 打完才有 |
| 斯洛克 | `sandworm_gauntlet` 沙虫护手 | ❌ 打完才有 |
| 赫尔瓦 | `knight_sword` 赫尔瓦的剑 | ❌ 打完才有 |
| 斯科尔 | `ice_gauntlet` 雪怪护手 | ❌ 打完才有 |
| Nerakyss | `kraken_tooth` 海怪之牙 | ❌ 打完才有 |

经过考察，首领崛起**无法为每个 BOSS 各找到一件"打 BOSS 前"的专属物品**，因此统一使用下面这把钥匙。

---

## 一、统一钥匙：远古试炼钥匙

| BOSS（中文） | BOSS 实体 ID | 激活物品（中文） | 激活物品 ID |
|---|---|---|---|
| 阿什洛德，炼狱飞龙 | `block_factorys_bosses:infernal_dragon` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| 斯洛克，巨型沙虫 | `block_factorys_bosses:sandworm` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| 赫尔瓦，冥界骑士 | `block_factorys_bosses:underworld_knight` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| 斯科尔，霜冻雪怪 | `block_factorys_bosses:yeti` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |
| **Nerakyss, The Kraken**（无官方中文） | `block_factorys_bosses:kraken` | 远古试炼钥匙 | `block_factorys_bosses:ancient_trial_key` |

### 为什么它满足条件（已核实）

| 条件 | 核实结果 |
|---|---|
| 打 BOSS 前可获取 | ✅ 来自**冥界竞技场**的试炼刷怪笼宝箱：`loot_table/spawners/ancient_trial_key.json`（`"type": "minecraft:chest"`）。结构 nbt 有 `underworld_arena_empty_trial_spawner` / `_soul_skeleton_trial_spawner` / `_wither_knight_trial_spawner` |
| 稀缺 | ✅ **没有任何合成配方**（39 个 recipe 文件里都不含它），只能开箱/打怪获得 |
| 同模组 | ✅ `block_factorys_bosses` |
| 设定合理 | ✅ 名为"试炼钥匙"，用于开启试炼 |

另有两个次要来源（同一模组的小怪掉落）：`soul_skeleton`（灵魂骷髅）、`soul_knight_wither_skeleton`（凋灵骷髅骑士）、`dragon_guard_sword`（飞龙守卫）、`flaming_skeleton_guard_sword` / `_fireball`（燃烧的飞龙守卫 / 燃烧的骷髅射手）。

### 已接受的缺点

**5 个 BOSS 共用同一把钥匙。** 玩家只要攒够钥匙，就能反复复活任意 BOSS。这是在本模组设计原则下"找不到足够多合适的专属物品"时的取舍。

---

## 二、箱子情况：无需额外保护

按同样的思路（"重生笼绝不能顶掉箱子"）核对了首领崛起，结论是**它没有"BOSS 战利品箱"机制**，因此它的 5 个条目没有额外保护任何箱子。

| 核对项 | 结果 |
|---|---|
| 是否有自己的箱子方块 | ❌ 没有（`blockstates` 下不存在任何 chest / crate / barrel 方块），结构使用原版 `minecraft:chest` |
| 是否有装着 BOSS 物品的箱子表 | ❌ 5 个 `chests/*.json`（`dragon_tower` / `dragon_tower_common` / `underworld_arena_common` / `_rare` / `_vault` / `_trash`）**都不含** `dragon_bone` / `dragon_skull` / `sandworm_gauntlet` / `knight_sword` / `ice_gauntlet` / `kraken_tooth` |
| BOSS 战利品如何发放 | 战利品表为 `"type": "minecraft:entity"` —— **直接掉物品** |
| 唯一的箱类来源 | `spawners/ancient_trial_key.json`（`"type": "minecraft:chest"`），它装的是**钥匙本身**，不是 BOSS 战利品 |

> 对比：暮色森林的 `darktower_boss.json` 是**箱子**表且装着暮初恶魂的整套战利品，所以那边的规则把箱子写进了 `avoid_blocks`。详见 [`twilight-forest-mapping.md`](twilight-forest-mapping.md) 第二节。

### ⚠️ 仍有一个缺口：原版箱子

它的结构（冥界竞技场地窖等）里仍有**原版箱子**。若要连这些也避开，需要把 `minecraft:chest` 与 `minecraft:trapped_chest` 加进每条规则的 `placement.home.avoid_blocks`（v4 起**没有**全局 `compat.protectedBlockIds` 这种东西，它已随 `protected_blocks` 字段一并删除；而且任何"保护"语义都无法阻止 `force_place` / 兜底写入覆盖方块）。这一步尚未执行，等你确认。
## 三、⚠️ 关于 BOSS 数量：4 个还是 5 个？

设计讨论中按 **4 个 BOSS** 处理，但 `2.1.2` 的实体与语言文件里实际有 **5 个**命名 BOSS，第 5 个是：

```
entity.block_factorys_bosses.kraken = Nerakyss, The Kraken
```

它有完整的 BOSS 特征：专属成就（`advancement.kill_kraken` "Terror of the Depths"、`no_hit_kraken` "Wraith of the Tide"）、多阶段字幕（`kraken.intro` / `phase_transition` / `death_final`）、独立部件实体（`kraken_tentacle` / `kraken_cannon`）、专属掉落 `kraken_tooth`。

**本映射文档与内置 JSON 按 5 个 BOSS 处理**（多写一个不会有害：如果某个版本没有这个实体，该条目会在 `/reload` 时因"实体未注册"被跳过并输出警告）。若你确认不需要海怪，删除 `kraken.json` 即可。

### ⚠️ 海怪完全没有官方中文

不只是实体名——它的物品（`kraken_tooth` / `kraken_trident` / `kraken_cannon_item`）和全部字幕**都只有 `en_us`**，`zh_cn.json` 里一条 `kraken` 都没有。所以游戏内会显示英文 `Nerakyss, The Kraken`。本文件中的"海怪之牙"是**按英文暂译**，不是官方译名。

---

## 四、明确不适合的实体

`entity.*` 里另有 25 个条目，都不是可重复召唤的 BOSS：

| 类别 | 实体 | 中文 | 原因 |
|---|---|---|---|
| 结构方块 | `big_cage` / `cage` / `big_cage_skelly` / `cage_skelly` | 笼子 | 是本模组**自己的**关押方块实体 |
| 发射物 | `ice_spike` / `ice_spike_pr` / `poison_spit_pr` / `rift_projectile` / `blazing_fire_ball` / `sword_wave` / `soul_shockwave` / `glacial_shove` / `cannonball` | 冰锥 / 剧毒唾沫 / 剑气波… | BOSS 技能产物 |
| 场地效果 | `fire_area` / `poison_area` / `sand_column` / `rift` / `knight_mark` | 火焰区域 / 剧毒区域 / 沙柱… | BOSS 技能产物 |
| BOSS 召唤物 | `dragon_guard_sword` / `flaming_skeleton_guard_sword` / `_fireball` | 飞龙守卫 / 燃烧的飞龙守卫 / 燃烧的骷髅射手 | 阿什洛德的召唤护卫 |
| BOSS 召唤物 | `frozen_skeleton` / `soul_skeleton` / `soul_knight_wither_skeleton` | 冰冻骷髅 / 灵魂骷髅 / 凋灵骷髅骑士 | 场地小怪（**同时也是试炼钥匙的来源**） |
| BOSS 召唤物 | `pile_of_bones` | 骨头堆 | 斯洛克战的召唤物 |
| 海怪部件 | `kraken_tentacle` / `kraken_cannon` | 海怪触手 / 海怪炮 | 海怪的部件 |
| 结构小怪 | `pirate_captain` / `pirate_rook` / `crossbow_pirate` | 海盗船长 / 海盗车 / 弩手海盗 | **无专属掉落、无中文名**，非 BOSS |

---

## 五、特殊规则建议

### 赫尔瓦，冥界骑士（Underworld Knight）

竞技场型 BOSS，位于冥界竞技场（`underworld_arena_key` 开门）。内置条目使用 `mode: "home"`，锚定首次观察到它的竞技场位置。

### 斯洛克，巨型沙虫（Sandworm）

沙虫在**沙漠地下**穿行，死亡位置常在地下沙层。因为是 `home` 模式（锚定首次出现的位置，而非死亡点），默认参数即可；不必为它加大 `search_up`。

### 阿什洛德，炼狱飞龙（Infernal Dragon）

在下界/火焰环境中作战。`avoid_fluids` 保持默认 `false`，避免因熔岩判定导致搜索失败。

### Nerakyss，海怪（Kraken）

水下 BOSS，竞技场在海底。内置条目保持 `require_ground: true`（海床是坚实方块）。若实测发现笼子难以放置，可单独为它设 `"require_ground": false`；`search_failure_fallback: true` 也能兜底。

---

## 六、内置 JSON 条目

已生成 5 个条目，目录：

```text
src/main/resources/data/block_factorys_bosses/boss_respawner/entries/
├── infernal_dragon.json
├── sandworm.json
├── underworld_knight.json
├── yeti.json
└── kraken.json
```

全部使用 `mode: "home"` + `activation.item = block_factorys_bosses:ancient_trial_key`，其余字段沿用现有内置条目的完整模板写法。骨架（**v4 形态**，实际内置条目还含 `place_delay_ticks`）：

```json
{
  "schema_version": 4,
  "enabled": true,
  "priority": 0,
  "entity": "block_factorys_bosses:underworld_knight",
  "activation": { "item": "block_factorys_bosses:ancient_trial_key", "amount": 1, "consume": true },
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
      "avoid_blocks": ["cataclysm:boss_respawner"],
      "place_delay_ticks": 0
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
