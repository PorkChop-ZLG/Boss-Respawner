# 通用Boss重生笼 vs 灾变Boss重生笼：详细对比

**参考版本：**
- 本模组：`boss_respawner`（Minecraft 1.21.1 / NeoForge 21.1.219）
- 灾变：`cataclysm`（Cataclysm，本地源码位于 `D:\Minecraft\Cataclysm`）

---

## 1. 总体结论

| 维度 | 灾变 | 本模组 |
|---|---|---|
| 定位 | 灾变自身 Boss 的专属机制 | 独立、通用、数据驱动的通用机制 |
| 驱动方式 | Java 硬编码 + 每个 Boss 各自调用 | JSON 数据包驱动 |
| Boss 范围 | 仅灾变内置 Boss | 任意注册实体 |
| 外部依赖 | 依赖 Cataclysm 自身 + Lionfish API 渲染 | 无强依赖；Cataclysm 仅作为可选运行时依赖 |
| 客户端渲染 | Lionfish `AdvancedEntityModel` 等 | Vanilla `ModelPart` / `AnimationDefinition` |
| 配置粒度 | 每个 Boss 一个开关 | 每个实体一条 JSON 规则 + 全局 TOML |

---

## 2. Java 类对应表

| 功能 | 灾变类 | 本模组类 |
|---|---|---|
| Boss 重生笼方块 | `com.github.L_Ender.cataclysm.blocks.Boss_Respawn_Spawner_Block` | `com.zonlong.bossrespawner.block.BossRespawnerBlock` |
| Boss 重生笼方块实体 | `com.github.L_Ender.cataclysm.blockentities.Boss_Respawn_Spawner_Block_Entity` | `com.zonlong.bossrespawner.blockentity.BossRespawnerBlockEntity` |
| 死亡后触发放置 | 各 Boss 实体的 `AfterDefeatBoss()` / `Respawner()` | `com.zonlong.bossrespawner.event.LivingDeathHandler` |
| 安全找位/放置逻辑 | 各 Boss 实体内部的 `Respawner(int x, int z, int minY, int maxY, ServerLevel)` | `com.zonlong.bossrespawner.placement.PlacementResolver` + `RespawnCagePlacer` |
| HomePos / 老巢 | `com.github.L_Ender.cataclysm.entity.etc.IHomeEntity` | `com.zonlong.bossrespawner.placement.HomePosLocator`（反射式兼容） |
| 数据规则 | Java 中 `setEntityId()` + `setTheItem()` + `CMCommonConfig` | `com.zonlong.bossrespawner.data.RespawnEntry` + `RespawnRuleManager` + `RespawnRuleReloadListener` |
| 客户端模型 | `com.github.L_Ender.cataclysm.client.model.block.Boss_Respawn_Spawner_Model` | `com.zonlong.bossrespawner.client.model.BossRespawnerModel` |
| 客户端动画 | `com.github.L_Ender.cataclysm.client.animation.Boss_Respawn_Spawner_Animation` | `com.zonlong.bossrespawner.client.model.BossRespawnerAnimations` |
| 方块实体渲染 | `com.github.L_Ender.cataclysm.client.render.blockentity.Boss_Respawn_Spawn_Renderer` | `com.zonlong.bossrespawner.client.render.BossRespawnerBlockEntityRenderer` |
| 物品手持渲染 | `com.github.L_Ender.cataclysm.client.render.CMItemstackRenderer` | `com.zonlong.bossrespawner.client.render.BossRespawnerItemRenderer` |
| 客户端注册 | `com.github.L_Ender.cataclysm.client.event.ClientSetup` | `com.zonlong.bossrespawner.client.ClientRegister` |
| 方块注册 | `com.github.L_Ender.cataclysm.init.ModBlocks` | `com.zonlong.bossrespawner.init.ModBlocks` |
| 物品注册 | `com.github.L_Ender.cataclysm.init.ModItems` | `com.zonlong.bossrespawner.init.ModItems` |
| 方块实体注册 | `com.github.L_Ender.cataclysm.init.ModTileentites` | `com.zonlong.bossrespawner.init.ModBlockEntities` |
| TOML 配置 | `com.github.L_Ender.cataclysm.config.CMCommonConfig`（按 Boss 开关） | `com.zonlong.bossrespawner.Config`（全局） |

