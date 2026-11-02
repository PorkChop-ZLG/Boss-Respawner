# Twilight Forest 专属模式：测试问题诊断与修复计划

**日期：** 2026-11-02
**状态：** 第二版 force-place 修复已实施（2026-11-03）；Naga 的“不取箱也能激活”robust 方案仍未实施
**前置设计：** [B 方案：固定锚点 + 死亡结算 + 笼子旁置](2026-11-02-twilightforest-placement-mode-design.md)
**适用代码：** 提交 `e92a92c`（暮色森林专属模式）+ 两轮修复

---

## 1. 背景

B 方案已实现：

- 暮色森林 Boss 死亡后等实体 `remove(KILLED)`（奖励箱已生成），再在 `HomePos + [1,0,0]` 放笼；
- 8 个主线 Boss 用 `spawn.transform` + `at: "anchor"` 把暮色自己的 `*_boss_spawner` 放到 `HomePos`；
- 谜题羊使用 `home` + `summon`（第一版修复后，不再是 `twilightforest` 模式）；
- 笼子不覆盖、不清空奖励箱。

实机测试发现 6 个问题。下面先给根因，再给修复方案和第一版实施结果。

---

## 2. 结论总表

| # | 现象 | 根因 | 修复层 |
|---|---|---|---|
| 1 | 娜迦偶现：笼子存在、右键无反应；清空上层箱子后可激活 | 娜迦奖励箱类型随机，第二种材质被 TF 放到 `HomePos`（锚点）；`canTransform` 拒绝非空箱子且无提示 | TF 随机箱子 + 本模组激活策略 |
| 2 | 巫妖每次复活后击败，奖励箱向上叠加 | `LichSpawnerBlockEntity` 下探后**无条件上移一格**，把 `HomePos` 每次 +1 | TF 的 Lich 刷怪笼 |
| 3 | 暮色恶魂笼子无法生成 | `HomePos`/箱子在空中，`require_ground: true` 拒绝悬空基准格；`search_failure_fallback: false` 不兜底 | JSON |
| 4 | 米诺菇笼子无法生成（房间地板多蘑菇） | 小蘑菇 `canBeReplaced()` 为 false，且不算完整地面；密集蘑菇挡掉候选/地面；fallback false | 本模组候选规则 + JSON |
| 5 | 谜题羊被当作暮色 Boss | 当前 JSON 是 `placement.mode: "twilightforest"`，应回到 `home` + `summon` | JSON |
| 6 | 暮色 Boss 笼子应允许悬空 | 8 条规则都是 `require_ground: true` | JSON |

---

## 3. 根因分析

### 3.1 问题 1：娜迦的锚点被第二个奖励箱占据

**TF 的箱子位置与落点规则**

- 箱子锚点：`EntityUtil.bossChestLocation(boss)` = `HomePos.below()`（`EntityUtil.java:54-56`）；
- 落箱：`IBossLootBuffer.tryDeposit`（`IBossLootBuffer.java:88-100`）：
  1. 如果该位置已经是**同类型**箱子 → 复用并覆盖 0–26 格；
  2. 如果该位置有**不同材质**的箱子（有 BlockEntity，不能替换）→ 从 `HomePos.below()` **向上逐格**找空位；
  3. `HomePos` 本身是空气 → 新箱子落在 `HomePos`。

**娜迦的箱子类型是随机的**（`Naga.java:538-540`）：

```java
return random.nextBoolean() ? TFBlocks.TWILIGHT_OAK_CHEST.get() : TFBlocks.CANOPY_CHEST.get();
```

于是会出现：

```text
        [ 第二种材质箱子 ]   ← HomePos（我们的锚点）
        [ 第一种材质箱子 ]   ← HomePos.below()
```

**我们的激活检查**

- 笼子在 `HomePos + [1,0,0]`（旁边），`BossRespawnerBlockEntity.canTransform`（约 `BossRespawnerBlockEntity.java:251-286`）检查锚点：
  - 锚点是空气/可替换 → 允许；
  - 锚点是**空**箱子 → 允许（激活时移除空箱子）；
  - 锚点是**非空**箱子 → 拒绝；
