# Universal Boss Respawner Bug 修复与设计调整方案

**日期：** 2026-09-05  
**目标：** 根据代码审查结果修复 P1/P2/P3 问题，并同步修改初版设计  
**状态：** 待批准/待执行

---

## 一、本次设计调整（大幅修改）

| 编号 | 调整 |
|---|---|
| D1 | 删除每区块重生笼数量限制，允许一个区块出现多个重生笼 |
| D2 | 删除 `visual.*` 数据驱动，跟随灾变：始终渲染幽灵生物预览与钥匙物品 |
| D3 | 删除 HomePos/老巢概念，本模组只在死亡地点生成重生笼 |
| D4 | 删除 `spawn.set_home_to_cage` 相关代码 |
| D5 | `maxAttempts` 默认改为 20 次；失败后恢复未点亮并重置，聊天栏+日志输出警告 |
| D6 | JSON Schema 同步简化：移除 `visual`、`placement.home*`、`set_home_to_cage`、每区块限制相关字段 |

---

## 二、源码头核对记录

- `LevelChunk#getBlockEntities()` 返回 `Map<BlockPos, BlockEntity>`，可用于区块内 BlockEntity 遍历。
- `ChunkSource#getChunkNow(int, int)` 可获取已加载 `LevelChunk`，不会强制加载区块。
- `ServerLevel#sendBlockUpdated(BlockPos, BlockState, BlockState, int)` 存在，可用于同步方块/方块实体。
- `Player#displayClientMessage(Component, boolean)` 和 `ServerPlayer#sendSystemMessage(Component)` 可用于聊天警告。
- `FMLEnvironment.dist == Dist.CLIENT` 可用于主入口内判断是否执行客户端初始化。
- `ModConfigSpec.Builder#translation(String)` 用于显式配置翻译键。
- `TagParser.parseTag(String)` 用于预解析 SNBT。

---

## 三、任务总览

| 任务 | 对应审查项 | 状态 |
|---|---|---|
| T1 | P1-1 重复笼检查性能优化 | 计划 |
| T2 | P1-2 删除每区块数量限制 | 计划 |
| T3 | P1-3 收敛为单一 `@Mod` 入口 | 计划 |
| T4 | P2-1 `activation.consume` 接入 | 计划 |
| T5 | P2-2 删除 `visual.*` | 计划 |
| T6 | P2-3 客户端注册表解析缓存 | 计划 |
| T7 | P2-4 `spawn.count > 1` 原子性 | 计划 |
| T8 | P2-5 reload 校验 `activation.item` | 计划 |
| T9 | P2-6 NBT 防御性校验 | 计划 |
| T10 | P2-7 `maxAttempts` 终态与警告 | 计划 |
| T11 | P2-8 放置后同步客户端 | 计划 |
| T12 | P2-9 删除 HomePosLocator，仅死亡点 | 计划 |
| T13 | P3-1 `schema_version` 校验 | 计划 |
| T14 | P3-6 未点亮时减少 tick 开销 | 计划 |
| T15 | P3-8 reload 预解析 `spawn.nbt` | 计划 |
| T16 | P3-9 渲染兜底非法注册对象 | 计划 |
| T17 | P3-10 右键激活触发 `GameEvent.BLOCK_CHANGE` | 计划 |
| T18 | P3-11 清理客户端调试日志 | 计划 |

---

## 四、详细任务

### T1 重复笼检查性能优化（P1-1）

**文件**

- `placement/RespawnCagePlacer.java`

**目标**

- 不再使用 `BlockPos.betweenClosed` 的 3D 全扫描。
- 不强制加载未加载区块。
- 只遍历附近已加载区块中的 `BossRespawnerBlockEntity`。

**实现思路**

1. 根据 `duplicate.search_radius` 计算区块范围：
   ```java
   int minChunkX = (center.getX() - r) >> 4;
   int maxChunkX = (center.getX() + r) >> 4;
   int minChunkZ = (center.getZ() - r) >> 4;
   int maxChunkZ = (center.getZ() + r) >> 4;
   ```
