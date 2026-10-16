# DeepSeek 交接执行计划：Home 出生点重生笼

**目标项目：** `D:\Minecraft\Boss-Respawner`  
**目标环境：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21  
**设计依据：** [Home 出生点模式设计](../home-respawn-design.md)  
**执行状态：** 设计已确认，交给 DeepSeek 实现  
**重要：** 本计划只作为实现交接，不要求当前 Codex 执行代码修改。

> **后续更新（两轮，务必先读）：**
>
> 1. **v3**：`spawn` 改为「先选模式、再放子对象」（必填 `spawn.mode` + `spawn.summon` / `spawn.transform`）；
>    `placement.<mode>` 新增 `place_delay_ticks`（默认 `0`）。
> 2. **v4（当前）**：`schema_version` 只接受 **`4`**。**删除了 `placement.<mode>.protected_blocks` 字段与全局 `compat.protectedBlockIds` 配置项**；
>    `avoid_blocks` 成为唯一的方块列表，语义为「搜索第一趟强制避开，第一趟无果时第二趟忽略它」，因此**避让永远不会导致放不出笼子**；
>    `force_place: true` 与 `search_failure_fallback: true` **跳过搜索并覆盖任何方块**（含箱子、基岩），旧方块及其内容蒸发、不掉落。
>
> **因此：本文正文里凡是提到 `protected_blocks`、`PROTECTED_BLOCK_IDS`、"不可绕过的保护底线"、"强制放置不能覆盖保护方块"的地方，都属于 v2/v3 时代的历史描述，v4 已全部推翻**（推翻理由见 [Home 出生点模式设计 §5.5](../home-respawn-design.md)）。文中出现的 `schema_version: 2` 与扁平 `spawn` 同样是当时的实现。**当前格式与行为一律以 [README](../../README.md)、[Home 出生点模式设计](../home-respawn-design.md) 和源码为准。**

## 一、执行约束

1. 先阅读 `AGENTS.md`、`README.md`、`docs/home-respawn-design.md` 和相关源码。
2. 不要恢复旧版 `HomePosLocator`，不要反射调用第三方 Boss 的方法。
3. 不要为 Cataclysm、Legendary Monsters、Mowzie’s Mobs 编写专用 Java 分支。
4. 不要修改第三方实体 NBT，不要调用实体 `load(...)` 来写入 Home 信息。
5. 不要把 Home 模式搜索失败回退到死亡点搜索。
6. 不要保留全局 `forcePlaceOnNoSafeSpot` 或 `foreignCageBlockIds`；~~新增的全局保护底线 `PROTECTED_BLOCK_IDS` 不得删除~~ → **v4 已把 `PROTECTED_BLOCK_IDS` 也一并删除**（连同 JSON 字段 `protected_blocks`），不要重新引入。
7. `placement.mode` 是 Schema v2 必填字段，不要给解析器添加隐式默认值。
8. ~~`force_place` 可以绕过 `avoid_blocks`，但不能绕过全局或规则 `protected_blocks`。~~ → **已被 v4 推翻**：不存在保护底线，`force_place` 与兜底写入会覆盖任何方块。
9. 目标区块未加载时统一 defer，不新增 `unloaded_target` JSON 字段，不执行同步写入。
10. **目标维度不可用必须与目标区块未加载分开**：`server.getLevel(targetDimension) == null` 时立即失败并清理记录，不得创建待放置任务。
11. **`ChunkEvent.Load` 只能作为触发信号**：回调中不得调用任何世界写入接口，实际放置必须延后到当前区块加载流程之外的后续 tick。
12. defer 任务按 `(targetDimension, targetBase)` 去重，并按 `(维度, ChunkPos)` 建立索引；不得每次区块加载都遍历全部任务。
13. 执行 defer 任务时必须用任务快照重跑完整放置流程，不得绕过 `search_failure_fallback: false`。
14. 遵循现有 Java 21、NeoForge 1.21.1 和 Gradle 风格。
15. 修改前先查看工作区状态，不要覆盖用户已有的无关改动。
16. 生产代码改动应有对应测试或 GameTest/可重复验证路径。
17. 完成前必须运行新鲜的编译、测试和资源结构检查，并报告失败项。