- 拒绝时只写日志；`BossRespawnerBlock.useItemOn` 仍然返回 `sidedSuccess`，**没有任何玩家可见提示**，所以表现为“右键毫无反应”。

清空上层箱子后，`Container.isEmpty()` 为 true，`canTransform` 允许，`finishTransform` 移除空的上层箱子并把刷怪笼放到锚点，因此恢复正常。与实测完全一致。

> 这不是 `require_ground` 或 `avoid_blocks` 的问题；娜迦的 `HomePos` 没有漂移。

### 3.2 问题 2：巫妖的 home 每次上移一格

`LichSpawnerBlockEntity.spawnMyBoss`（`LichSpawnerBlockEntity.java:31-51`）：

```java
BlockPos.MutableBlockPos mutableBlockPos = this.getBlockPos().mutable();
while (true) {
    if (minBuildHeight >= mutableBlockPos.getY()) break;
    if (accessor.getBlockState(mutableBlockPos.below()).isAir()) {
        mutableBlockPos.move(Direction.DOWN);   // 下探
    } else break;
}
mutableBlockPos.move(Direction.UP);             // ← 无条件上移
...
myCreature.setRestrictionPoint(GlobalPos.of(dimension, mutableBlockPos));
```

设计前提：刷怪笼放在**比目标位置高一格**的地方，先下探到最低空气格，再上移一格回到最低空气格。

我们的 `at: "anchor"` 把刷怪笼放在巫妖的 `HomePos`（最低空气格）：

- `HomePos` 下方是死亡奖励箱（不是空气）→ 不下探；
- 随后 `move(UP)` 把位置从 `HomePos` 移到 `HomePos + 1`；
- 新巫妖的 `HomePos = 原 HomePos + 1`。

每一轮：

```text
初始 home = H，箱子在 H-1
复活 → spawner 在 H；LichSpawner 上移 → 新 home = H+1
下一次死亡 → 箱子在 H
复活 → spawner 在 H+1；上移 → 新 home = H+2
下一次死亡 → 箱子在 H+1
...
```

箱子沿 `H-1、H、H+1、...` 向上叠加。**这是 LichSpawner 的确定性行为，不是随机箱子类型，也不是 `require_ground`。**

### 3.3 问题 3：暮色恶魂（Ur-Ghast）的悬空 home

`Ur-Ghast` 的刷怪笼在暗黑高塔顶部，`HomePos` 和奖励箱都在空中。当前规则：

```json
"offset": [1, 0, 0],
"require_ground": true,
"search_failure_fallback": false
```

笼子基准格 `HomePos + [1,0,0]` 的下方也是空气。`PlacementResolver.isLoadedAndValid`（`PlacementResolver.java:134-158`）：

```java
if (!state.isAir() && !state.canBeReplaced()) return false;
...
if (placement.requireGround() && !below.isFaceSturdy(level, pos.below(), Direction.UP)) {
    return false;
}
```

基准格以及搜索到的其他候选都因“没有完整地面”被拒绝；`search_failure_fallback: false` 又禁止兜底，所以完全不放置。

### 3.4 问题 4：米诺菇的蘑菇

小蘑菇是 `MushroomBlock extends BushBlock`（`MushroomBlock.java:23`），注册属性只有 `noCollission()`，**没有 `.replaceable()`**（`Blocks.java:1230-1243`）。因此：

- 蘑菇所在格：`state.canBeReplaced()` 为 false → 不能作为笼子候选；
- 蘑菇上方格：`below` 是蘑菇，`isFaceSturdy` 为 false → `require_ground` 拒绝；
- 房间地板被蘑菇密集覆盖时，候选位/地面同时被挡；
- `search_failure_fallback: false` → 搜索失败直接放弃。

所以不是“蘑菇被 avoid_blocks 避开”，而是**蘑菇既不是可替换方块，也不算完整地面**。

### 3.5 问题 5：谜题羊不是暮色 Boss

谜题羊是普通 `Animal`，不是 `BaseTFBoss`，没有奖励箱。改版前（`HEAD^`）的条目是：

```json
"placement": {
  "mode": "home",
  "home": {
    "offset": [0, 0, 0],
    "place_delay_ticks": 30,
    "force_place": false,
    "search_failure_fallback": true,
    "require_ground": true,
    ...
  }
},
"spawn": {
  "mode": "summon",
  ...
}
```

