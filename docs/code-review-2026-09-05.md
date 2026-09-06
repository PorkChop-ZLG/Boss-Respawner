# Universal Boss Respawner 代码审查报告

**审查日期：** 2026-09-05  
**审查类型：** 只读代码审查（未修改任何文件）  
**审查对象：** `D:\Minecraft\Boss-Respawner`  
**参考对比：** `D:\Minecraft\Cataclysm` 灾变 Boss 重生笼相关源码

---

## 一、审查范围

- 主入口与客户端入口  
- 方块与方块实体  
- 数据包系统  
- 放置与 HomePos  
- 死亡事件  
- 注册类  
- 客户端模型/渲染  
- 资源与文档  
- Gradle 配置与 `neoforge.mods.toml`

---

## 二、严重性分级

| 等级 | 含义 | 数量 |
|---|---|---|
| P0 | 阻断级，启动崩溃/必定破坏存档或主线程 | 未发现明确 P0 |
| P1 | 严重，可能造成服务器卡顿、强制加载区块、功能核心失效或启动风险 | 3 |
| P2 | 中等，字段/功能未生效、潜在重复生成、异常处理不足 | 9 |
| P3 | 建议，代码质量、冗余、日志、边界完善 | 若干 |

---

## 三、P1 严重问题

### P1-1 重复笼检查按 3D 立方体全扫描，且未先检查区块是否加载

**文件与位置**

- `placement/RespawnCagePlacer.java`
- `hasExistingCage()`：第 80-90 行
- `removeExistingCages()`：第 92-101 行

**问题描述**

```java
int r = Math.min(radius, 32);
for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
    if (level.getBlockState(pos).getBlock() instanceof BossRespawnerBlock ...)
}
```

- 默认 `duplicate.search_radius = 16`，扫描范围是 `33×33×33 = 35,937` 个方块。
- 最大半径 32 时是 `65×65×65 = 274,625` 个方块。
- 没有调用 `level.isLoaded(pos)` 保护，直接 `level.getBlockState(pos)`，可能触发未加载区块的加载/生成。
- `replace_existing` 模式还会再跑一次同样的扫描去删除旧笼。

**影响**

- Boss 死亡是低频事件，但触发时服务端主线程可能在单 tick 内执行数万甚至数十万次方块状态查询，并可能强制加载区块。

**建议修复**

- 扫描前/扫描中只处理已加载区块。
- 不要扫描完整 3D 立方体，限制合理 Y 范围。
- 改用遍历附近已加载 `LevelChunk` 的 `BlockEntity` 列表，只筛选 `BossRespawnerBlockEntity`。
- 可维护服务端缓存。

---

### P1-2 每区块数量限制按整根 16×16×世界高度逐方块扫描

**文件与位置**

- `placement/RespawnCagePlacer.java`
- `countCagesInChunk()`：第 103-118 行

**问题描述**

```java
for (int x = chunkX << 4; x < (chunkX << 4) + 16; x++) {
    for (int z = chunkZ << 4; z < (chunkZ << 4) + 16; z++) {
        for (int y = level.getMinBuildHeight(); y <= level.getMaxBuildHeight(); y++) {
            ...
        }
    }
}
```

- 默认高度范围约 -64 到 320，约 384~385 层。
- 每次放置执行约 `16×16×385 ≈ 98,560` 次 `getBlockState`。
- 仅 `Config.MAX_CAGES_PER_CHUNK >= 0` 时触发；默认 -1 不会执行。

**影响**

- 启用每区块限制后，每次 Boss 死亡放置笼都会执行约 10 万次方块查询，可能卡顿。

**建议修复**

- 遍历目标区块的 `BlockEntity` 列表，而不是逐方块扫描。
- 或维护“区块坐标 → 本模组笼数量”的服务端缓存。
- 注意 `<= level.getMaxBuildHeight()` 可能多查一层，建议 `>` 或明确开区间语义。

---

### P1-3 主类与客户端类同时使用同一 `modId` 标注 `@Mod`，存在启动期风险

**文件与位置**

- `UniversalBossRespawner.java` 第 20 行
- `UniversalBossRespawnerClient.java` 第 14 行

**问题描述**

- 两个类都声明为同一个 modId 的 `@Mod` 入口。
- 主类没有限定 `dist`，客户端类限定 `Dist.CLIENT`。
- 物理客户端上可能同时发现两个同 modId 的 `@Mod` 容器，导致重复初始化或启动错误。

**影响**

