# 通用 Boss 重生笼：Home 出生点模式设计

**日期：** 2026-10-16  
**状态：** 已确认，待实现（2026-10-16 审查修订）  
**适用版本：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21  
**Mod ID：** `boss_respawner`

## 1. 背景与目标

本模组的灵感来自 Cataclysm 的 Boss 重生笼，但目标不是重复实现 Cataclysm 已经提供的祭坛召唤机制。

本模组主要服务于以下实体：

- 随结构生成；
- 被击杀后不会自然再次出现；
- 没有现成的重复召唤方式；
- 需要通过本模组提供一个可再生的 Boss 挑战入口。

当前版本的 JSON 规则只支持在 Boss 死亡位置附近生成重生笼。本次设计新增 `home` 模式，使重生笼可以围绕 Boss 的登记出生点生成。

核心目标：

1. 不为每个第三方模组编写注入或硬编码兼容代码。
2. 不修改第三方实体的 NBT，降低与其他模组实体存档逻辑冲突的风险。
3. 通过 NeoForge 通用事件观察 Boss 加入世界的时机。
4. 通过存档级数据保存 UUID 到出生点的映射。
5. 通过 JSON 选择死亡点模式或 Home 模式。
6. 内置规则默认显式使用 Home 模式；在存在有效 Home 记录且目标位置最终可处理的前提下，搜索失败后仍会尝试强制放置或持久化延迟放置。

## 2. 非目标

本次不实现以下功能：

- 不读取或解析第三方实体的 `HomePos`、`getHomePos()`、`getRestrictCenter()` 等字段或方法。
- 不给第三方实体写入本模组自定义出生点 NBT。
- 不为 Cataclysm、Legendary Monsters、Mowzie’s Mobs 等模组编写专用 Java 分支。
- 不在 Home 位置搜索失败后回退到 Boss 死亡位置。
- 不通过结构扫描反推 Boss 的历史出生点。
- 不保证在模组安装前已经死亡、且没有任何记录的 Boss 能被还原到真实历史出生点。

## 3. 核心概念

### 3.1 Home 位置

Home 位置是本模组数据库登记的出生锚点，不等同于第三方模组的 AI 老巢或 Cataclysm 的 `HomePos`。

每个被 JSON 规则选中的实体，使用自己的 UUID 作为数据库键。记录至少包含：

```text
entity_uuid
entity_type
dimension
home_x
home_y
home_z
```

数据库保存原始登记位置，不保存加过 JSON 偏移量的位置。

### 3.2 首次登记

使用 NeoForge `EntityJoinLevelEvent` 作为通用观察入口。

服务端收到实体加入世界事件后：

1. 判断实体是否有当前有效的 `home` 模式规则。
2. 判断实体 UUID 是否已存在于数据库。
3. 不存在时，记录事件发生时的实体类型、维度和方块坐标。
4. 已存在时，不覆盖原始 Home 位置。

`loadedFromDisk()` 不能用来跳过登记。旧存档中的 Boss 第一次被加载时，也需要按“首次观察位置”登记。

这个位置对于旧存档实体可能不是历史真实出生点，但它是可解释、稳定且不修改第三方实体的兼容策略。

### 3.3 重生后的继承

重生笼召唤新实体后，新实体拥有新的 UUID。新 UUID 必须继承重生笼对应的原始 Home 位置，而不是登记为重生笼位置或实际生成位置。

坐标关系：

```text
原始 Home 位置 A
    ↓ placement.home.offset
重生笼位置 B
    ↓ spawn.summon.spawn_offset（v2 及以前为 spawn.spawn_offset）
实体实际生成位置 C
```

下一次死亡仍然使用 A，避免因放置偏移、安全搜索或生成偏移导致位置逐轮漂移。

## 4. JSON Schema v3