当前 `quest_ram.json` 是 `placement.mode: "twilightforest"`，让它走了 `HomePos`、等待实体移除、放在 `HomePos + [1,0,0]` 的专用流程，改变了原来的“首次观察 home + offset 0 + summon”行为。应只改 JSON。

### 3.6 问题 6：所有暮色 Boss 允许悬空

这是问题 3/4 的共同开关：8 条暮色规则都是 `require_ground: true`。

- `Ur-Ghast`：悬空 home → 放不出；
- 米诺菇：蘑菇/密集地面 → 放不出；
- 场地被破坏（地板被挖、爆炸、结构改动）→ 同样放不出；
- `require_ground: false` 后，`HomePos + [1,0,0]` 的空格可以直接通过候选校验；`RespawnCagePlacer` 对 `twilightforest` 模式的锚点/奖励箱格保护仍然生效（`chosen.equals(anchor) || chosen.equals(anchor.below())` 直接 ABANDON），不会因为允许悬空把笼子放到箱子上。

> 注意：`require_ground: false` 只解决“允许悬空”。如果基准格正好是一朵**不可替换**的蘑菇，搜索会退到蘑菇上方悬空，笼子能出现但不一定在基准格；要让笼子替换蘑菇落地，需要第 4 节的额外代码改动（把 `noCollission` 的植物类方块视为可替换）。

---

## 4. 修复方案

### 4.0 共同前提

- 保留 `search_failure_fallback: false`：TF 的 `HomePos.below()` 是奖励箱，任何强制/兜底写入都可能覆盖箱子；
- 保留 `RespawnCagePlacer` 的锚点/箱子格保护；
- 8 条暮色规则（8 个主线 Boss，不含谜题羊）统一 `require_ground: false`；
- 所有改动仍保持 `schema_version: 4`（加法字段，旧 v4 文件无需迁移）。

### 4.1 问题 3/4/6：允许悬空（JSON，首选）

将 8 条暮色规则的 `placement.twilightforest.require_ground` 改为 `false`：

| Boss | 现状 | 改为 |
|---|---|---|
| naga / lich / minoshroom / hydra / knight_phantom / ur_ghast / alpha_yeti / snow_queen | `require_ground: true` | `false` |

效果：

- Ur-Ghast：基准格空中的 `HomePos + [1,0,0]` 直接通过；
- 米诺菇：蘑菇上方的空格可以作为悬空候选；
- 场地被破坏后仍能生成笼子；
- 锚点/箱子格保护仍在，不会压住奖励箱。

**米诺菇的彻底落地（可选，问题 4 的增强）**：

如果希望笼子直接**替换蘑菇**落在基准格，而不是悬在蘑菇上方：

- 在 `PlacementResolver.isLoadedAndValid` 中，对 `twilightforest` 模式把 `state.getBlock() instanceof BushBlock`（含 `MushroomBlock`）也视为可替换；
- 候选位保留 `state.isAir() || state.canBeReplaced() || (twilightforest && state.getBlock() instanceof BushBlock)`；
- `level.setBlock` 会直接替换蘑菇（不掉落）；
- 这是代码改动，且只对 TF 模式生效；建议作为可选增强，先验证“允许悬空”是否已经满足米诺菇。

### 4.2 问题 5：谜题羊回到 `home` + `summon`（JSON）

把 `quest_ram.json` 恢复为 `HEAD^` 的旧条目：

- `placement.mode: "home"`；
- `placement.home`：`offset: [0,0,0]`、`place_delay_ticks: 30`、`force_place: false`、`search_failure_fallback: true`、`search_down: 32`、`search_up: 16`、`horizontal_radius: 4`、`require_ground: true`、`avoid_fluids: false`、`avoid_blocks`（18 项）；
- `spawn.mode: "summon"`，其余 summon 字段不变。

同步修改：

- `BuiltinEntriesTest` 里“9 条暮色条目都是 twilightforest 模式”的断言；
- `SchemaAdditionsTest.twilightForestRulesUseTwilightforestMode` 的 9 条循环（改为 8 条 TF + 1 条 home，或把 QuestRam 单独断言）；
- `docs/twilight-forest-mapping.md` 的“9 条全部 twilightforest”说明；
- README 的暮色段落。