- 若 NeoForge 不允许这种写法，客户端启动会失败。
- 本项目此前已实际运行 `runClient` 和 `runServer` 成功，当前不是实际故障，但建议收敛入口结构。

**建议修复**

- 只保留一个 `@Mod` 主入口。
- 客户端专用逻辑改用 `@EventBusSubscriber(value = Dist.CLIENT, bus = Bus.MOD)` 或 `modEventBus.addListener`。

---

## 四、P2 中等问题

### P2-1 `activation.consume` 已定义但未生效

**文件与位置**

- `data/RespawnEntry.java`：第 173-179 行
- `block/BossRespawnerBlock.java`：第 64-68 行
- `blockentity/BossRespawnerBlockEntity.java`：`setSpawnerData()` 未接收 `consume`

**问题描述**

- JSON 支持 `activation.consume`，设计上 `false` 表示不消耗钥匙物品。
- 右键时只要不是创造模式就无条件消耗，没有读取该字段。
- 方块实体也没有保存该字段。

**影响**

- `"consume": false` 不会生效。

**建议修复**

- 将 `activation.consume` 传入方块实体并写入 NBT。
- 右键判断 `!player.getAbilities().instabuild && be.shouldConsume()` 才 `shrink`。

---

### P2-2 `visual.*` 整组未接入

**文件与位置**

- `data/RespawnEntry.java`：第 228-235 行 `VisualRule`
- `placement/RespawnCagePlacer.java`：第 56-71 行，未传视觉配置
- `blockentity/BossRespawnerBlockEntity.java`：未保存视觉字段
- `client/render/BossRespawnerBlockEntityRenderer.java`：始终渲染预览实体与钥匙物品

**问题描述**

- `visual.show_entity`、`show_item`、`entity_scale` 已解析但从未写入方块实体或渲染器读取。
- 默认条目写了这些字段，实际不起作用。

**建议修复**

- 将 `VisualRule` 保存到 BE 与 NBT。
- 渲染器根据 `showEntity` / `showItem` 控制渲染。
- `entityScale` 支持 `auto` / 数值。

---

### P2-3 客户端渲染每帧做实体/物品注册表解析

**文件与位置**

- `client/render/BossRespawnerBlockEntityRenderer.java`
- `blockentity/BossRespawnerBlockEntity.java` 的 `getDisplayEntity()`

**问题描述**

- 显示实体对象有缓存，但每次仍执行 `ResourceLocation.parse` + `BuiltInRegistries.ENTITY_TYPE.get`。
- 渲染钥匙物品时每次执行类似注册表查询。
- 非法字符串可能抛异常。

**影响**

- 视野内重生笼较多时增加每帧 CPU/GC 开销。

**建议修复**

- 在 BE 加载或 `setSpawnerData` 时缓存解析结果。
- 解析失败返回 null/空。

---

### P2-4 `spawn.count > 1` 时可能部分生成后返回失败，重试造成重复实体

**文件与位置**

- `blockentity/BossRespawnerBlockEntity.java`
- `trySpawn()`：第 151-174 行

**问题描述**

- 如果第 1 个实体添加成功，第 2 个失败，方法直接返回 `false`。
- 方块实体随后重试，已成功添加的实体不会撤销，可能重复生成。

**建议修复**

- 先创建并验证所有实体，全部成功后再添加。
- 或中途失败时移除已添加实体/改为单独跳过。

---

### P2-5 数据加载未校验 `activation.item`

**文件与位置**

- `data/RespawnRuleReloadListener.java`：第 49-58 行
- `data/RespawnEntry.java`：`ActivationRule.fromJson()`
- `blockentity/BossRespawnerBlockEntity.java`：`matchesKeyItem()`

**问题描述**

- 数据包加载只校验实体 ID，不校验钥匙物品 ID。
- 错误物品 ID 会导致重生笼被放置但永远无法激活。

**建议修复**

- 在 reload 阶段同时校验 `activation.item` 是否已注册。
- 未注册则跳过并警告。

---

### P2-6 方块实体 NBT 反序列化缺少防御性校验

**文件与位置**

- `blockentity/BossRespawnerBlockEntity.java`

**问题描述**

- `SpawnOffset` 未校验长度是否为 3。
- 多处直接 `ResourceLocation.parse`，对损坏 NBT 没有保护。
- `PlayerRange`、`MaxAttempts` 等未钳制。

**影响**

- 损坏/手工修改 NBT 可能造成 `ArrayIndexOutOfBoundsException` 或崩溃。

**建议修复**