---

## 3. 详细异同点

### 3.1 方块

#### 相同点

- 都叫做“Boss 重生笼”方块。
- 都使用 `BooleanProperty LIT`/`BlockStateProperties.LIT` 表示“未点亮/已点亮”。
- 都使用 `BaseEntityBlock`，渲染形状都是 `RenderShape.ENTITYBLOCK_ANIMATED`。
- 都是不可破坏、无战利品表。

#### 不同点

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 注册 ID | `cataclysm:boss_respawner` | `boss_respawner:boss_respawner` |
| 属性细节 | `strength(-1F, 3600000F)`、`noLootTable`、`noOcclusion` | 相同思路，`mapColor(METAL)`、`SoundType.STONE` |
| 方块实体类型 | `ModTileentites.BOSS_RESPAWNER` | `ModBlockEntities.BOSS_RESPAWNER` |
| 右键逻辑 | 方块类 `useItemOn` 直接判断 `EntityType`/`ItemStack` | 方块类 `useItemOn` 通过 BE 判断字符串 item ID |

### 3.2 方块实体数据

#### 灾变方块实体保存

```text
EntityType        -> EntityType<?>
Item              -> ItemStack
Animaitonticks    -> 点亮后 tick
spawnedBoss       -> 是否已经生成
```

#### 本模组方块实体保存

```text
EntityType         -> String（实体 ID）
KeyItemId          -> String（物品 ID）
KeyAmount          -> int
SpawnRule          -> CompoundTag 快照：
                      DelayTicks / RequirePlayerNearby / PlayerRange /
                      AllowPeaceful / Count / SpawnOffset / Nbt /
                      FinalizeSpawn / SetHomeToCage /
                      MaxAttempts / RetryIntervalTicks
LitTicks / Attempts / Spawned / Stopped
```

#### 核心差异

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 实体类型存储 | 内存中直接保存 `EntityType<?>` | 保存 Registry ID 字符串，运行时再解析 |
| 钥匙物品 | 存 `ItemStack` | 存物品 ID + 数量 |
| 生成参数 | 基本固定（附近玩家 9 格、19 tick、非和平） | 每个笼独立保存可配置参数快照 |
| 配置变更后 | 行为由该 Boss 当前代码决定 | 已放置的笼使用放置时写入的快照，不受 `/reload` 影响 |
| 失败重试 | 成功前每 tick 累加，到达 19 后重试 | 可配置 `maxAttempts` / `retryIntervalTicks` |

### 3.3 死亡触发与放置

#### 灾变

- 不是全局事件。
- 每个灾变 Boss 在自己的死亡/击败流程中调用 `AfterDefeatBoss()`。
- 在 `AfterDefeatBoss()` 内调用各自实现的 `Respawner()`：
  - 从 Boss 的 `IHomeEntity#getHomePos()` 获取老巢。
  - 从 `maxY` 向下搜索可站立地面。
  - `serverLevel.setBlock(pos, ModBlocks.BOSS_RESPAWNER.get().defaultBlockState(), 2)`。
  - 写入 `setEntityId(...)` 和 `setTheItem(...)`。
- 相关实体：
  - `Ender_Guardian_Entity`
  - `The_Harbinger_Entity`
  - `Scylla_Entity`
  - `Netherite_Monstrosity_Entity`
  - `Ancient_Remnant_Entity`

#### 本模组

- 使用全局 `LivingDeathEvent`，优先级 `LOWEST`。
- 先检查事件是否被取消。
- 从 `RespawnRuleManager` 按实体类型查 JSON 规则。
- `RespawnCagePlacer` 调用 `PlacementResolver`：
  - `death` / `home` / `death_or_home`
  - 偏移、向下/向上/水平搜索
  - 避流体、要求可站立地面
  - 合并 JSON `avoid_blocks` 与 `Config.FOREIGN_CAGE_BLOCK_IDS`
- 放置后调用 `BossRespawnerBlockEntity#setSpawnerData(...)` 写入完整快照。

