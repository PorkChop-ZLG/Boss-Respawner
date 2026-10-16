# DeepSeek 交接执行计划：Home 出生点重生笼

**目标项目：** `D:\Minecraft\Boss-Respawner`  
**目标环境：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21  
**设计依据：** [Home 出生点模式设计](../home-respawn-design.md)  
**执行状态：** 设计已确认，交给 DeepSeek 实现  
**重要：** 本计划只作为实现交接，不要求当前 Codex 执行代码修改。

## 一、执行约束

1. 先阅读 `AGENTS.md`、`README.md`、`docs/home-respawn-design.md` 和相关源码。
2. 不要恢复旧版 `HomePosLocator`，不要反射调用第三方 Boss 的方法。
3. 不要为 Cataclysm、Legendary Monsters、Mowzie’s Mobs 编写专用 Java 分支。
4. 不要修改第三方实体 NBT，不要调用实体 `load(...)` 来写入 Home 信息。
5. 不要把 Home 模式搜索失败回退到死亡点搜索。
6. 不要保留全局 `forcePlaceOnNoSafeSpot` 或 `foreignCageBlockIds`；新增的全局保护底线 `PROTECTED_BLOCK_IDS` 不得删除。
7. `placement.mode` 是 Schema v2 必填字段，不要给解析器添加隐式默认值。
8. `force_place` 可以绕过 `avoid_blocks`，但不能绕过全局或规则 `protected_blocks`。
9. 目标区块未加载时统一 defer，不新增 `unloaded_target` JSON 字段，不执行同步写入。
10. 遵循现有 Java 21、NeoForge 1.21.1 和 Gradle 风格。
11. 修改前先查看工作区状态，不要覆盖用户已有的无关改动。
12. 生产代码改动应有对应测试或 GameTest/可重复验证路径。
13. 完成前必须运行新鲜的编译、测试和资源结构检查，并报告失败项。

## 二、必须修改的功能范围

### Task 1：RespawnEntry 改为 Schema v2

**文件：**

- `src/main/java/com/zonlong/bossrespawner/data/RespawnEntry.java`
- `src/main/java/com/zonlong/bossrespawner/data/RespawnRuleReloadListener.java`

**要求：**

1. 只接受 `schema_version: 2`。
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
   - `protected_blocks`
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
- `protected_blocks` 能解析。
- schema v1 被拒绝。

### Task 2：新增 BossHomeSavedData 与持久化待放置队列

**建议文件：**

- `src/main/java/com/zonlong/bossrespawner/data/BossHomeSavedData.java`
- 如有必要，新增 `HomeRecord.java`、`PendingPlacementTask.java` 或快照类型

**要求：**

1. 使用世界级 `SavedData` 保存 UUID 到 Home 位置的映射。
2. 数据挂在 Overworld 的 `DimensionDataStorage`，因为记录包含跨维度位置。
3. 文件名建议使用 `boss_respawner_homes`。
4. 记录至少包含：UUID、实体类型 ID、维度 ID、BlockPos。
5. 同一 SavedData 中增加持久化待放置任务，或使用独立但同等可靠的 SavedData。
6. 待放置任务至少保存：任务 ID、来源 UUID、实体类型、目标维度、目标坐标、Home 快照、放置规则快照、激活/生成/重复规则快照、创建时间。
7. 未加载目标区块时创建或更新任务，任务不设置自动过期时间，服务器重启后仍保留。
8. 任务必须去重，避免同一死亡事件重复创建。
9. 提供以下明确操作：
   - `get(UUID)`
   - `contains(UUID)`
   - `registerIfAbsent(...)`
   - `registerOrReplace(...)` 或等价的重生继承方法
   - `remove(UUID)`
   - `enqueuePendingPlacement(...)`
   - `removePendingPlacement(...)`
   - `setDirty()` 封装
10. UUID 已存在时，普通登记不能覆盖旧位置。
11. 重生笼继承原始 Home 时必须能够覆盖新 UUID 的临时记录。
12. NBT 读写使用稳定字段名并做类型校验。
13. 建议使用 `dataVersion = 1`；读取更高版本时拒绝并警告，单条坏记录跳过并警告。
14. 不保存实体对象、第三方 NBT 或运行时引用。

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
5. 重生笼生成实体时，先 `type.create()` 取得新 UUID，再在 `addFreshEntity` 前将其绑定到保存的原始 Home。
6. 加入世界失败时清理刚创建的新 UUID 记录。
7. 不要把新实体实际生成位置登记成 Home。
8. 如果 `count > 1`，同一批生成的实体都继承同一个 Home。
9. 如果生成失败，不创建错误的 Home 记录。

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
12. 目标区块未加载时，任何模式都必须创建或更新持久化 defer 任务，不能调用会触发区块加载的写入接口。
13. 区块真正加载后，安全地重试对应任务；成功后删除任务，任务不设置自动过期时间。
14. 强制放置忽略 `require_ground`、`avoid_fluids`、`avoid_blocks`，但不能忽略全局或规则 `protected_blocks`。
15. 保护检查至少覆盖目标位置和目标位置下方。
16. `avoid_blocks` 只用于搜索候选位置。
17. 普通搜索遇到已有本模组重生笼时继续寻找其他候选位置。
18. `force_place` 固定坐标与 `allow_multiple` 同时使用时，允许后放置者覆盖同坐标旧笼；文档和日志应明确这一限制。
19. 保留现有 `duplicate.mode` 行为。

