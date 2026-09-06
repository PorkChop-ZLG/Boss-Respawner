# Universal Boss Respawner（通用Boss重生笼）

一个数据驱动的 NeoForge 1.21.1 模组：生物/Boss 死亡后，可按照 JSON 配置在死亡地点生成“重生笼”；玩家使用指定物品右键激活后，重生笼会重新召唤该生物。

- **Mod ID：** `boss_respawner`
- **环境：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21
- **定位：** 独立模组，不强依赖 Cataclysm 或任何具体 Boss 模组。

> 当前开发阶段默认带有一个启用的 `minecraft:warden` 示例条目。
> **正式发布前请移除或禁用** `src/main/resources/data/boss_respawner/boss_respawner/entries/minecraft/warden.json`，
> 以保持“默认不干扰任何生物”的设计目标。

---

## 功能

- 生物死亡后按 JSON 规则在死亡地点放置未激活重生笼。
- 方块实体保存实体类型、钥匙物品与生成参数快照。
- 手持正确物品右键：消耗物品、点亮方块、播放动画。
- 到达延迟后尝试生成目标实体；失败后按间隔重试。
- 默认最多尝试 20 次，失败后恢复未点亮、重置，并在聊天栏和日志输出警告。
- 生成成功自动销毁重生笼。
- 同一 Boss 可同时存在多套重生笼（刻意设计）。

## 获取方块

本模组将重生笼 BlockItem 放入原版“刷怪蛋”创造模式标签页。

也可通过命令获取：

```mcfunction
/give @s boss_respawner:boss_respawner
```

## JSON 配置

数据包目录：

```
data/<namespace>/boss_respawner/entries/<任意文件名>.json
```

- 监听目录会递归加载所有 `.json`。
- 执行 `/reload` 后生效。
- 当前支持的 `schema_version` 为 `1`。
- 非法或未注册的条目会被跳过，不影响其他条目。

### 必填/选填总览

| 分类 | 字段 | 必填/选填 | 默认值 |
|---|---|---|---|
| 顶层 | `entity` | **必填** | 无 |
| 顶层 | `activation.item` | **必填** | 无 |
| 顶层 | `schema_version` | 选填 | `1` |
| 顶层 | `enabled` | 选填 | `true` |
| 顶层 | `priority` | 选填 | `0` |
| 激活 | `activation.amount` | 选填 | `1` |
| 激活 | `activation.consume` | 选填 | `true` |
| 放置 | `placement` 整个配置块 | 选填 | 使用默认值 |
| 死亡条件 | `death` 整个配置块 | 选填 | 使用默认值 |
| 生成 | `spawn` 整个配置块 | 选填 | 使用默认值 |
| 重复策略 | `duplicate` 整个配置块 | 选填 | 使用默认值 |

> 除了 `entity` 和 `activation.item`，其他所有字段都可以省略。

---

### 最小 JSON 模板

只填写必须字段即可正常工作，其余全部使用默认值：

```json
{
  "schema_version": 1,
  "entity": "minecraft:warden",
  "activation": {
    "item": "minecraft:echo_shard"
  }
}
```

该模板等效于：

- 任何难度下，监守者死亡后都会在其死亡地点附近放置重生笼；
- 手持 1 个回响碎片右键激活；
- 右键会消耗回响碎片；
- 点亮后延迟 20 tick 尝试生成 1 只监守者；
- 生成时需要附近有玩家；
- 默认允许同一个 Boss 同时存在多个重生笼（不做重复检查）。

---

### 完整示例：监守者

文件：`data/my_pack/boss_respawner/entries/minecraft/warden.json`

```json
{
  "schema_version": 1,
  "enabled": true,
  "priority": 0,
  "entity": "minecraft:warden",
  "activation": {
    "item": "minecraft:echo_shard",
    "amount": 1,
    "consume": true
  },
  "placement": {
    "offset": [0, 1, 0],
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": true,
    "avoid_blocks": ["cataclysm:boss_respawner"]
  },
  "death": {
    "player_kill_only": false,
    "dimensions": [],
    "biomes": []
  },
  "spawn": {
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
  },
  "duplicate": {
    "mode": "allow_multiple",
    "search_radius": 16
  }
}
```

---

### 字段详细说明

#### 顶层字段

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `schema_version` | int | 选填 | `1` | JSON 格式版本。当前只支持 `1`；其他版本会跳过该条目。 |
| `enabled` | bool | 选填 | `true` | 设为 `false` 时忽略整个条目。 |
| `priority` | int | 选填 | `0` | 多个条目匹配同一个实体时使用，数字越大越优先；同优先级时按条目 ID 字典序取较小者。 |
| `entity` | string 或 string[] | **必填** | 无 | 目标实体 ID，例如 `"minecraft:warden"`。也可以写成数组，让多个实体共用同一套规则，例如 `["minecraft:zombie", "minecraft:skeleton"]`。数组中的实体必须全部已注册，否则整个条目被跳过。 |

#### `activation`：激活条件

`activation` 是必填配置块，至少需要提供 `item`。

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `item` | string | **必填** | 无 | 激活重生笼所需物品 ID，例如 `"minecraft:echo_shard"`。物品必须已注册，否则条目被跳过。 |
| `amount` | int | 选填 | `1` | 每次右键激活消耗的物品数量，最小为 `1`。 |
| `consume` | bool | 选填 | `true` | 是否消耗钥匙物品。设为 `false` 时不消耗；创造模式本身也不会消耗。 |