## 二、必须修改的功能范围

### Task 1：RespawnEntry 改为 Schema v2

**文件：**

- `src/main/java/com/zonlong/bossrespawner/data/RespawnEntry.java`
- `src/main/java/com/zonlong/bossrespawner/data/RespawnRuleReloadListener.java`

**要求：**

1. ~~只接受 `schema_version: 2`。~~ → 后续为 `3`，**当前只接受 `4`**（见文首更新说明）。
2. `placement.mode` 必须存在，支持 `death` 和 `home`，缺失或非法时跳过规则并 `warn`。
3. `placement.death` 和 `placement.home` 使用嵌套对象。
4. 解析出明确的模式类型，例如 `PlacementMode.DEATH` / `PlacementMode.HOME`。
5. 两个模式对象都解析以下字段：
   - `offset`
   - `force_place`
   - `search_failure_fallback`
   - `search_down`
   - `search_up`
   - `horizontal_radius`
   - `require_ground`
   - `avoid_fluids`
   - `avoid_blocks`
   - ~~`protected_blocks`~~（v4 已删除；其中的方块 id 应并入 `avoid_blocks`）
6. `force_place` 默认值为 `false`。
7. `search_failure_fallback` 默认值必须为 `true`。
8. 其他可选字段可以省略并使用默认值。
9. 模式无效或对应对象缺失时跳过规则并输出 `warn`。
10. 不要让旧 schema v1 的扁平 placement 被静默解释为新结构。
11. 不添加 `unloaded_target` 字段；未加载区块策略由 Java 固定为 defer。

**建议测试：**

- Home 嵌套结构能解析。
- 缺少 `search_failure_fallback` 时得到 `true`。
- 缺少 `mode` 时被拒绝。
- Death 嵌套结构能解析。
- 非法模式被拒绝。
- 缺少选中模式对象被拒绝。
- ~~`protected_blocks` 能解析。~~ → v4 改为：`avoid_blocks` 能解析，残留的 `protected_blocks` 键**不产生任何效果**。
- schema v1 被拒绝。

### Task 2：新增 BossHomeSavedData 与持久化待放置队列

**建议文件：**

- `src/main/java/com/zonlong/bossrespawner/data/BossHomeSavedData.java`
- 如有必要，新增 `HomeRecord.java`、`PendingPlacementTask.java` 或快照类型

**要求：**