- `SpawnOffset` 长度不是 3 时重置默认值。
- 安全解析 Registry ID。
- 对关键数值做范围钳制。

---

### P2-7 `maxAttempts` 达到上限后留下永久卡死的点亮方块

**文件与位置**

- `blockentity/BossRespawnerBlockEntity.java`
- `BossRespawnerBlock.java`

**问题描述**

- `maxAttempts` 达到后只设置 `stopped = true`，方块仍保持 `LIT=true`。
- 方块不可破坏，且 `LIT` 时右键直接返回，无法重新激活。

**建议修复**

- 达到上限后销毁、恢复未点亮并重置，或提供回收途径。

---

### P2-8 放置后写入 BE 数据未显式向客户端同步

**文件与位置**

- `placement/RespawnCagePlacer.java`：第 54-72 行

**问题描述**

- 流程为 `setBlock` → 获取 BE → `setSpawnerData()` → `setChanged()`。
- 未调用 `level.sendBlockUpdated(...)`。

**影响**

- 客户端可能先收到方块，但 BE 数据尚未同步，直到区块重载才显示正确。

**建议修复**

- 写入后调用 `level.sendBlockUpdated(pos, oldState, newState, 3)` 或手动发送 `ClientboundBlockEntityDataPacket`。

---

### P2-9 `HomePosLocator` 反射兼容存在边界风险

**文件与位置**

- `placement/HomePosLocator.java`：第 19-50 行

**问题描述**

- `Class.forName` 默认会初始化类，可能抛 `ExceptionInInitializerError` 等。
- catch 只捕获 `ReflectiveOperationException | RuntimeException`，不捕获 `LinkageError`。
- 跨维度 HomePos 与灾变语义不一致：灾变会把笼放到 HomePos 所在维度，本模组会回退死亡维度。

**建议修复**

- 使用 `Class.forName(name, false, classLoader)`。
- catch `LinkageError`。
- 明确文档当前只支持同维度 HomePos，后续可扩展跨维度。

---

## 五、P3 建议与代码质量问题

### P3-1 `schema_version` 被忽略

- `data/RespawnEntry.java` 未读取 `schema_version`。
- 建议未来用于不兼容升级提示。

### P3-2 多条目同优先级冲突时没有警告

- `data/RespawnRuleReloadListener.java` 第 63-69 行静默选择字典序更小条目。
- 建议同优先级冲突时输出警告。

### P3-3 未知 `placement.mode` 静默回退

- `placement/PlacementResolver.java` 第 73-82 行。
- 建议数据加载时校验 mode 枚举。

### P3-4 `VisualRule.entityScale` 目前只支持字符串 `auto`

- `data/RespawnEntry.java` 第 232 行使用 `getString`。
- 设计文档说支持 string/double，但当前没有数值解析。

### P3-5 `LOG_PLACEMENT` 与注释不一致

- `Config.java` 注释说“placed, skipped, spawned”。
- `RespawnCagePlacer.java` 只记录放置成功，跳过只打 debug，生成成功无日志。

### P3-6 未点亮方块实体也每 tick 执行

- `blockentity/BossRespawnerBlockEntity.java` 第 66-75 行。
- 可考虑只在 `LIT` 状态或客户端需要动画时才 tick。

### P3-7 手动放置的方块没有数据，无法使用

- `ModItems.java` 的 BlockItem 允许手动放置。
- 手动放置的 BE 默认无实体类型/钥匙物品，无法激活。
- 建议文档说明或提供设置功能。

### P3-8 无效 `spawn.nbt` 延迟到放置时才解析

- `placement/RespawnCagePlacer.java` 第 120-129 行。
- 建议 reload 时预解析。

### P3-9 渲染线程缺少对非法/缺失注册对象的兜底

- `client/render/BossRespawnerBlockEntityRenderer.java` 直接解析 ResourceLocation。
- 建议与 P2-3/P2-6 一起处理。

### P3-10 未调用 `level.gameEvent`

- `BossRespawnerBlock.java` 右键激活时没有触发 `GameEvent.BLOCK_CHANGE`。
- 可能影响 sculk 等监听方块。

### P3-11 客户端启动日志为调试残留

- `UniversalBossRespawnerClient.java` 仍有 `HELLO FROM CLIENT SETUP` 和用户名输出。
- 建议删除或改为 debug。

---

## 六、相比灾变模组的额外功能接入状态