#### 核心差异

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 触发点 | 每个 Boss 内部手动调用 | 通用死亡事件 |
| 目标实体匹配 | 硬编码 Java 类 | Registry ID 字符串 + JSON |
| 位置规则 | 只有 HomePos 相关搜索 | 死亡点/HomePos/偏移/多模式 |
| 防止重复 | 无通用逻辑（由结构/Boss 流程保证） | `keep_existing` / `replace_existing` / `allow_multiple` |
| 每区块限制 | 无 | `Config.MAX_CAGES_PER_CHUNK` |
| 对其他模组避让 | 不关心 | `Config.FOREIGN_CAGE_BLOCK_IDS` |

### 3.4 HomePos / 老巢

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 实现方式 | `IHomeEntity` 接口，直接 `getHomePos()` | `HomePosLocator` 反射式鸭子类型 |
| `IHomeEntity` 兼容 | 原生支持 | 通过反射调用，未安装 Cataclysm 时自动跳过 |
| 原版 Mob | 通常不依赖 | 支持 `Mob#getRestrictCenter()`，且仅当 `hasRestriction()` 为 true 才视为有效家 |
| 写入 HomePos | 直接 `setHomePos(GlobalPos)` | 反射调用 `setHomePos`，仅当 JSON `set_home_to_cage=true` |
| 缺少 HomePos | 灾变 Boss 一般都有 | 自动退回死亡点（`death_or_home`） |

### 3.5 激活逻辑

#### 相同点

- 手持正确物品右键，消耗 1 个物品（本模组可配置数量/是否消耗）。
- 把 `LIT` 设为 true。
- 发送方块事件 `blockEvent(pos, block, 1, 0)` 启动客户端动画。

#### 不同点

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 消耗 | 固定“1 个” | `activation.amount` |
| 创造模式 | 原版逻辑也会消耗 | 创造模式不消耗 |
| 物品匹配 | `stack.is(itemstack.getItem())` | `stack.is(Item)` + `stack.count >= amount` |
| 错误物品 | 仍返回 `sidedSuccess` | 返回 `PASS_TO_DEFAULT_BLOCK_INTERACTION` |

### 3.6 生成 Boss

#### 灾变

```text
entity = spawnType.create(serverLevel)
entity.setPos(方块中心)
if Mob:
    finalizeSpawn(...)
    if IHomeEntity: setHomePos(笼位置)
serverLevel.addFreshEntity(entity)
成功 -> destroyBlock(pos, false)
```

#### 本模组

```text
按 count 循环：
    entity = EntityType.create(serverLevel)
    entity.setPos(spawnOffset 后位置)
    若 JSON nbt 非空 -> entity.load(nbt)
    if Mob && finalizeSpawn: finalizeSpawn(...)
    if setHomeToCage: 反射 setHomePos
    addFreshEntity(entity)
全部成功 -> destroyBlock(pos, false)
失败 -> 保留 lit 并等待 retryIntervalTicks
```

#### 核心差异

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 生成数量 | 1 | `spawn.count` |
| 额外 NBT | 无 | `spawn.nbt`（SNBT） |
| 生成偏移 | 方块中心 | `spawn_offset` 可配置 |
| `finalizeSpawn` | 总是执行 | `spawn.finalize_spawn` 可关 |
| 是否写回家 | 只要 `IHomeEntity` 就写 | `spawn.set_home_to_cage` 可配 |
| 失败策略 | 保持点亮继续尝试 | `maxAttempts` + `retryIntervalTicks` |

### 3.7 客户端模型与渲染

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 模型基类 | Lionfish `AdvancedEntityModel` | Vanilla `HierarchicalModel<Entity>` |
| 部件 | Lionfish `AdvancedModelBox` | Vanilla `ModelPart` |
| 动画定义 | Lionfish `AdvancedAnimationDefinition` | Vanilla `AnimationDefinition` |
| 动画关键帧 | Lionfish `AdvancedKeyframe` / `AdvancedKeyframeAnimations` | Vanilla `Keyframe` / `KeyframeAnimations` |
| 方块实体渲染器 | `Boss_Respawn_Spawn_Renderer` | `BossRespawnerBlockEntityRenderer` |
| 幽灵生物预览 | `BE.getDisplayEntity()` | `BE.getDisplayEntity()`（同样缓存） |
| 钥匙物品预览 | `itemRenderer.renderStatic(...)` | 同样实现 |
| 物品手持渲染 | 集成在 `CMItemstackRenderer` | 独立 `BossRespawnerItemRenderer` |
| 动画切换 | `AnimationState` + `blockEvent` | 相同思路，但用 Vanilla `AnimationState` |