> **状态：已升到 v3。** 本节最初写于 schema v2 时期。当前实现为 **schema v3**：`schema_version` 必须写 `3`，
> `spawn` 与 `placement` 一样改为「先选模式、再放子对象」（`spawn.mode` + `spawn.summon` / `spawn.transform`），
> `placement.<mode>` 新增 `place_delay_ticks`（默认 `0`）。v1 与 v2 的布局都会被明确拒绝，不再解析。
> 下面 placement 部分的嵌套结构与字段名与 v3 完全一致，只需把版本号读作 `3`。

项目所有者已确认，本模组当前开发阶段可以接受破坏性的 Schema 变更：**v1 → v2 → v3** 均已发生过，旧版本规则不再解析。新的规则使用 `schema_version: 3`。

### 4.1 顶层结构

以下字段必须显式存在：

- `schema_version`；
- `entity`；
- `activation.item`；
- `placement.mode`；
- 与 `placement.mode` 对应的配置块：`placement.death` 或 `placement.home`；
- `spawn.mode`；
- 与 `spawn.mode` 对应的配置块：`spawn.summon` 或 `spawn.transform`。

除上述字段外，字段均可省略并使用代码默认值。非法字段类型、非法模式或缺少选中模式对象时，整条规则跳过并输出 `warn`。

`placement.mode` 不再有默认值。默认使用 Home 的含义体现在所有内置规则和模组提供的模板都显式使用：

```json
"mode": "home"
```

### 4.2 最小有效示例

这是允许省略可选字段的最小 Home 规则：