### Task 6：清理 Config.java

**文件：**

- `src/main/java/com/zonlong/bossrespawner/Config.java`
- `src/main/java/com/zonlong/bossrespawner/DebugLog.java`
- 所有引用旧配置的 Java 文件和文档

**删除：**

- `FORCE_PLACE_ON_NO_SAFE_SPOT`
- `FOREIGN_CAGE_BLOCK_IDS`

**新增：**

- `PROTECTED_BLOCK_IDS`，默认至少包含 `cataclysm:boss_respawner`。

该全局列表是不可绕过的安全底线，JSON 的 `protected_blocks` 只能追加，不能移除。

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

1. 每个文件的 `schema_version` 改为 `2`。
2. 每个文件显式设置：

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
    "avoid_blocks": [],
    "protected_blocks": []
  }
}
```

3. 每个文件显式写出所有顶层和子对象可选字段，作为完整模板。
4. 原有 `avoid_blocks` 内容迁移到 `placement.home.avoid_blocks`。
5. 需要追加保护的方块写入 `placement.home.protected_blocks`。
6. Cataclysm 默认保护由全局 `PROTECTED_BLOCK_IDS` 提供，不得在 JSON 中移除。
7. 逐个检查全部内置 JSON，不能只做字符串替换后跳过解析验证。

### Task 8：文档和日志

**文件：**

- `README.md`
- `docs/universal-boss-respawner-design-v2.md` 或更新后的设计文档
- 相关历史文档如需保留，必须注明其为历史版本

**要求：**

1. 说明 schema v2。
2. 说明 `death` 和 `home` 两种模式。
3. 说明内置规则默认显式使用 `home`，解析器不隐式补默认模式。
4. 说明 `search_failure_fallback` 默认是 `true`。
5. 说明目标区块未加载时统一持久化 defer，不提供 JSON 字段选择。
6. 说明强制放置忽略 `avoid_blocks`，但不能覆盖保护方块。
7. 说明 Home 来源是本模组 SavedData，不是第三方实体 NBT。
8. 说明旧数据包不兼容是有意的版本变更。
9. 无 Home 记录时的日志必须包含实体 ID、UUID、死亡维度和死亡坐标，并提示检查规则加载或 `/reload`。

## 三、测试与验收标准

### 单元级验证

如果项目允许加入 JUnit，至少覆盖：

- schema v2 Home JSON 解析；
- schema v2 Death JSON 解析；
- 缺少 `mode` 被拒绝；
- 缺少选中模式对象被拒绝；
- 默认 `search_failure_fallback=true`；
- `protected_blocks` 解析；
- 非法模式拒绝；
- SavedData 记录序列化和反序列化；
- 待放置任务序列化、去重和重启恢复；
- 已有 UUID 不被普通登记覆盖；
- 重生继承可以在加入世界前写入新 UUID；
- 保护方块阻止强制放置；
- `avoid_blocks` 在强制放置时可绕过。

如果不适合引入 JUnit，则使用 NeoForge GameTest 或提供可重复的独立解析验证，不要以“只编译通过”代替行为验证。

### 静态资源验证

运行脚本或等价检查，确认：

- 所有 `src/main/resources/data/**/entries/*.json` 都是合法 JSON；
- 所有条目 `schema_version == 2`；
- 所有条目存在 `placement.mode`；
- 所有条目 `placement.mode == "home"`；
- 所有条目有 `placement.home`；
- 内置规则的可选字段全部显式存在；
- 不存在旧的顶层 `placement.offset`、`placement.search_down` 等字段；
- 不存在对 `FOREIGN_CAGE_BLOCK_IDS`、`FORCE_PLACE_ON_NO_SAFE_SPOT` 的源码引用；
- 存在 `PROTECTED_BLOCK_IDS` 的实现和引用。

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
10. `force_place=true` 时忽略 `avoid_blocks`，但命中保护方块时保持原方块并警告。
11. Death 模式仍能围绕死亡位置搜索。
12. 重生实体在 `addFreshEntity` 前继承原始 Home，不以新生成位置建立 Home。
13. 重生笼成功放置后删除旧 UUID 记录，失败时保留。
14. 删除全局强制放置和旧全局避让列表后，规则仍能独立决定普通搜索行为。

## 四、交付格式

DeepSeek 完成后应报告：

1. 修改和新增的文件列表。
2. SavedData 的字段和存档文件名。
3. JSON schema v2 的最终格式。
4. Home 登记与重生继承的事件时机。
5. 运行过的命令及完整结果摘要。
6. 未解决的问题、已接受的限制和需要人工进游戏验证的项目。

除非用户另行要求，不要自动提交 Git commit，不要修改无关功能。