> 谜题羊不再走 TF 的“等实体移除/HomePos”路径，`EntityHomeRegistrationHandler` 会像改版前一样登记它的首次观察 home。

### 4.3 问题 2：巫妖的“刷怪笼上移”

#### 方案 2A（首选，字段小）

新增 `spawn.transform.spawner_offset`（int[3]，默认 `[0,0,0]`），表示**刷怪笼相对锚点**的放置偏移（仅 `at: "anchor"` 时生效）：

- 巫妖：`placement.offset: [1,0,0]`（笼子在 home 东侧），`spawner_offset: [0,1,0]`（刷怪笼在 home 上方一格）；
- 激活：`target = homePos + spawner_offset`（home+1）；删掉笼子，刷怪笼放到 home+1；
- LichSpawner：从 home+1 下探到 home（下方是 home 空气？下方是 home；home 下方是奖励箱，不再下探），再上移一格回到 home；`HomePos` 保持 home；
- 下一次死亡：箱子仍在 home-1；笼子仍在 home 东侧；稳定。

需要同步：

- `TransformRule` 增加 `int[] spawnerOffset`；`SpawnRule.fromJson` 解析；
- `RespawnRuleSnapshot` 存 `SpawnerOffset`；旧快照默认 `[0,0,0]`；
- `BossRespawnerBlockEntity` 存 `spawnerOffset`；`setTransform` 参数；NBT `SpawnerOffset`；
- `canTransform`/`finishTransform` 的 `targetPos = homePos.offset(spawnerOffset)`；
- 锚点箱子检查（`canTransform`）仍然针对 `homePos`：如果 home 是**空**箱子，先移除；非空则拒绝。对巫妖而言，home-1 是箱子、home 通常是空气；这个检查不会误伤；
- JSON：8 条 transform 条目加 `"spawner_offset": [0,0,0]`，巫妖为 `[0,1,0]`；
- 测试：解析/快照/NBT/内置条目；`BuiltinEntriesTest` 的 transform 字段清单。

#### 方案 2B（备选，代码更少但生成位置略高）

不增加字段；保留 `at: "anchor"`（刷怪笼在 home），在巫妖生成后把它的 `HomePos` **重设回锚点**：

- 需要一个 `EntityJoinLevelEvent` 钩子，识别“由我们的刷怪笼生成的新巫妖”，把它的 `HomePos` 设回 home；
- 识别方式：在 `BossHomeSavedData` 记录一个待覆盖映射 `(dimension, spawnerPos) -> (dimension, anchor)`，在 `finishTransform` 放刷怪笼前写入；join 事件读取；
- 由于 LichSpawner 会把实体生成在 home+1，映射需要按 `spawnerPos` 或 `entity.HomePos.below()` 查找；
- 巫妖的实际生成位置会比 home 高一格，但 home 不再漂移；
- 需要 TF 的 `EnforcedHomePoint#setRestrictionPoint`（`compileOnly` 的可选 compat 类），或反射/NBT 注入；比 2A 复杂。

#### 方案 2C（统一修复 1+2 的 robust 方案，见 4.5）

把刷怪笼放在**笼子位置**（`at: "cage"`），再用 2B 的待覆盖映射把新 Boss 的 home 重设为锚点。这样：

- 刷怪笼不在锚点，锚点上的非空箱子不再是激活障碍（同时解决问题 1）；
- Lich 的 home 被覆盖回锚点，不再上移；
- Naga 的 home 也被覆盖回锚点，不再受 upper chest 影响；
- 代价：`EntityJoinLevelEvent` + 覆盖映射 + TF API/compat，改动最大。

### 4.4 问题 1：娜迦的锚点非空箱子

#### 方案 1A（首选，最小改动）

保留“锚点非空箱子 → 拒绝激活”的安全策略，但**给玩家可见提示**：

- `BossRespawnerBlockEntity.canTransform` 返回/记录一个明确原因（例如 `ANCHOR_OCCUPIED_BY_CHEST`）；
- `BossRespawnerBlock.useItemOn` 在 `activate` 返回 false 时，如果是这个原因，向玩家发送 `boss_respawner.message.anchor_occupied`（含坐标或“请先取走该奖励箱的物品”）；
- 增加 `zh_cn` / `en_us` 翻译键（`JadeTranslationsTest` 的语言键集一致性测试会覆盖）；
- 行为仍是“先取走上层箱子里的物品，再右键”，但不再是“毫无反应”。