2. 对每个区块：
   ```java
   LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
   if (chunk == null) continue;
   for (BlockEntity be : chunk.getBlockEntities().values()) {
       if (be instanceof BossRespawnerBlockEntity cage) {
           if (cage.getBlockPos().distSqr(center) <= (double) (r * r)
                   && entityTypeId.equals(cage.getEntityTypeId())) {
               return true;
           }
       }
   }
   ```
3. `removeExistingCages` 同样改为遍历已加载区块 BlockEntity。

**验收**

- 不调用 `getBlockState` 扫描全立方体。
- 未加载区块跳过。
- 重复检测与替换逻辑结果与原先一致。

---

### T2 删除每区块数量限制（P1-2）

**文件**

- `Config.java`：删除 `MAX_CAGES_PER_CHUNK`
- `placement/RespawnCagePlacer.java`：删除 `countCagesInChunk()` 及其调用
- `assets/boss_respawner/lang/en_us.json` / `zh_cn.json`：删除对应翻译
- `docs/**` / `README.md`：删除该配置说明
- `docs/plans/2026-09-05-universal-boss-respawner-design.md`：同步更新

**验收**

- 代码中无 `MAX_CAGES_PER_CHUNK`、`countCagesInChunk` 残留。
- 一个区块可以存在多个重生笼。

---

### T3 单一 `@Mod` 入口（P1-3）

**文件**

- `UniversalBossRespawner.java`
- `UniversalBossRespawnerClient.java`

**实现思路**

1. 只保留 `UniversalBossRespawner` 的 `@Mod`。
2. 删除 `UniversalBossRespawnerClient` 上的 `@Mod`。
3. 在主构造器中：
   ```java
   if (FMLEnvironment.dist == Dist.CLIENT) {
       UniversalBossRespawnerClient.init(modContainer);
   }
   ```
4. `UniversalBossRespawnerClient` 改为普通客户端辅助类：
   ```java
   public static void init(ModContainer container) {
       container.registerExtensionPoint(IConfigScreenFactory.class,
               (mc, parent) -> new ConfigurationScreen(container, parent));
   }
   ```
5. 删除 `UniversalBossRespawnerClient` 中的调试日志。

**验收**

- 全项目只有一个 `@Mod` 注解。
- `runClient` 正常打开配置界面。
- `runServer` 正常启动且不会引用客户端类。

---

### T4 接入 `activation.consume`（P2-1）

**文件**

- `data/RespawnEntry.java`
- `blockentity/BossRespawnerBlockEntity.java`
- `placement/RespawnCagePlacer.java`
- `block/BossRespawnerBlock.java`

**实现思路**

1. `BossRespawnerBlockEntity` 增加 `boolean consumeKeyItem` 字段。
2. `setSpawnerData(...)` 增加 `boolean consume` 参数并写入 NBT。
3. `RespawnCagePlacer` 传入 `entry.activation().consume()`。
4. 右键逻辑：
   ```java
   if (!player.getAbilities().instabuild && be.shouldConsume()) {
       stack.shrink(be.getKeyAmount());
   }
   ```

**验收**

- `consume=false` 创造/生存模式都不消耗钥匙物品。
- `consume=true` 非创造模式仍消耗。

---

### T5 删除 `visual.*`（P2-2）

**文件**

- `data/RespawnEntry.java`：删除 `VisualRule`、`visual` 解析
- `docs/**` / `README.md` / 示例 JSON / 设计文档：删除 `visual`
- `blockentity/BossRespawnerBlockEntity.java`：如无其他改动可保持不变
- `client/**`：渲染器保持始终渲染，不读取 `visual`

**验收**

- JSON 不再需要 `visual`。
- 渲染器仍始终显示幽灵生物与钥匙物品。

---

### T6 客户端注册表解析缓存（P2-3）

**文件**

- `blockentity/BossRespawnerBlockEntity.java`
- `client/render/BossRespawnerBlockEntityRenderer.java`

**实现思路**