```json
{
  "schema_version": 3,
  "entity": "example:boss",
  "activation": {
    "item": "example:key"
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

### 4.3 placement 结构

`placement` 只包含模式选择，以及按模式嵌套的配置对象。

```json
"placement": {
  "mode": "home",
  "home": {
    "offset": [0, -1, 0],
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

Death 示例：

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

允许的模式：

```text
death
home
```

解析规则：

- `mode` 缺失时跳过整条规则并输出 `warn`；
- `mode: death` 时必须存在 `placement.death`；
- `mode: home` 时必须存在 `placement.home`；
- 非法模式、缺失对应对象或字段类型错误时跳过整条规则并输出 `warn`。

### 4.4 两个模式对象共用的字段

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `offset` | int[3] | `[0,0,0]` | 相对模式基准点的世界坐标偏移 |
| `force_place` | boolean | `false` | 是否跳过搜索，直接在固定坐标放置 |
| `search_failure_fallback` | boolean | `true` | 搜索失败后是否执行强制放置 |
| `search_down` | int | `32` | 向下搜索距离 |
| `search_up` | int | `16` | 向上搜索距离 |
| `horizontal_radius` | int | `4` | 水平搜索半径 |
| `require_ground` | boolean | `true` | 搜索时是否要求下方有坚实地面 |
| `avoid_fluids` | boolean | `false` | 搜索时是否避开流体 |
| `avoid_blocks` | string[] | `[]` | 搜索时偏好避开的方块 ID，可被强制放置绕过 |
| `protected_blocks` | string[] | `[]` | 任何情况下都不得覆盖的方块 ID |

`search_failure_fallback` 的默认值必须是 `true`，因为本模组的默认目标是尽量确保重生笼生成。但目标维度不可用或目标区块未加载时不能立即执行强制放置，必须按 §5.3 处理（前者立即失败，后者进入持久化待放置队列）。

### 4.5 内置 JSON 的模板要求

一般第三方数据包可以省略可选字段；但是本模组 `src/main/resources/data/**/boss_respawner/entries/*.json` 内的所有规则必须把可选字段全部显式写出，作为其他作者的完整模板。

内置规则必须显式包含 `enabled`、`priority`、激活规则可选字段、选中模式对象的全部字段、`death`、`spawn` 和 `duplicate` 的全部可选字段。

## 5. 放置语义

### 5.1 death 模式

基准点是死亡实体的 `blockPosition()`。

```text
origin = death_position
base = origin + placement.death.offset
```

- `force_place: true`：目标区块已加载时直接放置在 `base`。
- `force_place: false`：以 `base` 为搜索中心。
- 搜索成功：放置在搜索到的位置。
- 搜索失败且 `search_failure_fallback: true`：目标区块已加载时直接放置在 `base`。
- 搜索失败且 `search_failure_fallback: false`：不放置并输出 `warn`。
- 目标维度不可用：按 §5.3.1 立即失败，不进入队列。
- 目标区块未加载：不执行同步写入，进入持久化待放置队列。

### 5.2 home 模式

基准点是数据库中该实体 UUID 对应的 Home 位置，且维度必须使用数据库记录的维度。

```text
origin = saved_home_position
base = origin + placement.home.offset
```

- `force_place: true`：目标区块已加载时直接放置在 `base`。
- `force_place: false`：以 `base` 为搜索中心。
- 搜索成功：放置在搜索到的位置。
- 搜索失败且 `search_failure_fallback: true`：目标区块已加载时直接放置在 `base`。
- 搜索失败且 `search_failure_fallback: false`：不放置并输出 `warn`。
- 目标维度不可用：按 §5.3.1 立即失败，不进入队列。
- 目标区块未加载：不执行同步写入，进入持久化待放置队列。
- 无 Home 记录：不放置、不使用死亡点，并输出包含实体 ID、UUID、死亡维度、死亡坐标和补救建议的 `warn`。

Home 模式始终围绕 Home 位置搜索，绝不切换为死亡位置搜索。

### 5.3 未加载区块：统一永久 defer

未加载区块不通过 JSON 配置。无论是 `death` 还是 `home`，也无论 `force_place` 或 `search_failure_fallback` 的值如何，都使用同一套规则。

#### 5.3.1 目标维度不可用与目标区块未加载必须分开

这两者的失败语义完全不同，**不得合并判断**：

| 情况 | 判定方式 | 处理 |
|---|---|---|
| 目标维度不存在或不可用 | `server.getLevel(targetDimension) == null` | **立即失败**，输出 `error`；**不创建待放置任务**；删除该实体的 Home 记录 |
| 维度可用、目标区块未加载 | `level.isLoaded(base)` 为 `false` | 进入持久化 defer 队列 |

`MinecraftServer.getLevel(ResourceKey<Level>)` 是 `@Nullable`：当某个模组维度被移除后，指向它的 Home 记录将永远取不到 level。若把这种情况也送进「永久、不过期」的队列，任务会**永远无法完成、又永不释放**，同时 §6.1 还会持续保留其 Home 记录，形成永久泄漏。因此维度不可用必须走立即失败路径。

#### 5.3.2 defer 流程

1. 按 5.3.1 判定目标维度可用；
2. 维度可用但目标区块未加载时，不得调用会同步加载区块的写入接口；
3. 创建或更新持久化待放置任务；
4. 目标区块之后真正加载时，再执行该任务；
5. 任务在服务器重启后仍然存在，不设置自动过期时间。

“永久”表示任务不会因为一次未加载检查而丢弃；它会一直保留到目标可以处理并成功放置，或因维度不可用、保护方块、重复策略等明确的终止条件被处理。不得通过改用死亡位置来规避队列。

待放置任务必须保存规则快照和目标信息，不能只保存实体 UUID 后在未来重新读取可能已经变化的 JSON。

#### 5.3.3 去重键

任务按**放置目标**去重，而不是按来源实体去重：

```text
dedupKey = (targetDimension, targetBase)
```

理由：

- 若按 `sourceEntityUuid` 去重，等于没有去重——每次死亡都是新实体、新 UUID，任务数将随历史死亡总数**无界增长**；
- 按放置目标去重后，一个目标只有一条任务，任务总数由「世界上出现过的放置目标数」决定，是**有界**的。这正是「不设置自动过期时间」能够成立的前提。

`sourceEntityUuid` 降级为「最近一次触发该任务的死亡」的信息字段，仅用于日志与排查。

任务因去重而被合并时，必须同时清理**被合并那次死亡**的 Home 记录：该记录对已死亡实体已无用途，而任务自带的 `homeSnapshot` 已保存所需信息。规则快照以最近一次死亡为准。

#### 5.3.4 执行 defer 任务的语义

「执行该任务」指**用任务中保存的规则快照重跑一遍完整的放置流程**（搜索 → 按 `search_failure_fallback` 决定是否强制放置），等价于该死亡事件发生在区块已加载时应走的那条路径。

- 目标区块此时已加载，只是满足了前置条件，**不改变** `force_place` / `search_failure_fallback` 的语义；
- 因此 `search_failure_fallback: false` 的规则在 defer 之后**仍然不会**强制放置，不得因为「已经等了很久」而自行降级为强制放置；
- 放置成功后的处理（写入方块、保存 Home 快照、删除来源 UUID 记录）与同步路径完全一致。

#### 5.3.5 触发时机与线程约束

`ChunkEvent.Load` 只能作为**触发信号**，不能在其中直接执行放置。

该事件的 javadoc 明确警告：*"This event may be called before the underlying `LevelChunk` is promoted to `ChunkStatus#FULL`. You will cause chunk loading deadlocks if you don't delay your level interactions."* —— 与 §7.1 的 `EntityJoinLevelEvent` 属于同一类风险。

因此：

- `ChunkEvent.Load` 回调中只允许更新「待执行」标记或加入待执行集合，**不得调用任何世界写入接口**；
- 危险的不只是「远处区块」。`duplicate.mode: replace_existing` 会 `destroyBlock` 相邻位置的重生笼，方块更新会触发邻居通知，而 `ChunkEvent.Load` 触发的那个区块本身正在加载、**相邻区块可能未加载**。在这些回调里发起世界写入，等于在区块加载流程内部再发起一次同步区块加载，会导致死锁；
- 实际的放置动作必须延后到当前区块加载流程**之外的后续 tick** 执行（例如 `server.execute(...)`，或在 `ServerTickEvent` 中消费待执行列表）；
- `EntityJoinLevelEvent` 回调中同样只做轻量登记，不得执行放置；
- 必须对同一任务去重，并对日志限频，避免同一任务在多次区块加载中被反复记录。

### 5.4 强制放置

强制放置路径的目标是“无论搜索安全条件如何，都使用指定坐标生成重生笼”。它必须：

- 不寻找地面；
- 允许浮空；
- 忽略 `require_ground`；
- 忽略 `avoid_fluids`；
- 忽略 `avoid_blocks`；
- 目标区块已加载时直接在 `base` 设置重生笼方块；
- 允许替换目标位置上的普通方块，但不能覆盖保护方块。

`duplicate.mode` 仍然保留。强制放置只绕过位置安全检查，不自动绕过重复重生笼策略。

如果目标维度不可用、坐标超出世界边界、命中保护方块或底层世界操作失败，Java 代码仍应记录错误或警告。此类底层失败不能通过换到死亡位置来掩盖。

其中**目标维度不可用**必须按 §5.3.1 走立即失败路径，不得进入 defer 队列，否则会产生永远无法完成、又永不过期的任务。

### 5.5 avoid_blocks 与 protected_blocks

两个字段的语义必须分开：

| 字段 | 作用 | 强制放置是否绕过 |
|---|---|---|
| `avoid_blocks` | 普通搜索时优先避开的方块 | 是 |
| `protected_blocks` | 任何情况下都不得覆盖的方块 | 否 |

本模组保留一个全局保护底线 `Config.PROTECTED_BLOCK_IDS`，默认至少包含：

```text
cataclysm:boss_respawner
```

每条 JSON 的 `protected_blocks` 只能追加保护方块，不能移除全局保护方块。最终保护集合为全局列表与当前规则列表的并集。

检查目标方块时，至少检查目标位置及其下方位置，保持当前模组避让逻辑的范围。

删除旧的全局 `Config.FOREIGN_CAGE_BLOCK_IDS`。它原本承担的是可配置避让，不应继续混用保护底线语义。

### 5.6 duplicate 与同一 Home 冲突

`duplicate.mode` 仍然保留并优先控制已有重生笼的重复策略。

- 普通搜索时，已有本模组重生笼占据的方块本身不可作为普通候选位置，搜索会继续寻找其他位置。
- `force_place: true` 时，固定坐标语义优先；如果 `duplicate.mode` 为 `allow_multiple`，相同坐标最终只能保留一个方块实体，后放置者可能覆盖先放置者，这是固定坐标强制放置的已接受限制。
- `keep_existing` 和 `replace_existing` 仍按现有规则执行，不因 Home 模式改变。

## 6. SavedData 设计

新增一个服务端共享的 `SavedData`，建议挂在 Overworld 的 `DimensionDataStorage` 上，作为跨维度共享数据库。

建议类名：

```text
com.zonlong.bossrespawner.data.BossHomeSavedData
```

建议存档文件名：

```text
boss_respawner_homes.dat
```

#### 6.0 存档落点与命名约束

由于挂在 **Overworld** 的 `DimensionDataStorage` 上，文件的完整落点是存档根目录下的 `data/`：

```text
saves/<存档名>/data/boss_respawner_homes.dat
```

路径的推导链（Minecraft 1.21.1）：

1. `ServerChunkCache` 用 `<维度目录>/data` 构造每个维度的 `DimensionDataStorage`；
2. `DimensionType.getStorageFolder` 对**主世界直接返回存档根目录**（下界为 `DIM-1`、末地为 `DIM1`、其他维度才是 `dimensions/<命名空间>/<路径>`）；
3. `DimensionDataStorage.getDataFile` 用 `new File(dataFolder, name + ".dat")` 拼接，**不会再建子目录**。

可直接对照同一目录下已有的原版与模组文件，例如 `raids.dat`、`random_sequences.dat`，以及同样采用「主世界级 SavedData」模式的第三方模组文件（如 `legendary_monsters_worlddata.dat`）。

**命名约束：`name` 中不得包含 `/` 或 `\`。**

`new File(parent, child)` 会把 child 里的分隔符当成子路径，但 `DimensionDataStorage` **不会创建该子目录**；而 `SavedData.save` 对失败只做：

```java
try {
    NbtIo.writeCompressed(compoundtag, file.toPath());
} catch (IOException ioexception) {
    LOGGER.error("Could not save data {}", this, ioexception);
}
```

即只打一行 `error` 日志。结果是**每次保存都静默失败、数据永不落盘，而游戏表面上一切正常**，极难排查。因此本模组必须使用平铺的单段文件名，不要试图放进 `data/boss_respawner/` 之类的子目录。

对备份与迁移的影响：

- **一个文件承载所有维度**：下界、末地、模组维度的 Home 位置与待放置任务都在这一个文件里（这正是选择主世界存储的目的）。备份 `saves/<存档名>/data/` 即可，不需要另外处理 `DIM-1/data/` 或其他维度目录。
- **随存档走**：换存档即换数据，删存档则数据一并消失。这也保证了 UUID → Home 的映射不会跨存档串味。
- **删除该文件的后果**：全部 Home 记录**和全部待放置任务**一并丢失。已登记的 Boss 会在下次区块加载时按「首次观察」重新登记（位置为当时所在处，可能不是原出生点），但 defer 任务**无法恢复**。

建议 Java 数据结构：

```text
Map<UUID, HomeRecord>
Map<TaskDedupKey, PendingPlacementTask>                    // 去重键见 §5.3.3
Map<ResourceKey<Level>, Map<Long, Set<TaskDedupKey>>>      // (维度, ChunkPos) → 任务索引
```

`TaskDedupKey` 即 §5.3.3 定义的 `(targetDimension, targetBase)`。

**区块索引是必需项，不是优化项。** `ChunkEvent.Load` 是极高频事件（玩家飞行时每分钟可达数千次），而任务永不自动过期、数量只增不减。若每次区块加载都遍历全部任务来判断「有没有我的活」，开销为 O(任务数) × 极高的调用频率，会随世界规模持续恶化。按 `(维度, ChunkPos)` 建索引后，单次查找为 O(1)。

`HomeRecord` 至少包含：

```text
UUID entityUuid
ResourceLocation entityType
ResourceKey<Level> dimension
BlockPos homePos
```

`PendingPlacementTask` 至少包含：

```text
ResourceKey<Level> targetDimension          // 去重键前半
BlockPos targetBase                         // 去重键后半
UUID lastSourceEntityUuid                   // 最近一次触发该任务的死亡实体，仅作信息字段
ResourceLocation entityType
HomeRecord homeSnapshot
PlacementRuleSnapshot placementSnapshot
ActivationRuleSnapshot activationSnapshot
SpawnRuleSnapshot spawnSnapshot
DuplicateRuleSnapshot duplicateSnapshot
long createdGameTime
long notBeforeGameTime                      // v3 新增：place_delay_ticks 换算出的到期时间
```

待放置任务保存完整规则快照，保证 `/reload` 或服务器重启后仍按死亡当时的规则执行（去重合并时以最近一次死亡为准）。

建议额外保留数据格式版本，当前值为 `1`：

```text
int dataVersion
```

读取高于当前支持版本的数据时，拒绝解析该数据文件并输出警告，不按低版本强行读取。

**并且必须同时拒绝写回该文件。** 若只拒绝解析而把数据视为空，下一次自动保存就会用空数据覆盖新版本写入的文件，使一次降级造成不可逆的数据丢失。建议在拒绝解析后将该文件改名备份（例如 `<name>.dat.unsupported-<version>`）并停止对其写入，或以只读方式跳过。

保存规则：

- 新增记录后调用 `setDirty()`；
- 读取损坏记录时跳过单条记录并输出警告，不让整个世界加载失败；
- 不把实体对象、EntityType 对象或第三方 NBT 写入 SavedData；
- 只保存 Registry ID、UUID、维度、坐标和规则快照；
- 待放置任务不设置自动过期时间，但必须按 §5.3.3 的去重键去重，否则会无界增长；
- 目标维度不可用时不得创建待放置任务（见 §5.3.1）。

### 6.1 Home 记录生命周期

| 时机 | 动作 |
|---|---|
| 实体加入世界 | `registerIfAbsent(uuid, ...)`，已有记录不覆盖 |
| Home 模式死亡且重生笼成功放置 | 重生笼保存 Home 快照后，删除死亡实体 UUID 记录 |
| 放置失败或进入 defer 队列 | 保留死亡实体记录，直到最终放置成功 |
| defer 任务因去重被合并 | 删除**被合并那次死亡**的 UUID 记录；任务快照以最近一次死亡为准 |
| 目标维度不可用导致放置永久失败 | 删除该实体 UUID 记录，不再重试，避免永久残留 |
| 重生笼成功创建实体 | 为新 UUID 登记原始 Home，且登记发生在 `addFreshEntity` 之前 |
| 重生笼生成失败 | 不创建新 UUID 记录，并清理可能已写入的临时登记 |

删除旧 UUID 后，重生笼方块实体中的 Home 快照成为后续重生链路的来源。

## 7. 事件与生命周期

### 7.1 EntityJoinLevelEvent

服务端监听 `EntityJoinLevelEvent`：

- 客户端事件直接忽略；
- 没有有效 `home` 规则的实体直接忽略；
- UUID 已存在时不覆盖；
- UUID 不存在时登记当前实体位置；
- `loadedFromDisk()` 只用于日志或来源标记，不得阻止旧实体首次登记；
- 回调中只进行轻量判断和登记，禁止扫描结构、放置方块或主动加载远处区块。

### 7.2 重生实体继承

重生笼方块实体必须保存原始 Home 信息，至少包括：

```text
HomeDimension
HomePos
```

实现必须优先采用以下时序：

1. `type.create(serverLevel)` 创建实体；
2. 从实体取得新 UUID；
3. 在 `addFreshEntity` 之前，用重生笼保存的 Home 快照登记新 UUID；
4. 设置位置、执行生成初始化并加入世界。

如果加入世界失败，必须清理刚创建的临时记录。不得把新实体的实际生成位置作为新的 Home。

### 7.3 数据包重载后的补登记

这是必做流程，不是可选优化。规则重载完成后，调度到服务器主线程，遍历各 `ServerLevel` 当前已经加载的实体，为命中 `home` 规则且 UUID 尚未登记的实体使用当前位置补登记。

该流程只遍历已加载实体，不主动加载新区块、不放置重生笼，并输出补登记、跳过和异常数量。

## 8. 全局配置调整

删除：

```text
Config.FORCE_PLACE_ON_NO_SAFE_SPOT
Config.FOREIGN_CAGE_BLOCK_IDS
```

新增并保留安全底线：

```text
Config.PROTECTED_BLOCK_IDS
```

默认至少包含：

```text
cataclysm:boss_respawner
```

继续保留：

```text
Config.ENABLE_MOD
Config.HIGHLIGHT_BOSS_RESPAWNER
Config.HIGHLIGHT_BOSS_RESPAWNER_RANGE
Config.DEBUG_INFO
```

普通放置策略由 JSON 控制；全局保护列表只承担不可绕过的安全底线，JSON 只能追加不能移除。

## 9. 内置数据包迁移

迁移目录：

```text
src/main/resources/data/**/boss_respawner/entries/*.json
```

所有内置条目统一：

- `schema_version` 改为 `3`（后续已升到 v3：`spawn` 改为模式 + 子对象，`placement.<mode>` 增加 `place_delay_ticks`）；
- `placement.mode` 改为必填并显式写 `home`；
- 原 `placement` 字段移动到 `placement.home`；
- 增加 `force_place`，默认 `false`；
- 增加 `search_failure_fallback`，默认 `true`；
- 增加 `protected_blocks: []`；
- `spawn` 补必填的 `mode`（内置规则全部为 `"summon"`，幻影骑士为 `"transform"`），原扁平字段移入 `spawn.summon`；
- 所有可选字段都显式写出，作为完整模板；
- 需要避开的方块显式写入 `placement.home.avoid_blocks`；
- 不再依赖旧的全局 `foreignCageBlockIds`。

## 10. 风险与已接受限制

- 安装本模组前已经移动过的旧 Boss，首次登记位置可能不是历史出生点。
- Boss 换阶段生成新 UUID 时，只有通过本模组重生笼生成的实体能可靠继承 Home；第三方自行替换实体不在本次范围内。
- **无 Home 记录时不会生成重生笼**：若某实体在任何时刻都未被本模组观察到（例如规则在其实体已加载之后才通过 `/reload` 添加，且补登记未覆盖），该实体死亡时不会放置重生笼，也不会回退到死亡位置。§7.3 的补登记与 §5.2 的警告日志就是为了让这种情况既罕见又可定位。
- 强制放置会忽略 `avoid_blocks`，但永远不能覆盖全局或规则 `protected_blocks`。除此之外它会**替换目标位置上的普通方块**：迟到的 defer 任务可能在玩家已改造过的位置落位，而重生笼方块为 `strength(-1.0F, ...)`，在生存模式下**不可破坏**，误放无法由玩家自行清除。规则作者应据此谨慎设置 `placement.home.offset`。
- 未加载目标区块时不会同步加载，而是永久保留持久化 defer 任务。任务规模由去重键 `(targetDimension, targetBase)` 界定，即与「出现过的放置目标数」成正比，而不是与历史死亡总数成正比。
- 目标维度不可用时立即失败并清理该实体的 Home 记录，因此不会因为维度被移除而留下永远无法完成的任务。
- 同一 Home 使用固定坐标强制放置且允许多笼时，相同坐标最终只能保留一个方块实体。
- SavedData 的 `setDirty()` 依赖正常世界保存流程，不提供每次登记立即落盘的事务保证。
- 若数据文件的 `dataVersion` 高于本模组支持的版本，本模组会拒绝读写该文件；降级后新版本写入的 Home 数据与待放置任务不可用（但不会被覆盖破坏）。