优点：改动最小、不会删除任何战利品；缺点：不完全满足“不需要清空奖励箱”。

#### 方案 1B（robust，和 2C 合并）

把刷怪笼放到笼子位置、把新 Boss 的 home 重设为锚点：

- 锚点上的上层箱子保持原样（有物品也不影响激活）；
- 玩家可以先打 Boss，再取箱子；
- Naga 的 home 稳定；
- 需要 4.5 的映射/兼容层。

优点：真正满足“不需要清空/挖掉奖励箱”；缺点：代码量最大。

#### 方案 1C（不推荐）

把上层箱子移动到旁边的空格并保留内容：

- 需要复制 `Container` 内容和方块状态；
- 玩家可能困惑，且箱子位置变化；
- 不推荐。

### 4.5 Robust 方案（1B + 2C）：刷怪笼放在笼子，home 重设为锚点

如果要“不取走箱子也能激活”+“巫妖不漂移”，推荐统一方案：

1. `spawn.transform.at: "cage"`（或保留 `at: "anchor"` 但用 `spawner_offset` 指到笼子位置）；
2. `finishTransform` 放刷怪笼前，在 `BossHomeSavedData` 写入：
   - key：`(spawnerDimension, spawnerPos)`；
   - value：`(anchorDimension, anchorPos)` + 时间戳；
3. 监听 `EntityJoinLevelEvent`：
   - 只处理 TF Boss（按 `HomePos` 或实体类型识别）；
   - 按实体的 `HomePos`/`HomePos.below()`/生成位置找到映射；
   - 用 TF 的 `EnforcedHomePoint#setRestrictionPoint(GlobalPos)` 把 home 设为锚点（可选 `compileOnly` compat 类，TF 未加载时不加载）；
   - 删除映射；
4. 刷怪笼放在笼子位置，锚点上的箱子保持原样；Naga/Lich 的 home 都被重设为锚点；
5. 映射需要持久化/清理（未成功生成时不会永久残留）；
6. 这个方案与 `spawner_offset` 二选一：如果选 2C，Lich 不需要 `spawner_offset`。

**风险**：
- `EntityJoinLevelEvent` 时序（实体加入前/后）与 `HomePos` 的可写性；
- TF 可选 compat（`compileOnly`）或 NBT 注入的兼容性；
- 刷怪笼放在笼子位置时，Naga 生成在笼子/地面、Lich 生成在笼子上方一格；视觉上可能与“刷怪笼在 home”略有差异；
- 映射的持久化与清理。

### 4.6 方案选择建议

| 问题 | 最小方案 | Robust 方案 | 建议 |
|---|---|---|---|
| 1 娜迦 | 1A 提示 | 1B/2C | 先做 1A；若“不清箱”是硬需求再做 1B |
| 2 巫妖 | 2A `spawner_offset` | 2C | 先做 2A；它只影响巫妖，风险小 |
| 3 Ur-Ghast | 4.1 `require_ground:false` | 同 | 必做 |
| 4 米诺菇 | 4.1 悬空 | 4.1 + 植物可替换 | 先悬空；看实测是否够 |
| 5 谜题羊 | 4.2 JSON home+summon | 同 | 必做 |
| 6 全部悬空 | 4.1 | 同 | 必做 |

**推荐的第一版**：4.1（8 条 `require_ground:false`）+ 4.2（QuestRam home/summon）+ 2A（Lich `spawner_offset`）+ 1A（Naga 提示）。这一版不改变“锚点非空箱子拒绝”的安全策略，改动可控；如果实测仍不能接受娜迦“必须先取箱子”，再实施 1B/2C。

---

## 5. 字段与兼容性汇总