1. BE 增加缓存字段：
   - `ResourceLocation entityLocation`
   - `EntityType<?> cachedEntityType`
   - `ResourceLocation itemLocation`
   - `Item cachedItem`
2. 在 `setSpawnerData` 和 `loadAdditional` 时解析一次。
3. 新增：
   - `@Nullable EntityType<?> getCachedEntityType()`
   - `@Nullable Item getCachedKeyItem()`
4. 渲染器使用缓存，不再每帧 `ResourceLocation.parse`。
5. 所有 `ResourceLocation` 解析使用 `tryParse`，失败返回空并警告。

**验收**

- 渲染帧路径无 `BuiltInRegistries.get` / `ResourceLocation.parse`。
- 非法 ID 不会在渲染线程抛异常。

---

### T7 `spawn.count > 1` 原子性（P2-4）

**文件**

- `blockentity/BossRespawnerBlockEntity.java`

**实现思路**

1. 先循环创建全部实体，应用 NBT / `finalizeSpawn` / `setHome`。
2. 全部创建成功后，再循环 `addFreshEntity`。
3. 若某个 `addFreshEntity` 失败：
   - 对已添加成功的实体调用 `entity.discard()`。
   - 返回 `false`，避免重试后重复生成。
4. 若某个实体创建失败，直接返回 `false`，不添加任何实体。

**验收**

- `count > 1` 不会因部分失败产生重复实体。

---

### T8 reload 校验 `activation.item`（P2-5）

**文件**

- `data/RespawnRuleReloadListener.java`

**实现思路**

- 加载每个条目时同时检查：
  ```java
  if (!BuiltInRegistries.ITEM.containsKey(entry.activation().item())) {
      LOGGER.warn("Skipping ... invalid activation item ...");
      continue;
  }
  ```

**验收**

- 错误物品 ID 在 `/reload` 时被跳过并警告。
- 不会生成“死笼”。

---

### T9 NBT 防御性校验（P2-6）

**文件**

- `blockentity/BossRespawnerBlockEntity.java`

**实现思路**

1. `SpawnOffset` 读取后：
   ```java
   if (spawnOffset == null || spawnOffset.length != 3) {
       spawnOffset = new int[]{0, 1, 0};
   }
   ```
2. 所有 Registry ID 使用 `tryParse` + 缓存，失败清空。
3. 对 `delayTicks`、`playerRange`、`count`、`retryIntervalTicks`、`maxAttempts` 做范围钳制：
   - `delayTicks >= 0`
   - `playerRange > 0`
   - `count >= 1`
   - `retryIntervalTicks >= 1`
   - `maxAttempts >= 1` 或 `-1`

**验收**

- 损坏 NBT 不会导致数组越界或非法解析崩溃。

---

### T10 `maxAttempts` 终态与警告（P2-7）

**文件**

- `data/RespawnEntry.java`
- `blockentity/BossRespawnerBlockEntity.java`
- `assets/boss_respawner/lang/en_us.json` / `zh_cn.json`
- `docs/**`

**实现思路**

1. `SpawnRule` 默认 `max_attempts` 改为 `20`，默认 `retry_interval_ticks` 改为 `4`。
2. BE 达到上限后：
   - `attempts = 0`
   - `stopped = false`
   - `litTicks = 0`
   - 方块状态恢复 `LIT=false`
   - 调用 `serverLevel.sendBlockUpdated(...)`
3. 警告：
   - 日志：`LOGGER.warn("...")`
   - 聊天：向方块附近玩家发送 `Component.translatable("boss_respawner.message.respawn_failed", entityTypeId)`
4. 新增翻译：
   ```text
   boss_respawner.message.respawn_failed
   ```

**验收**

- 默认 20 次尝试。
- 失败后恢复未点亮，可再次激活。
- 聊天栏和日志都有警告。

---

### T11 放置后同步客户端（P2-8）

**文件**

- `placement/RespawnCagePlacer.java`

**实现思路**

- `setSpawnerData(...)` 后调用：
  ```java
  level.sendBlockUpdated(pos, state, state, 3);
  ```
  或发送 `ClientboundBlockEntityDataPacket`。