> 本模组不引入 Lionfish API，模型/动画/渲染均为 Vanilla 原生实现。

### 3.8 配置

| 项目 | 灾变 | 本模组 |
|---|---|---|
| 总开关 | 无全局文件，按 Boss `CMCommonConfig` | `Config.general.enableMod` |
| Boss 级规则 | Java 类内硬编码 + `CMCommonConfig.respawner` | JSON `RespawnEntry` |
| 日志 | 无统一日志开关 | `Config.general.logPlacement` |
| 兼容避让 | 无 | `Config.compat.foreignCageBlockIds` |
| 每区块限制 | 无 | `Config.limits.maxCagesPerChunk` |
| 数据包刷新 | 无 | `/reload` 自动重载 |

---

## 4. 机制流程图对比

### 灾变流程

```text
Boss 死亡 -> AfterDefeatBoss()
            -> Respawner(HomePos x/z, minY, maxY)
            -> 向下找地面
            -> setBlock(BOSS_RESPAWNER)
            -> BE.setEntityId(该Boss)
            -> BE.setTheItem(该Boss钥匙)
            -> 玩家右键背包物品
            -> lit=true + blockEvent
            -> tick 19 后 spawnMyBoss
            -> addFreshEntity
            -> destroyBlock
```

### 本模组流程

```text
任意实体死亡 -> LivingDeathEvent(LOWEST)
            -> RespawnRuleManager.find(entityType)
            -> 匹配 JSON 规则
            -> PlacementResolver.findPlacementPos()
            -> RespawnCagePlacer.tryPlace()
            -> setBlock(BOSS_RESPAWNER)
            -> BE.setSpawnerData(JSON 快照)
            -> 玩家右键 activation.item
            -> lit=true + blockEvent
            -> BE.tick: 按 delay_ticks 延迟
            -> RespawnExecutor 逻辑（位于 BE 内）
            -> addFreshEntity
            -> destroyBlock
```

---

## 5. 关键差异总结

1. **数据驱动 vs 硬编码**
   - 灾变只能复活它自己写死的 Boss。
   - 本模组可通过 JSON 配置任意实体，无需修改 Java。

2. **可配置性**
   - 灾变：固定 19 tick、固定 9 格玩家范围。
   - 本模组：延迟、玩家范围、和平难度、数量、NBT、偏移、重试等都可配置。

3. **依赖**
   - 灾变依赖自身 Boss 与 Lionfish API。
   - 本模组无强依赖，客户端模型使用 Vanilla 实现。

4. **兼容性**
   - 灾变只关心自己的 Boss。
   - 本模组通过 `foreignCageBlockIds`、未知实体跳过、可选依赖等方式降低对其他模组的副作用。

5. **生存圈**
   - 灾变通常每个 Boss 一个固定 HomePos。
   - 本模组支持死亡点、HomePos、死亡点+HomePos 回退，适合普通生物。

---

## 6. 同一 Boss 两套重生笼：刻意设计

- **设计决策：同一个 Boss 可以同时存在两套 Boss 重生笼，这是刻意设计，不是 Bug。**
- 若本模组 JSON 中也配置了 Cataclysm 的 Boss，Cataclysm 自己仍会生成自己的重生笼，本模组也可能生成另一个笼。
- 当前 `foreignCageBlockIds` 只用于“避免把本模组笼直接放在 Cataclysm 笼的位置”，不会阻止本模组在附近另找位置放第二个笼。
- 是否让同一 Boss 同时拥有两套重生笼，由数据包作者决定：
  - 不配置 Cataclysm Boss 的 JSON → 只有灾变原版笼；
  - 配置 Cataclysm Boss 的 JSON → 两套笼共存。
- 若整合包不希望共存，可通过不配置对应 JSON、或使用 JSON `duplicate` / 后续扩展的专属禁用规则来控制。