| 变更 | 类型 | 默认 | 兼容性 |
|---|---|---|---|
| `placement.twilightforest.require_ground: false` | JSON | 现有字段 | 仅改 8 条内置规则；自定义规则可自行选择 |
| `quest_ram.json` 回 `home` + `summon` | JSON | — | 旧 `home` 模式仍在；不影响其它条目 |
| `spawn.transform.spawner_offset` | 新可选字段 | `[0,0,0]` | 加法；旧规则/旧快照默认零；`schema_version` 仍为 4 |
| `spawn.transform.at` | 已有 | `"cage"` | 2A 仍用 `"anchor"`；2C 用 `"cage"` |
| `BossHomeSavedData` 待覆盖映射（仅 2C） | 新持久化记录 | 空 | 加法；需要清理策略 |
| TF `EnforcedHomePoint` 兼容层（仅 2B/2C） | 可选 `compileOnly` | — | TF 未加载时不加载；主代码不得引用 TF 类 |
| 娜迦提示翻译键（仅 1A） | 新 lang 键 | — | `zh_cn`/`en_us` 同时添加 |

> `schema_version` 不升：以上均为加法字段/内置 JSON 调整；旧 v4 文件仍可解析。

---

## 6. 测试与验收

### 单元测试

- 解析：`spawn.transform.spawner_offset`（默认、显式、非法长度）；`quest_ram` 的 `home` 模式；
- 快照：`spawner_offset` 的 NBT 往返；旧快照默认零；
- 内置条目：8 条 TF `require_ground:false`、Lich 的 `spawner_offset:[0,1,0]`、QuestRam `home`；
- 箱子/Boss 生成：`BossRespawnerBlockEntity` 的 target 计算（纯函数化或源码级）；
- 源码：TF 未加载时无 TF 类引用（现有 `JadeOptionalityTest` 模式可扩展）；
- 现有 106 项测试保持通过。

### 实机验收

| 编号 | 场景 | 预期 |
|---|---|---|
| N1 | 娜迦连续 5–10 轮，确保两种箱子材质都出现 | 若无上层箱：正常；若有上层非空箱：1A 给出提示，清空后可激活；1B 则直接可激活 |
| L1 | 巫妖连续 5–10 轮 | 箱子始终在 `HomePos.below()`，不向上叠加；笼子位置稳定 |
| U1 | 暮色恶魂 | 笼子悬空出现在箱子旁；激活后刷怪笼正常生成 Boss |
| M1 | 米诺菇 | 笼子出现（悬空或替换蘑菇）；激活后正常生成 |
| Q1 | 谜题羊 | 回到首次观察 home + summon 的行为 |
| T1 | 任意 TF Boss，先把场地地板挖掉/炸掉 | 笼子仍能出现（悬空） |
| K1 | 幻影骑士 | 仍只出 1 个笼，激活后 6 只 |
| R1 | 无 TF 环境 | 启动无 `NoClassDefFoundError`（1B/2C 的 compat 类不得被常载代码引用） |
| P1 | 和平难度 | 仍拒绝激活、不扣钥匙 |

---

## 7. 风险与开放问题

1. **娜迦的“必须先取上层箱子”**是否是硬需求？如果是，必须做 1B/2C；如果只要求“不要毫无反应”，1A 足够。
2. **巫妖的 2A 与 2C**：2A 改动小、风险低，但刷怪笼在 home 上方一格；2C 统一修复 1+2，但需要 join 事件/映射/TF compat。
3. **米诺菇**：`require_ground:false` 先验证悬空是否满足；若要笼子替换蘑菇落地，再加植物可替换。
4. **QuestRam 的测试/文档**：`BuiltinEntriesTest`/`SchemaAdditionsTest`/README/mapping 需要同步，否则旧断言会失败。
5. **TF compat 的加载安全**：2B/2C 若用 `EnforcedHomePoint`，必须像 Jade 一样隔离，TF 未加载时不加载，且主代码不引用 TF 类。
6. **`search_failure_fallback: false`**：保留；所有“悬空/蘑菇”修复都通过放宽候选，而不是强制覆盖奖励箱。
7. **箱子类型随机**：TF 的 `getDeathContainer` 无法控制；1A 的提示和 1B 的 home 覆盖是两种应对方式。

---

## 8. 推荐实施顺序