| 额外功能 | 入口/位置 | 是否真正接入 | 说明 |
|---|---|---|---|
| JSON 数据包条目 | `RespawnEntry` / `RespawnRuleReloadListener` | ✅ 已接入 | 支持 `/reload`，未知实体跳过 |
| `entity` 数组 | `RespawnEntry.parseEntities` | ✅ 已接入 | 多条实体共享规则 |
| `priority` 冲突处理 | `RespawnRuleReloadListener.shouldReplace` | ✅ 已接入 | 同优先级无警告 |
| `activation.item` / `amount` | `RespawnCagePlacer` → `setSpawnerData` | ✅ 已接入 | item 未在 reload 时校验 |
| `activation.consume` | `RespawnEntry` 解析 | ❌ 未接入 | 永远消耗非创造物品 |
| `placement.mode/offset/search/requireGround/avoidFluids` | `PlacementResolver` | ✅ 已接入 | |
| `placement.avoid_blocks` + `Config.foreignCageBlockIds` | `PlacementResolver` | ✅ 已接入 | 仅检查候选位/下方 |
| `death.player_kill_only/dimensions/biomes` | `LivingDeathHandler` | ✅ 已接入 | |
| `spawn.delay_ticks` | `BossRespawnerBlockEntity.tick` | ✅ 已接入 | |
| `spawn.require_player_nearby/player_range/allow_peaceful` | BE tick/条件 | ✅ 已接入 | |
| `spawn.count` | BE `trySpawn` | ⚠️ 部分接入 | 部分失败重复风险 |
| `spawn.spawn_offset` | BE `trySpawn` | ✅ 已接入 | NBT 反序列化缺校验 |
| `spawn.nbt` | `RespawnCagePlacer.parseNbt` | ✅ 已接入 | 延迟到放置时解析 |
| `spawn.finalize_spawn` | BE `trySpawn` | ✅ 已接入 | |
| `spawn.set_home_to_cage` | BE `trySetHome` 反射 | ✅ 已接入 | 仅 Cataclysm `IHomeEntity` |
| `spawn.max_attempts` | BE tick | ⚠️ 已接入但终态不完整 | 达到上限后留下永久点亮方块 |
| `spawn.retry_interval_ticks` | BE tick | ✅ 已接入 | |
| `visual.show_entity/show_item/entity_scale` | `VisualRule` | ❌ 未接入 | BE 未保存，渲染器未读取 |
| `duplicate.keep_existing/replace_existing/allow_multiple` | `RespawnCagePlacer` | ✅ 已接入 | 3D 大范围扫描需优化 |
| `Config.MAX_CAGES_PER_CHUNK` | `RespawnCagePlacer.countCagesInChunk` | ✅ 已接入 | 整根 16×16×高度扫描需优化 |
| Home 通用鸭子类型 | `HomePosLocator` | ✅ 已接入 | 仅同维度 |
| `schema_version` | `RespawnEntry` | ❌ 未接入 | 无版本校验 |
| 无硬依赖 Cataclysm | 反射 + optional dependency | ✅ 已接入 | `neoforge.mods.toml` 声明 optional |

---

## 七、总体结论

该模组整体架构清晰，核心思路“JSON 数据驱动 + Registry ID + 方块实体 NBT 快照 + 反射兼容 Cataclysm”是正确的，核心链路完整：

死亡事件 → 规则查找 → 位置解析 → 放置笼 → 方块实体保存 → 右键激活 → 延迟生成 → 销毁笼。

当前主要问题集中在：

1. 服务端搜索路径性能与区块加载风险（P1）。
2. 若干“已定义未接入”字段（P2）：`activation.consume`、`visual.*`。
3. 边界处理不足（P2）：NBT 损坏、非法 ID、`count > 1`、`maxAttempts` 终态。
4. 启动期双 `@Mod` 风险（P1，已实测通过但建议整理）。

---

## 八、优先修复清单

1. **重构 `RespawnCagePlacer` 的重复笼/每区块限制查询**
   - 加 `isLoaded` 保护
   - 改用 chunk BlockEntity 列表或服务端缓存
   - 限制扫描范围/维度

2. **验证双 `@Mod` 入口**
   - 已实际运行通过，但仍建议收敛为单一主入口。

3. **补齐已声明字段**
   - `activation.consume`
   - `visual.show_entity/show_item/entity_scale`

4. **修复 `trySpawn` 的 `count > 1` 原子性**

5. **加固 NBT 与 ID 解析**
   - 校验 `SpawnOffset` 长度
   - 缓存/安全解析 `ResourceLocation`
   - reload 时校验 `activation.item`

6. **完善 `maxAttempts` 失败终态**

7. **优化客户端每帧注册表查找**
