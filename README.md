# Universal Boss Respawner（通用Boss重生笼）

将 灾变 模组的 Boss重生笼 机制做成一个独立模组，并且改造为数据驱动系统。

生物/Boss 死亡后，可按照 JSON 配置在指定锚点生成“重生笼”；玩家使用指定物品右键激活后，重生笼会重新召唤该生物。

本模组的目的，是优化多人游戏下的Boss战体验，使得每个玩家在找到Boss结构后，都有Boss可以打，不用担心Boss被挑战完了后，需要重新寻找。

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
- 有一层**不可绕过的保护方块底线**，任何模式下都不会覆盖灾变等模组的祭坛方块。
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

## JSON 配置

数据包目录：

```
data/<namespace>/boss_respawner/entries/<任意文件名>.json
```

- 监听目录会递归加载所有 `.json`。
- 执行 `/reload` 后生效；已放置的重生笼使用放置当时的规则快照，不会被 `/reload` 改变。
- **当前只支持 `schema_version: 2`**，`schema_version` 必须显式写出。
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
| 顶层 | `schema_version` | **必填** | 无（必须为 `2`） |
| 顶层 | `entity` | **必填** | 无 |
| 顶层 | `activation.item` | **必填** | 无 |
| 顶层 | `placement.mode` | **必填** | 无（`death` 或 `home`） |
| 顶层 | `placement.<mode>` 对应的配置块 | **必填** | 无 |
| 顶层 | `enabled` | 选填 | `true` |
| 顶层 | `priority` | 选填 | `0` |
| 激活 | `activation.amount` | 选填 | `1` |
| 激活 | `activation.consume` | 选填 | `true` |
| 死亡条件 | `death` 整个配置块 | 选填 | 使用默认值 |
| 生成 | `spawn` 整个配置块 | 选填 | 使用默认值 |
| 重复策略 | `duplicate` 整个配置块 | 选填 | 使用默认值 |
| 放置 | `placement.<mode>` 内的各字段 | 选填 | 见字段表 |

> 除上述必填项外，其他字段都可以省略并使用默认值。内置规则为了充当完整模板，把所有可选字段都显式写了出来。

---

### 最小 JSON 模板

只填写必须字段即可正常工作，其余全部使用默认值：

```json
{
  "schema_version": 2,
  "entity": "minecraft:warden",
  "activation": {
    "item": "minecraft:echo_shard"
  },
  "placement": {
    "mode": "home",
    "home": {}
  }
}
```

该模板等效于：

- 监守者首次被观察到时登记其当前位置为出生点；
- 死亡后在其出生点附近放置重生笼；
- 手持 1 个回响碎片右键激活并消耗；
- 点亮后延迟 20 tick 尝试生成 1 只监守者；
- 生成时需要附近有玩家；
- 搜索失败时强制放置；
- 同一 Boss 允许同时存在多个重生笼。

---

### 完整示例：监守者