1. **JSON-only**：8 条 `require_ground:false`；QuestRam `home`+`summon`；同步测试/文档；
2. **Lich 2A**：`spawner_offset` 字段 + 解析/快照/NBT + Lich JSON `[0,1,0]` + 测试；
3. **Naga 1A**：激活失败提示 + lang 键 + 测试；
4. **实机回归**：按第 6 节验收；
5. **可选**：如果 Naga 仍不可接受，实施 1B/2C；如果 Minoshroom 悬空仍不够，加植物可替换。

---

## 9. 参考

- TF 源码：
  - `twilightforest/entity/boss/Naga.java:537-540`（随机箱子）
  - `twilightforest/entity/boss/Lich.java:909-912`（随机箱子）
  - `twilightforest/block/entity/spawner/LichSpawnerBlockEntity.java:31-51`（下探 + 上移）
  - `twilightforest/entity/boss/IBossLootBuffer.java:88-100`（同类型复用/向上搜索）
  - `twilightforest/util/entities/EntityUtil.java:54-56`（箱子 = home.below）
  - `net/minecraft/world/level/block/MushroomBlock.java:23`、`Blocks.java:1230-1243`（蘑菇不可替换）
- 本模组：
  - `blockentity/BossRespawnerBlockEntity.java`（`canTransform`/`finishTransform`）
  - `block/BossRespawnerBlock.java`（`useItemOn` 无提示）
  - `placement/PlacementResolver.java:134-158`（air/replaceable + require_ground）
  - `event/TwilightSettlementService.java`（TF 模式）
  - `src/main/resources/data/twilightforest/boss_respawner/entries/*.json`

---

## 10. 实施结果（第一版）

按第 4.6 节推荐的“最小方案”实施：

| 问题 | 已实施 | 说明 |
|---|---|---|
| 3/4/6 悬空 | 8 条暮色规则 `require_ground: false` | Ur-Ghast、米诺菇、场地被破坏后的悬空候选可通过；锚点/奖励箱格保护仍保留 |
| 5 谜题羊 | `quest_ram.json` 改回 `home` + `summon` | 使用 `HEAD^` 的旧模板：`offset [0,0,0]`、`place_delay_ticks 30`、`search_failure_fallback true`、`require_ground true`、`avoid_blocks` 18 项 |
| 2 巫妖 | 新增 `spawn.transform.spawner_offset`，巫妖为 `[0,1,0]` | 刷怪笼放到 `HomePos + [0,1,0]`；LichSpawner 下探回 `HomePos`；`canTransform`/`finishTransform` 支持锚点+偏移，空箱子会先移除 |
| 1 娜迦 | 激活失败提示 | `BossRespawnerBlockEntity.TransformFailure` + `anchor_occupied` / `target_occupied` 两条 lang 键；上层非空箱子时给出提示，仍保留“先清空再激活”的安全策略 |

**未实施（保留为后续选项）**：

- Naga 的 1B/2C：刷怪笼放到笼子位置 + `EntityJoinLevelEvent` 把新 Boss 的 home 重设为锚点，从而“不取箱也能激活”；
- 1C 移动箱子（不推荐）。

> 第二轮修复（第 11 节）已用 force-place + home 重设取代了这里原本的“Minoshroom 植物可替换”和“Lich 2B/2C”待办。

**验证**：

```text
gradlew.bat test   → 106 tests, 0 failures
gradlew.bat build  → BUILD SUCCESSFUL
```

已同步更新 README、`docs/twilight-forest-mapping.md`、[B 方案设计](2026-11-02-twilightforest-placement-mode-design.md) 的后续链接，以及相关测试：`SchemaAdditionsTest`（8 条 TF + QuestRam home、`require_ground:false`、Lich `spawner_offset`）、`BuiltinEntriesTest`（QuestRam 的 home 模式、`spawner_offset` 字段）、`BossHomeSavedDataTest`（`spawnerOffset` 快照）、`BossRespawnerBlockEntity` 的失败枚举/提示。

**仍需实机确认**：

- 娜迦两种箱子材质各刷多轮：有上层非空箱时是否出现提示、清空后是否可激活；
- 巫妖多轮：箱子是否始终在 `HomePos.below()`，不再上叠；
- 暮色恶魂/米诺菇/被破坏场地：笼子是否能悬空生成；
- 谜题羊：是否回到旧的 home + summon 行为；
- 幻影骑士、和平难度、无 TF 环境回归。