**验收**

- 联机环境下刚放置的重生笼能立即显示实体预览/钥匙物品。

---

### T12 删除 HomePosLocator（P2-9）

**文件**

- 删除 `placement/HomePosLocator.java`
- `placement/PlacementResolver.java`
- `data/RespawnEntry.java`
- `blockentity/BossRespawnerBlockEntity.java`
- `placement/RespawnCagePlacer.java`
- `docs/**` / README / 示例 JSON

**实现思路**

1. `PlacementRule` 删除 `mode` 字段。
2. `PlacementResolver` 直接使用 `entity.blockPosition()`。
3. 删除 `HomePosLocator.findHomePos` 调用。
4. 删除 `spawn.set_home_to_cage`、`trySetHome`、相关 NBT/参数。
5. 更新 JSON 文档：放置位置只有“死亡点”。

**验收**

- 无 HomePos 相关类/字段/文档。
- 重生笼始终在死亡位置附近生成。

---

### T13 `schema_version` 校验（P3-1）

**文件**

- `data/RespawnEntry.java`

**实现思路**

- 读取 `schema_version`，缺省 `1`。
- 若 `> 1` 或非 `1`，警告并跳过该条目。

**验收**

- 非法/未来版本 JSON 会被明确拒绝并警告。

---

### T14 未点亮时减少 tick 开销（P3-6）

**文件**

- `blockentity/BossRespawnerBlockEntity.java`

**实现思路**

- `tick` 开头：
  ```java
  if (!state.getValue(BossRespawnerBlock.LIT)) {
      if (level.isClientSide) {
          // 不播放动画，不需要自增 tickCount
      }
      return;
  }
  be.tickCount++;
  if (level.isClientSide) return;
  ...
  ```

**验收**

- 未点亮方块不再执行服务端生成逻辑。
- 点亮后动画仍正常。

---

### T15 reload 预解析 `spawn.nbt`（P3-8）

**文件**

- `data/RespawnRuleReloadListener.java`

**实现思路**

- 如果 `spawn.nbt` 非空，尝试 `TagParser.parseTag(...)`。
- 解析失败：警告并跳过该条目。

**验收**

- 非法 NBT 在 `/reload` 时被拒绝。

---

### T16 渲染兜底非法注册对象（P3-9）

**文件**

- `client/render/BossRespawnerBlockEntityRenderer.java`
- `blockentity/BossRespawnerBlockEntity.java`

**实现思路**

- 与 T6 一致，使用缓存。
- 渲染器获取 `Item` / `EntityType` 失败时直接返回，不抛异常。

**验收**

- 无效 ID 不会导致渲染崩溃。

---

### T17 右键激活触发 GameEvent（P3-10）

**文件**

- `block/BossRespawnerBlock.java`

**实现思路**

- 激活成功后：
  ```java
  level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
  ```

**验收**

- 与灾变行为一致，能被 sculk 等监听。

---

### T18 清理客户端调试日志（P3-11）

**文件**

- `UniversalBossRespawnerClient.java`

**实现思路**

- 删除 `HELLO FROM CLIENT SETUP`、用户名日志。

**验收**

- 客户端启动无调试输出。

---

## 五、文档同步修改清单

- `docs/plans/2026-09-05-universal-boss-respawner-design.md`
- `docs/cataclysm-boss-respawner-comparison.md`
- `docs/code-review-2026-09-05.md` 标注已修复项
- `README.md`
- 示例 JSON：`src/main/resources/data/boss_respawner/boss_respawner/entries/minecraft/warden.json`
- `src/main/resources/assets/boss_respawner/lang/en_us.json`
- `src/main/resources/assets/boss_respawner/lang/zh_cn.json`

---

## 六、执行顺序建议

1. 先做设计/数据层删减（T2、T5、T12、T13、T15、T8）。
2. 再做核心行为修复（T4、T7、T9、T10、T11、T14）。
3. 再做性能与安全优化（T1、T3、T6、T16）。
4. 最后文档与翻译同步（T17、T18、文档）。