#### `placement`：重生笼放置位置

`placement` 整个配置块可选，省略时全部使用默认值。

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `offset` | int[3] | 选填 | `[0, 0, 0]` | 以生物死亡位置为基础的手动偏移，例如 `[0, 1, 0]` 表示从死亡点上方 1 格开始搜索。 |
| `search_down` | int | 选填 | `32` | 从偏移点向下搜索的最大格数，最小 `0`。 |
| `search_up` | int | 选填 | `16` | 从偏移点向上搜索的最大格数，最小 `0`。 |
| `horizontal_radius` | int | 选填 | `4` | 垂直搜索失败后，水平螺旋搜索的半径，最小 `0`。 |
| `require_ground` | bool | 选填 | `true` | 是否要求重生笼下方有可站立的完整方块。 |
| `avoid_fluids` | bool | 选填 | `true` | 是否避免把重生笼放在流体中或流体上方。 |
| `avoid_blocks` | string[] | 选填 | `[]` | 避免作为重生笼位置或支撑位置的方块 ID 列表。会额外合并 TOML 中的 `foreignCageBlockIds`。 |

#### `death`：死亡触发条件

`death` 整个配置块可选，省略时表示只按实体类型匹配，不额外限制。

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `player_kill_only` | bool | 选填 | `false` | 设为 `true` 时，只有玩家直接或间接造成的击杀才会生成重生笼。 |
| `dimensions` | string[] | 选填 | `[]` | 允许生成的维度 ID 列表，例如 `["minecraft:overworld"]`。空数组表示不限制维度。 |
| `biomes` | string[] | 选填 | `[]` | 允许生成的生物群系 ID 列表，例如 `["minecraft:deep_dark"]`。空数组表示不限制生物群系。 |

#### `spawn`：生成行为

`spawn` 整个配置块可选，省略时使用默认生成参数。

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `delay_ticks` | int | 选填 | `20` | 右键点亮后，等待多少 tick 再尝试生成，最小 `0`。 |
| `require_player_nearby` | bool | 选填 | `true` | 生成时是否要求附近有存活玩家。 |
| `player_range` | double | 选填 | `9.0` | 附近玩家检测范围，最小 `1.0`。 |
| `allow_peaceful` | bool | 选填 | `false` | 是否允许在和平难度下生成。 |
| `count` | int | 选填 | `1` | 一次生成的实体数量，最小 `1`。 |
| `spawn_offset` | int[3] | 选填 | `[0, 0, 0]` | 实体生成位置相对重生笼方块的偏移。 |
| `nbt` | string | 选填 | `""` | 生成实体时额外写入的 SNBT，例如 `"{Health:100f,Glowing:1b}"`。如果非空但无法解析，整个条目会被跳过。 |
| `finalize_spawn` | bool | 选填 | `true` | 对 `Mob` 是否调用 `finalizeSpawn` 完成生成初始化。 |
| `max_attempts` | int | 选填 | `20` | 最大生成尝试次数。达到上限后会恢复未点亮并重置。 |
| `retry_interval_ticks` | int | 选填 | `4` | 生成失败后的重试间隔，最小 `1`。 |

#### `duplicate`：重复重生笼策略

`duplicate` 整个配置块可选。默认 `allow_multiple`，与灾变一致，**不做重复检查**。

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `mode` | string | 选填 | `allow_multiple` | `allow_multiple`：不检查附近已有笼，直接放置；`keep_existing`：附近已有真实重生笼时不再放置新笼；`replace_existing`：附近已有真实重生笼时先移除旧笼再放置新笼。 |
| `search_radius` | int | 选填 | `16` | 重复检查半径，最小 `0`。仅在 `mode` 不是 `allow_multiple` 时生效。 |

> 重复检测只会统计真实存在的 `boss_respawner` 方块，不会把残留的幽灵 BlockEntity 当作已有重生笼。

---

### 加载与校验规则

- 文件路径格式：
  ```
  data/<namespace>/boss_respawner/entries/<任意文件名>.json
  ```
- `schema_version` 必须为 `1`。
- `entity` 必须至少包含一个有效实体 ID。
- `entity` 数组中的所有实体都必须已注册，否则整条跳过。
- `activation.item` 必须指向已注册物品，否则整条跳过。
- `spawn.nbt` 非空时必须是合法 SNBT，否则整条跳过。
- `enabled = false` 的条目直接忽略。
- 多个数据包可以通过原版数据包优先级覆盖同名 JSON 文件。

## TOML 全局配置

生成于 `config/boss_respawner-common.toml`：

```toml
[general]
# 调试信息：在日志中打印 Boss 重生笼的调试信息。默认 false。
enableMod = true
logPlacement = false

[compat]
foreignCageBlockIds = ["cataclysm:boss_respawner"]
```

## 开发验证

```bat
gradlew.bat compileJava
gradlew.bat build
gradlew.bat runServer
gradlew.bat runClient
```

## 美术资源

本模组使用的重生笼贴图来自 Cataclysm，并已获得作者授权移植。模型与动画代码按 Vanilla `ModelPart` / `AnimationDefinition` 重写，不依赖 Lionfish API。