1. 使用世界级 `SavedData` 保存 UUID 到 Home 位置的映射。
2. 数据挂在 Overworld 的 `DimensionDataStorage`，因为记录包含跨维度位置。
3. 文件名使用 `boss_respawner_homes`，落点为 `saves/<存档名>/data/boss_respawner_homes.dat`。
4. **`name` 中不得包含 `/` 或 `\`**：`DimensionDataStorage` 不会创建子目录，而 `SavedData.save` 对写入失败只打一行 `Could not save data` 的 error 日志，会导致每次保存静默失败、数据永不落盘。必须使用平铺的单段文件名，不要放进 `data/boss_respawner/` 之类的子目录。
5. 记录至少包含：UUID、实体类型 ID、维度 ID、BlockPos。
6. 同一 SavedData 中增加持久化待放置任务，或使用独立但同等可靠的 SavedData。
7. 待放置任务至少保存：**目标维度 + 目标坐标**（二者构成去重键）、最近来源 UUID、实体类型、Home 快照、放置规则快照、激活/生成/重复规则快照、创建时间。
8. **目标维度不可用**（`server.getLevel(dimension) == null`）时不得创建任务：立即失败、输出 `error`，并删除该实体的 Home 记录。
9. 维度可用但未加载目标区块时创建或更新任务，任务不设置自动过期时间，服务器重启后仍保留。
10. 任务去重键为 `(targetDimension, targetBase)`；`lastSourceEntityUuid` 仅作信息字段。去重合并时删除被合并那次死亡的 Home 记录，规则快照以最近一次死亡为准。
11. 必须为任务建立 `(维度, ChunkPos)` 索引，使区块加载时的查找为 O(1)，不得遍历全部任务。
12. 提供以下明确操作：
    - `get(UUID)`
    - `contains(UUID)`
    - `registerIfAbsent(...)`
    - `registerOrReplace(...)` 或等价的重生继承方法
    - `remove(UUID)`
    - `enqueuePendingPlacement(...)`
    - `removePendingPlacement(...)`
    - `pendingForChunk(dimension, chunkPos)` 或等价的索引查询
    - `setDirty()` 封装
13. UUID 已存在时，普通登记不能覆盖旧位置。
14. 重生笼继承原始 Home 时必须能够覆盖新 UUID 的临时记录。
15. NBT 读写使用稳定字段名并做类型校验。
16. 建议使用 `dataVersion = 1`；读取更高版本时拒绝并警告，单条坏记录跳过并警告。
17. **拒绝解析更高版本 `dataVersion` 时必须同时拒绝写回该文件**（改名备份或只读跳过），否则下一次自动保存会用空数据覆盖新版本数据，造成不可逆丢失。
18. 不保存实体对象、第三方 NBT 或运行时引用。

**建议数据字段：**

```text
UUID
EntityType
Dimension
X
Y
Z
```

### Task 3：监听实体加入世界并登记 Home

**文件：**

- `src/main/java/com/zonlong/bossrespawner/event/EntityHomeRegistrationHandler.java`
- `src/main/java/com/zonlong/bossrespawner/UniversalBossRespawner.java`

**要求：**

1. 注册到 `NeoForge.EVENT_BUS`。
2. 只在服务端处理。
3. 使用 `EntityJoinLevelEvent`。
4. 通过 `RespawnRuleManager.INSTANCE.find(entity.getType())` 判断是否有规则。
5. 仅对 `entry.placement().mode() == HOME` 的实体登记。
6. 新 UUID 登记事件发生时的维度和 `blockPosition()`。
7. 已有 UUID 不覆盖。
8. `loadedFromDisk()` 仅用于日志或来源标记，不得阻止旧实体首次登记。
9. 回调中不扫描结构、不加载远处区块、不放置重生笼。
10. 日志应包含实体 ID、UUID、维度、坐标和是否从磁盘加载。

### Task 4：扩展 BossRespawnerBlockEntity 保存 Home 信息

**文件：**

- `src/main/java/com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java`
- `src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java`

**要求：**

1. 重生笼放置时，如果规则是 Home 模式，保存原始 Home 维度和坐标。
2. 保存字段不能依赖第三方实体 NBT。
3. 旧的死亡点重生笼仍然可以加载和工作；缺少 Home 字段时按旧快照行为处理。
4. Home 模式死亡后，重生笼成功放置时才删除死亡 UUID 的 Home 记录；进入 defer 或放置失败时保留记录。
5. defer 任务因去重被合并时，删除被合并那次死亡的 UUID 记录。
6. 目标维度不可用导致放置永久失败时，删除该实体 UUID 记录。
7. 重生笼生成实体时，先 `type.create()` 取得新 UUID，再在 `addFreshEntity` 前将其绑定到保存的原始 Home。
8. 加入世界失败时清理刚创建的新 UUID 记录。
9. 不要把新实体实际生成位置登记成 Home。
10. 如果 `count > 1`，同一批生成的实体都继承同一个 Home。
11. 如果生成失败，不创建错误的 Home 记录。

### Task 5：重构放置解析与 defer 队列

**文件：**

- `src/main/java/com/zonlong/bossrespawner/placement/PlacementResolver.java`
- `src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java`
- `src/main/java/com/zonlong/bossrespawner/event/LivingDeathHandler.java`
- 新增区块加载事件处理类或等价服务

**要求：**

1. `RespawnCagePlacer.tryPlace(...)` 根据规则模式选择基准点。
2. Death 模式基准点是死亡实体位置。
3. Home 模式基准点是 SavedData 中该 UUID 的 Home 位置和维度。
4. Home 模式缺少记录时跳过并输出 `warn`，不能使用死亡位置。
5. 模式配置的 `offset` 应只应用一次。
6. `force_place: true` 时，目标区块已加载才直接使用 `base`；不执行搜索。
7. 普通搜索只以当前模式的 `base` 为中心。
8. 搜索顺序继续保持现有逻辑：向下、向上、水平范围。
9. 搜索失败且 `search_failure_fallback: true` 时，目标区块已加载才使用同一个 `base` 执行强制放置。
10. 搜索失败且 `search_failure_fallback: false` 时，不放置并输出 `warn`。
11. Home 搜索失败不能切换到 Death 搜索。
12. **区分目标维度不可用与目标区块未加载**：`server.getLevel(targetDimension) == null` 时立即失败、输出 `error`、**不创建待放置任务**，并删除该实体的 Home 记录；只有维度可用且 `level.isLoaded(base)` 为 `false` 时才进入 defer。
13. 目标区块未加载时，任何模式都必须创建或更新持久化 defer 任务，且不能调用会触发区块加载的写入接口。
14. 任务去重键为 `(targetDimension, targetBase)`；合并时清理被合并那次死亡的 Home 记录。
15. 执行 defer 任务时必须用任务中保存的规则快照**重跑完整放置流程**（搜索 → 按 `search_failure_fallback` 决定是否强制放置）。不得因为「已经等待很久」就直接强制放置，也不得绕过 `search_failure_fallback: false`。
16. `ChunkEvent.Load`（或等价事件）只作为触发信号：回调中**不得调用任何世界写入接口**，只更新待执行标记或加入待执行集合；实际放置必须延后到当前区块加载流程之外的后续 tick（例如 `server.execute(...)`，或在 `ServerTickEvent` 中消费待执行列表）。
17. 任务成功后删除；任务不设置自动过期时间。
18. 必须按 `(维度, ChunkPos)` 索引任务，使单次区块加载的查找为 O(1)，不得遍历全部任务。
19. ~~强制放置忽略 `require_ground`、`avoid_fluids`、`avoid_blocks`，但不能忽略全局或规则 `protected_blocks`。~~ → **v4**：强制放置与兜底写入忽略上述全部条件，并且**会覆盖任何方块**（不再存在保护概念）。
20. ~~保护检查至少覆盖目标位置和目标位置下方。~~ → **v4 删除**：写入路径不做任何方块检查。
21. ~~`avoid_blocks` 只用于搜索候选位置。~~ → **v4 补充**：`avoid_blocks` 只影响搜索第 1 趟；第 1 趟无果时第 2 趟忽略它。
22. 普通搜索遇到已有本模组重生笼时继续寻找其他候选位置。
23. `force_place` 固定坐标与 `allow_multiple` 同时使用时，允许后放置者覆盖同坐标旧笼；文档和日志应明确这一限制。
24. 保留现有 `duplicate.mode` 行为。

### Task 6：清理 Config.java

**文件：**

- `src/main/java/com/zonlong/bossrespawner/Config.java`
- `src/main/java/com/zonlong/bossrespawner/DebugLog.java`
- 所有引用旧配置的 Java 文件和文档

**删除：**

- `FORCE_PLACE_ON_NO_SAFE_SPOT`
- `FOREIGN_CAGE_BLOCK_IDS`

**新增（v2/v3 当时）：**

- ~~`PROTECTED_BLOCK_IDS`，默认至少包含 `cataclysm:boss_respawner`。~~
- **v4：该常量与整个 `[compat]` 配置组也已删除。** 它默认含的 `cataclysm:boss_respawner` 改为写进每条规则的 `avoid_blocks`。
  ~~该全局列表是不可绕过的安全底线，JSON 的 `protected_blocks` 只能追加，不能移除。~~ → 该设计已被推翻，理由见 [Home 出生点模式设计 §5.5](../home-respawn-design.md)。

**保留：**

- `ENABLE_MOD`
- `HIGHLIGHT_BOSS_RESPAWNER`
- `HIGHLIGHT_BOSS_RESPAWNER_RANGE`
- `DEBUG_INFO`

### Task 7：迁移全部内置 JSON

**目录：**

```text
src/main/resources/data/**/boss_respawner/entries/*.json
```

**要求：**

1. ~~每个文件的 `schema_version` 改为 `2`。~~ → 后续为 `3`，**当前为 `4`**。
2. ~~每个文件显式设置：~~（**v4 的形态：删掉 `protected_blocks`**）

```json
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
    "avoid_blocks": []
  }
}
```

3. 每个文件显式写出所有顶层和子对象可选字段，作为完整模板。
4. 原有 `avoid_blocks` 内容迁移到 `placement.home.avoid_blocks`；v4 起它是**唯一**的方块列表。
5. ~~需要追加保护的方块写入 `placement.home.protected_blocks`。~~ → **v4**：希望避让的方块（箱子、祭坛……）写入 `placement.home.avoid_blocks`；不再有 `protected_blocks`。
6. ~~Cataclysm 默认保护由全局 `PROTECTED_BLOCK_IDS` 提供，不得在 JSON 中移除。~~ → **v4**：该全局列表已删除，`cataclysm:boss_respawner` 改为显式写进每条规则的 `avoid_blocks`。
7. 逐个检查全部内置 JSON，不能只做字符串替换后跳过解析验证。

### Task 8：文档和日志

**文件：**

- `README.md`
- `docs/universal-boss-respawner-design-v2.md` 或更新后的设计文档
- 相关历史文档如需保留，必须注明其为历史版本

**要求：**

1. 说明 schema ~~v2~~ → **v4**（当前）。
2. 说明 `death` 和 `home` 两种模式。
3. 说明内置规则默认显式使用 `home`，解析器不隐式补默认模式。
4. 说明 `search_failure_fallback` 默认是 `true`。
5. 说明目标区块未加载时统一持久化 defer，不提供 JSON 字段选择；目标维度不可用时立即失败。
6. ~~说明强制放置忽略 `avoid_blocks`，但不能覆盖保护方块。~~ → **v4**：说明强制放置与兜底写入忽略 `avoid_blocks`，**并且会覆盖任何方块**（含箱子、基岩），旧内容蒸发不掉落。
7. 说明强制放置会替换目标位置上的普通方块，且重生笼方块在生存模式下不可破坏，误放无法由玩家自行清除。
8. 说明 Home 来源是本模组 SavedData，不是第三方实体 NBT。
9. 说明旧数据包不兼容是有意的版本变更，并给出迁移对照表（**当前为 v1 → v4、v2 → v4、v3 → v4 三张**），至少覆盖：
   - `schema_version`：`1`/`2`/`3` → `4`；
   - `placement.<扁平字段>` → `placement.death.<字段>` 或 `placement.home.<字段>`；
   - `placement.mode` 成为必填；
   - 全局 `forcePlaceOnNoSafeSpot` → `placement.<mode>.search_failure_fallback`；
   - 全局 `foreignCageBlockIds` → `placement.<mode>.avoid_blocks`；~~并说明新增的全局 `PROTECTED_BLOCK_IDS` 与规则级 `protected_blocks`~~ → **v4：两者都已删除，`protected_blocks` 里的 id 应并入 `avoid_blocks`**。
10. 说明 defer 任务按放置目标去重，且其规模与「出现过的放置目标数」成正比。
11. 无 Home 记录时的日志必须包含实体 ID、UUID、死亡维度和死亡坐标，并提示检查规则加载或 `/reload`。

## 三、测试与验收标准

### 单元级验证

如果项目允许加入 JUnit，至少覆盖：

- schema ~~v2~~ → **v4** Home JSON 解析；
- schema ~~v2~~ → **v4** Death JSON 解析；
- 缺少 `mode` 被拒绝；
- 缺少选中模式对象被拒绝；
- 默认 `search_failure_fallback=true`；
- ~~`protected_blocks` 解析；~~ → **v4**：`avoid_blocks` 解析，且残留的 `protected_blocks` 键不生效；
- 非法模式拒绝；
- SavedData 记录序列化和反序列化；
- 待放置任务序列化、去重和重启恢复；
- 去重键 `(targetDimension, targetBase)` 的合并行为，以及合并时清理被合并死亡的 Home 记录；
- 目标维度不可用时不创建任务并清理 Home 记录；
- 拒绝更高 `dataVersion` 时不做写回；
- 已有 UUID 不被普通登记覆盖；
- 重生继承可以在加入世界前写入新 UUID；
- ~~保护方块阻止强制放置；~~ → **v4**：强制放置/兜底写入覆盖目标方块，且 v4 的唯一 ABANDON 分支是"两趟搜索都失败且 `search_failure_fallback: false`"；
- `avoid_blocks` 在强制放置时可绕过；且 `PlacementResolver` 确实会做第 2 趟"忽略避让"的搜索。

如果不适合引入 JUnit，则使用 NeoForge GameTest 或提供可重复的独立解析验证，不要以“只编译通过”代替行为验证。

### 静态资源验证

运行脚本或等价检查，确认：

- 所有 `src/main/resources/data/**/entries/*.json` 都是合法 JSON；
- 所有条目 `schema_version == 4`；
- 所有条目存在 `placement.mode`；
- 所有条目 `placement.mode == "home"`；
- 所有条目有 `placement.home`；
- 所有条目**不存在** `protected_blocks` 键；
- 内置规则的可选字段全部显式存在；
- 不存在旧的顶层 `placement.offset`、`placement.search_down` 等字段；
- 不存在对 `FOREIGN_CAGE_BLOCK_IDS`、`FORCE_PLACE_ON_NO_SAFE_SPOT`、`PROTECTED_BLOCK_IDS`、`protectedBlocks` 的源码引用；
- 不存在"写入前的保护拒绝"分支（旧日志行 `Refusing to place the respawn cage`）。

### 构建验证

```text
gradlew.bat compileJava
gradlew.bat test
```

如果 Gradle 因网络依赖失败，必须报告完整原因，不得声称实现完成。

### 行为验收

1. Home 模式 Boss 加入世界后生成 Home 记录。
2. 同一实体移动后重新加载，不覆盖原 Home。
3. 旧存档中未记录的 Boss 首次加载时登记当前位置。
4. `/reload` 新增 Home 规则后，当前已加载匹配实体得到补登记。
5. Home 模式死亡后，搜索中心是 Home，不是死亡点。
6. Home 所在区块未加载时，不触发区块加载，而是创建持久化 defer 任务。
7. 服务器重启后 defer 任务仍存在，目标区块加载后可以完成放置。
8. Home 搜索失败且 fallback 为 true 时，已加载目标位置执行强制放置。
9. Home 搜索失败且 fallback 为 false 时，不生成并输出警告。
10. ~~`force_place=true` 时忽略 `avoid_blocks`，但命中保护方块时保持原方块并警告。~~ → **v4**：`force_place=true` 时忽略 `avoid_blocks` 且**覆盖目标处的任何方块**（旧内容蒸发、不掉落）；不存在保护方块。
11. Death 模式仍能围绕死亡位置搜索。
12. 重生实体在 `addFreshEntity` 前继承原始 Home，不以新生成位置建立 Home。
13. 重生笼成功放置后删除旧 UUID 记录，失败时保留。
14. 删除全局强制放置和旧全局避让列表后，规则仍能独立决定普通搜索行为。
15. 目标维度不可用时立即失败并清理 Home 记录，且**不产生**待放置任务。
16. defer 任务按 `(targetDimension, targetBase)` 去重：同一放置目标的多次死亡只保留一条任务。
17. `ChunkEvent.Load` 回调执行期间**没有发生任何世界写入**；实际放置发生在之后的 tick。
18. defer 任务执行时重跑完整放置流程：`search_failure_fallback: false` 的规则不会因为 defer 而被强制放置。
19. 区块加载时的任务查找不遍历全部任务（构造大量任务验证为 O(1) 查找）。
20. `dataVersion` 高于支持版本时，文件既不被解析也不被覆盖写回。
21. `replace_existing` 在 defer 场景下不会从区块加载回调内部触发相邻区块的写入。
22. **（v4 追加）** 避让不会导致笼子消失：存在"只有忽略 `avoid_blocks` 才能落位"的候选时，笼子仍然放在那里（第 2 趟搜索）。

## 四、交付格式

DeepSeek 完成后应报告：

1. 修改和新增的文件列表。
2. SavedData 的字段和存档文件名。
3. JSON schema 的最终格式（~~v2~~ → **v4**）。
4. Home 登记与重生继承的事件时机。
5. 运行过的命令及完整结果摘要。
6. 未解决的问题、已接受的限制和需要人工进游戏验证的项目。

除非用户另行要求，不要自动提交 Git commit，不要修改无关功能。