```json
{
  "schema_version": 2,
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
      "force_place": false,
      "search_failure_fallback": true,
      "search_down": 32,
      "search_up": 16,
      "horizontal_radius": 4,
      "require_ground": true,
      "avoid_fluids": false,
      "avoid_blocks": ["cataclysm:boss_respawner"],
      "protected_blocks": []
    }
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

`death` 模式的结构完全相同，只需把 `mode` 换成 `"death"` 并把 `home` 换成 `death`：

```json
"placement": {
  "mode": "death",
  "death": {
    "offset": [0, 0, 0],
    "force_place": false,
    "search_failure_fallback": true,
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": false,
    "avoid_blocks": [],
    "protected_blocks": []
  }
}
```

---

### 字段详细说明

#### 顶层字段

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `schema_version` | int | **必填** | 无 | 必须为 `2`。缺失、非数字或其他数值都会跳过该条目。 |
| `enabled` | bool | 选填 | `true` | 设为 `false` 时忽略整个条目。 |
| `priority` | int | 选填 | `0` | 多个条目匹配同一个实体时使用，数字越大越优先；同优先级时按条目 ID 字典序取较小者。 |
| `entity` | string 或 string[] | **必填** | 无 | 目标实体 ID，例如 `"minecraft:warden"`；也可写成数组共用同一套规则。数组中的实体必须全部已注册，否则整个条目被跳过。 |

#### `activation`：激活条件

| 字段 | 类型 | 必填/选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `item` | string | **必填** | 无 | 激活重生笼所需物品 ID。物品必须已注册，否则条目被跳过。 |
| `amount` | int | 选填 | `1` | 每次右键激活消耗的物品数量，最小为 `1`。 |
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
| `force_place` | bool | `false` | 是否跳过搜索，直接在 `基准点 + offset` 放置 |
| `search_failure_fallback` | bool | `true` | 搜索失败后是否在该坐标强制放置 |
| `search_down` | int | `32` | 从基准点向下搜索的最大格数，最小 `0` |
| `search_up` | int | `16` | 从基准点向上搜索的最大格数，最小 `0` |
| `horizontal_radius` | int | `4` | 垂直搜索失败后水平搜索的半径，最小 `0` |
| `require_ground` | bool | `true` | 搜索时是否要求下方有可站立的完整方块 |
| `avoid_fluids` | bool | `false` | 搜索时是否避开流体 |
| `avoid_blocks` | string[] | `[]` | **搜索时偏好避开**的方块 ID，可被 `force_place` 绕过 |
| `protected_blocks` | string[] | `[]` | **任何情况下都不得覆盖**的方块 ID，`force_place` 也不能绕过 |

放置语义：

- `force_place: true`：目标区块已加载时直接放在该坐标，不执行搜索。
- `force_place: false`：以该坐标为搜索中心，顺序为**向下 → 向上 → 水平**。
- 搜索成功：放在搜索到的位置。
- 搜索失败且 `search_failure_fallback: true`：在该坐标强制放置。
- 搜索失败且 `search_failure_fallback: false`：不放置并输出警告。
- **`home` 模式搜索失败绝不回退到死亡位置。**
- 无 Home 记录：不放置、不使用死亡点，并输出含实体 ID、UUID、死亡维度与坐标的警告。

**强制放置的语义：** 它会忽略 `require_ground`、`avoid_fluids`、`avoid_blocks`，并且**允许替换目标位置上的普通方块**。它**不会**绕过 `protected_blocks`（见下）。由于重生笼方块在生存模式下不可破坏，误放的位置玩家无法自行清除，规则作者应据此谨慎设置 `offset`。

#### `avoid_blocks` 与 `protected_blocks`

这两个字段语义不同，**不可混用**：

| 字段 | 作用 | `force_place` 是否绕过 |
|---|---|---|
| `avoid_blocks` | 普通搜索时优先避开的方块 | 是 |
| `protected_blocks` | 任何情况下都不得覆盖的方块 | **否** |

此外模组自带一条全局保护底线，默认包含 `cataclysm:boss_respawner`。最终保护集合是**全局列表与当前规则列表的并集**：规则只能追加保护方块，不能移除全局保护方块。保护检查覆盖目标位置及其下方位置。

> 本模组永远不会覆盖受保护方块；命中时会保持原方块不变并输出警告。这就是从旧版全局 `foreignCageBlockIds` 拆出来的“安全底线”职责，而原来的“可配置避让”职责现在由每条规则的 `avoid_blocks` 承担。

#### `death`：死亡触发条件

| 字段 | 类型 | 选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `player_kill_only` | bool | 选填 | `false` | 设为 `true` 时只有玩家直接或间接造成的击杀才会生成重生笼 |
| `dimensions` | string[] | 选填 | `[]` | 允许生成的维度 ID 列表，空数组表示不限制 |
| `biomes` | string[] | 选填 | `[]` | 允许生成的生物群系 ID 列表，空数组表示不限制 |

#### `spawn`：生成行为

| 字段 | 类型 | 选填 | 默认值 | 说明 |
|---|---|---|---|---|
| `delay_ticks` | int | 选填 | `20` | 右键点亮后等待多少 tick 再尝试生成，最小 `0` |
| `require_player_nearby` | bool | 选填 | `true` | 生成时是否要求附近有存活玩家 |
| `player_range` | double | 选填 | `9.0` | 附近玩家检测范围，最小 `1.0` |
| `allow_peaceful` | bool | 选填 | `false` | 是否允许在和平难度下生成 |
| `count` | int | 选填 | `1` | 一次生成的实体数量，最小 `1`。`count > 1` 时同一批实体继承同一个 Home |
| `spawn_offset` | int[3] | 选填 | `[0, 0, 0]` | 实体生成位置相对重生笼方块的偏移 |
| `nbt` | string | 选填 | `""` | 生成实体时额外写入的 SNBT。非空但无法解析时整条跳过 |
| `finalize_spawn` | bool | 选填 | `true` | 对 `Mob` 是否调用 `finalizeSpawn` |
| `max_attempts` | int | 选填 | `20` | 最大尝试次数。`-1` 表示无限。达到上限后恢复未点亮并重置 |
| `retry_interval_ticks` | int | 选填 | `4` | 生成失败后的重试间隔，最小 `1` |

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
- `schema_version` 必须显式存在且等于 `2`。
- `placement.mode` 必须显式存在且为 `death` 或 `home`。
- 选中的模式对象（`placement.death` 或 `placement.home`）必须存在。
- `entity` 必须至少包含一个有效实体 ID，且数组中的所有实体都必须已注册。
- `activation.item` 必须指向已注册物品。
- `spawn.nbt` 非空时必须是合法 SNBT。
- `enabled = false` 的条目直接忽略。
- 以上任一条件不满足，整条规则被跳过并输出警告，不影响其他条目。
- 多个数据包可以通过原版数据包优先级覆盖同名 JSON 文件。

### 从 schema v1 迁移

Schema v1 已**不再被解析**（这是有意的破坏性变更）。如果你的数据包还是 v1，请按下表迁移：

| v1 | v2 |
|---|---|
| `schema_version: 1` | `schema_version: 2` |
| `placement.offset` | `placement.<mode>.offset` |
| `placement.search_down` | `placement.<mode>.search_down` |
| `placement.search_up` | `placement.<mode>.search_up` |
| `placement.horizontal_radius` | `placement.<mode>.horizontal_radius` |
| `placement.require_ground` | `placement.<mode>.require_ground` |
| `placement.avoid_fluids` | `placement.<mode>.avoid_fluids` |
| `placement.avoid_blocks` | `placement.<mode>.avoid_blocks` |
| *(无)* | `placement.mode` —— 新增必填，取 `"death"` 或 `"home"` |
| *(无)* | `placement.<mode>.force_place` —— 新增，默认 `false` |
| *(无)* | `placement.<mode>.search_failure_fallback` —— 新增，默认 `true` |
| *(无)* | `placement.<mode>.protected_blocks` —— 新增，默认 `[]` |
| 全局 `forcePlaceOnNoSafeSpot` | `placement.<mode>.search_failure_fallback`（按规则单独设置） |
| 全局 `foreignCageBlockIds` | `placement.<mode>.avoid_blocks`（避让）与新增的全局 `protectedBlockIds` / 规则级 `protected_blocks`（保护底线） |
| 全局 `debug.logPlacement` | 现为 `debug.debugInfo` |

最省事的迁移方式：把原来的扁平 `placement` 内容整体挪进 `placement.death`，然后补上 `"mode": "death"`，并显式写出三个新增字段：

```json
"placement": {
  "mode": "death",
  "death": {
    "offset": [0, 0, 0],
    "force_place": false,
    "search_failure_fallback": true,
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": false,
    "avoid_blocks": ["cataclysm:boss_respawner"],
    "protected_blocks": []
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

[compat]
# 本模组在任何情况下都不得覆盖这些方块，即使规则设了 force_place。
# 规则里的 protected_blocks 只能追加，不能移除这里的条目。
protectedBlockIds = ["cataclysm:boss_respawner"]

[debug]
# 调试信息：在日志中打印 Boss 重生笼的调试信息。默认 false。
debugInfo = false
```

放置与兼容策略现在全部由 JSON 规则控制；全局配置只保留总开关、客户端高亮与这条不可绕过的保护底线。

## 开发验证

```bat
gradlew.bat compileJava
gradlew.bat test
gradlew.bat build
gradlew.bat runServer
gradlew.bat runClient
```

`gradlew.bat test` 运行的是不启动游戏的纯逻辑单元测试，涵盖：

- schema v2 的解析与各类拒绝路径（含 v1 扁平结构、缺失 `mode`、缺失模式对象、非法模式）；
- `BossHomeSavedData` 的 NBT 往返、去重合并、区块索引与 `dataVersion` 拒绝策略；
- 全部 18 个内置 JSON 的真实解析验证与字段默认值检查；
- 对已删除全局配置项的源码引用检查。