---

## 11. 第二轮修复（2026-11-03）：force-place、统一偏移、巫妖/米诺菇替换奖励箱

第一版实机测试后，用户要求：

1. 所有暮色主线 Boss 的 `placement.offset` 统一为 `[0,-1,-1]`（与奖励箱同一水平面）；
2. `placement.force_place` 统一为 `true`；
3. 巫妖仍然上叠：改为允许 `spawn.transform` **强制放置**，让巫妖/米诺菇的刷怪笼直接占据奖励箱格、替换奖励箱。

### 11.1 已实施

| 项 | 内容 |
|---|---|
| 统一偏移 | 8 条主线 `placement.twilightforest.offset = [0,-1,-1]`（Naga/Lich 由用户手改，其余 6 条由脚本统一） |
| 笼子 force | 8 条主线 `placement.twilightforest.force_place = true`；`RespawnCagePlacer` 直接使用 `base = HomePos + [0,-1,-1]`，不搜索；锚点/奖励箱格硬守卫仍生效 |
| 巫妖/米诺菇 force | `spawn.transform.force_place = true`；`spawner_offset = [0,-1,0]`（落在 `HomePos.below()` 奖励箱格）；`canTransform` 允许替换**非空 ChestBlock**，`finishTransform` 删除箱子并放入刷怪笼（箱内物品按设计丢失） |
| home 重设 | 新 `PendingHomeOverride` + `BossHomeSavedData.pendingHomeOverrides` + `TwilightHomeOverrideHandler`（`EntityJoinLevelEvent` + 反射调用 TF `EnforcedHomePoint#setRestrictionPoint`）把新 Boss 的 home 重设回 `HomePos`。巫妖的 LichSpawner 会下探/上移，米诺菇的 base spawner 会以箱子格为 home；没有这一步，下一轮会继续漂移 |
| 旧笼迁移 | 无 `ForcePlace` 标签的旧巫妖/米诺菇笼在 `loadAdditional` 中迁移为 `force=true`、`spawner_offset=[0,-1,0]`；旧世界不必重新打 Boss 才能应用修复 |
| 其余 6 条 | `spawner_offset=[0,0,0]`、`spawn.transform.force_place=false` |
| Naga 提示 | 1A 的 `anchor_occupied` / `target_occupied` 提示保留；force 规则不会触发它 |

### 11.2 字段/持久化

- `spawn.transform.force_place`（bool，默认 false）：只允许替换目标格/锚点格上的 `ChestBlock`，其他方块仍拒绝；
- `TransformRule` 增加 `forcePlace`；`RespawnRuleSnapshot`、`BossRespawnerBlockEntity` 的 `ForcePlace` NBT、`RespawnCagePlacer` 均已接通；
- `PendingHomeOverride`（spawner 坐标 + anchor 坐标 + createdAt，TTL 1 小时）存入 `BossHomeSavedData` 的 `pendingHomeOverrides`；`EntityJoinLevelEvent` 读取并反射设置 home；
- 旧数据没有 `pendingHomeOverrides` 时默认空列表；`BossHomeSavedData.DATA_VERSION` 仍为 1（加法字段）。

### 11.3 验证

```text
gradlew.bat test   → 107 tests, 0 failures
gradlew.bat build  → BUILD SUCCESSFUL
```

新增/更新测试：`spawner_offset` + `force_place` 解析/快照、8 条 `offset/placement.force_place`、巫妖/米诺菇的 `spawner_offset/force_place`、`pendingHomeOverrides` 的保存/加载/去重/过期、`BuiltinEntriesTest` 的 TF 默认值。

### 11.4 仍需实机确认

- 巫妖多轮：刷怪笼是否每次都在奖励箱格、盒子是否不再上叠、Boss 的 home 是否保持；
- 米诺菇多轮：刷怪笼是否替换奖励箱、Boss 是否在箱子格生成、home 是否保持（不会向下漂移）；
- 其他 6 条：笼子是否在 `HomePos + [0,-1,-1]`、刷怪笼是否仍在 `HomePos`；
- 旧世界旧巫妖/米诺菇笼是否被迁移；
- 奖励箱内容确实被替换（这是新设计的预期代价，不是 bug）。

