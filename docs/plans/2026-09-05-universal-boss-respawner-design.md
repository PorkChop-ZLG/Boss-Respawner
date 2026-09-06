# 通用 Boss 重生笼（Universal Boss Respawner）设计方案

**版本：** v1.0（设计稿）
**适用环境：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21 / ModDevGradle
**Mod ID：** `boss_respawner`
**目标目录：** `D:\Minecraft\Boss-Respawner`
**状态：** 待评审与批准

---

## 0. 设计目标与非目标

### 目标
1. 做成独立、数据驱动的“Boss/生物重生笼”机制模组。
2. 不硬编码任何具体实体、物品、模组，全部通过 JSON 配置。
3. 默认“零配置零副作用”：不装任何 JSON 时不干扰任何生物。
4. 本阶段只实现 1 个方块 + 1 个 BlockItem + 1 个方块实体。
5. 为未来“普通 / 精英 / Boss”多套方块预留扩展点。

### 非目标（本阶段不做）
- 不实现多套方块/物品。
- 不建立专门 Creative Tab。
- 不写死 Cataclysm Boss 的兼容代码（只做可选、反射式或配置式兼容）。
- 不做 GUI、进度、成就、多语言 UI。
- 不实现“玩家主动放置空笼并手动指定要复活生物”的交互（后续可扩展）。

---

## 1. 总体架构

### 1.1 分层原则

```
┌────────────────────────────────────────────────────────────┐
│ 数据层：JSON 数据包 → RespawnEntry 记录 → RespawnRuleManager │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│ 游戏逻辑层：死亡事件 → 位置解析/安全放置 → 方块实体 → 生成执行 │
│ （只依赖 Registry ID，不依赖具体实体类）                     │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│ 客户端表现层：模型/贴图/动画/BlockEntityRenderer/ItemRenderer │
│ （仅 Dist.CLIENT 加载）                                      │
└────────────────────────────────────────────────────────────┘
```

### 1.2 模块划分

| 模块 | 职责 | 关键类 |
|---|---|---|
| 注册 | 方块、方块实体、物品 | `init/ModBlocks`、`init/ModBlockEntities`、`init/ModItems` |
| 方块 | 方块属性、状态、右键 | `block/BossRespawnerBlock` |
| 方块实体 | 数据保存、tick、生成尝试 | `blockentity/BossRespawnerBlockEntity` |
| 数据驱动 | JSON 读取、校验、缓存 | `data/RespawnEntry`、`data/RespawnRuleManager`、`data/RespawnRuleReloadListener` |
| 放置 | 死亡位置/HomePos 解析、安全找位 | `placement/PlacementResolver`、`placement/HomePosLocator` |
| 生成 | 创建实体、NBT、finalizeSpawn | `spawn/RespawnExecutor` |
| 事件 | 死亡监听、数据包重载监听 | `event/LivingDeathHandler`、`event/DataPackHandler` |
| 客户端 | 模型、动画、方块实体渲染、物品手持渲染 | `client/model/*`、`client/render/*` |
| 配置 | TOML 总开关/全局兼容设置 | `Config` |

### 1.3 数据驱动与游戏逻辑边界

- **数据驱动边界：** JSON 决定“哪种生物死亡后生成笼、用哪个物品激活、在哪生成、生成什么、生成条件”。
- **游戏逻辑边界：** Java 只实现通用机制；读取 Registry 中的 `ResourceLocation` / `EntityType<?>` / `Item`。
- **重要原则：** 方块被放置时，会把该条目解析后的“运行时快照”写入方块实体 NBT。此后即使 JSON 被删除或修改，已存在的重生笼仍按快照工作；只有新放置的笼会使用新 JSON。
- **绝对禁止：** 在 Java 代码中 `instanceof CataclysmBoss` 或引用具体 Boss 类名来完成核心逻辑。

### 1.4 建议包结构 / 文件树

```
D:\Minecraft\Boss-Respawner
└── src/main
    ├── java/com/zonlong/bossrespawner
    │   ├── UniversalBossRespawner.java
    │   ├── UniversalBossRespawnerClient.java
    │   ├── Config.java
    │   ├── init/
    │   │   ├── ModBlocks.java
    │   │   ├── ModBlockEntities.java
    │   │   └── ModItems.java
    │   ├── block/
    │   │   └── BossRespawnerBlock.java
    │   ├── blockentity/
    │   │   └── BossRespawnerBlockEntity.java
    │   ├── data/
    │   │   ├── RespawnEntry.java
    │   │   ├── ActivationRule.java
    │   │   ├── PlacementRule.java
    │   │   ├── DeathRule.java
    │   │   ├── SpawnRule.java
    │   │   ├── VisualRule.java
    │   │   ├── DuplicateRule.java
    │   │   ├── RespawnRuleManager.java
    │   │   └── RespawnRuleReloadListener.java
    │   ├── placement/
    │   │   ├── PlacementResolver.java
    │   │   └── HomePosLocator.java
    │   ├── spawn/
    │   │   └── RespawnExecutor.java
    │   ├── event/
    │   │   ├── LivingDeathHandler.java
    │   │   └── DataPackHandler.java
    │   └── client/
    │       ├── model/BossRespawnerModel.java
    │       ├── model/BossRespawnerAnimations.java
    │       ├── render/BossRespawnerBlockEntityRenderer.java
    │       ├── render/BossRespawnerItemRenderer.java
    │       └── ClientRegister.java
    └── resources
        ├── assets/boss_respawner
        │   ├── blockstates/boss_respawner.json
        │   ├── models/item/boss_respawner.json
        │   ├── models/block/boss_respawner.json   // 备用/粒子 fallback
        │   ├── textures/block/boss_respawner.png  // 需授权后移植
        │   └── lang/en_us.json, zh_cn.json
        ├── data
        │   └── <datapack-namespace>/boss_respawner/entries/*.json
        └── META-INF/neoforge.mods.toml
```

> `src/main/resources/data` 下默认不放入任何条目，保证“无 JSON 零副作用”。

---

## 2. 数据驱动 JSON 设计

### 2.1 目录与命名

- 监听目录：`data/<namespace>/boss_respawner/entries/`
- 文件后缀：`.json`
- 监听器会递归加载该目录下所有 `.json`。
- 文件命名建议：
  - 单个实体：`<entity_namespace>/<entity_path>.json`
  - 示例：`data/my_pack/boss_respawner/entries/minecraft/warden.json`
  - 简单场景也可直接：`warden.json`
- 该文件的 ResourceLocation 只作为“条目 ID”，**不要求与实体 ID 一致**。
- `/reload` 会重新加载全部 JSON；不合法文件单独报错并跳过，不影响其它条目。

### 2.2 顶层 JSON 结构（推荐）

```jsonc
{
  // 顶层控制
  "schema_version": 1,            // 当前固定为 1
  "enabled": true,                // 总开关
  "priority": 0,                  // 数字越大越优先（解决多条目匹配同一实体）
  "entity": "minecraft:warden",   // 可以是字符串或字符串数组

  // 激活重生笼
  "activation": {
    "item": "minecraft:echo_shard",
    "amount": 1,
    "consume": true
  },

  // 重生笼生成位置
  "placement": {
    "mode": "death_or_home",      // death | home | death_or_home
    "offset": [0, 1, 0],          // 对最终位置的手动偏移
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": true,
    "avoid_blocks": ["cataclysm:boss_respawner"]
  },

  // 死亡触发条件
  "death": {
    "player_kill_only": false,
    "dimensions": [],             // 空 = 所有维度
    "biomes": []                  // 空 = 所有生物群系
  },

  // 点亮后生成行为
  "spawn": {
    "delay_ticks": 60,
    "require_player_nearby": true,
    "player_range": 16.0,
    "allow_peaceful": false,
    "count": 1,
    "spawn_offset": [0, 1, 0],
    "nbt": "{Glowing:1b}",
    "finalize_spawn": true,
    "set_home_to_cage": false,
    "max_attempts": -1,           // -1 = 无限重试
    "retry_interval_ticks": 20
  },

  // 客户端展示（可选）
  "visual": {
    "show_entity": true,
    "show_item": true,
    "entity_scale": "auto"
  },

  // 重复生成策略
  "duplicate": {
    "mode": "keep_existing",      // keep_existing | replace_existing | allow_multiple
    "search_radius": 16
  }
}
```

### 2.3 字段说明

| JSON 路径 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `schema_version` | int | 1 | 版本校验，未来不兼容时提示 |
| `enabled` | bool | true | `false` 表示忽略该条目 |
| `priority` | int | 0 | 同一实体匹配到多条时，高者生效 |
| `entity` | string 或 string[] | 必填 | 实体 Registry ID；`["a:b","c:d"]` 表示共享同一套规则 |
| `activation.item` | string | 必填 | 物品 Registry ID |
| `activation.amount` | int | 1 | 每次激活消耗数量 |
| `activation.consume` | bool | true | 是否消耗；创造模式建议不消耗 |
| `placement.mode` | string | `death_or_home` | 见 2.4 |
| `placement.offset` | [x,y,z] | `[0,0,0]` | 最终坐标手动偏移 |
| `placement.search_down` | int | 32 | 向下搜索地面最大格数 |
| `placement.search_up` | int | 16 | 向上搜索最大格数 |
| `placement.horizontal_radius` | int | 4 | 垂直找不到时水平螺旋搜索半径 |
| `placement.require_ground` | bool | true | 要求下方有可站立方块 |
| `placement.avoid_fluids` | bool | true | 避免放在流体中 |
| `placement.avoid_blocks` | string[] | `[]` | 若目标/候选被这些方块占用则跳过；默认模组会内置建议 `cataclysm:boss_respawner`（见 7.4） |
| `death.player_kill_only` | bool | false | 只有玩家（含间接击杀）导致死亡才生成 |
| `death.dimensions` | string[] | `[]` | 空=全部；例如 `["minecraft:overworld"]` |
| `death.biomes` | string[] | `[]` | 空=全部；可选过滤 |
| `spawn.delay_ticks` | int | 60 | 点亮后等待 tick 再尝试生成 |
| `spawn.require_player_nearby` | bool | true | 生成时是否需要附近玩家 |
| `spawn.player_range` | double | 16.0 | 玩家检测范围 |
| `spawn.allow_peaceful` | bool | false | 和平难度是否允许生成 |
| `spawn.count` | int | 1 | 一次生成数量（用于群体生物/子实体） |
| `spawn.spawn_offset` | [x,y,z] | `[0,1,0]` | 相对方块位置生成偏移 |
| `spawn.nbt` | SNBT string | 空 | 生成实体时写入的额外 NBT；例如 `"{Health:100f,Glowing:1b}"` |
| `spawn.finalize_spawn` | bool | true | 对 `Mob` 调用 `finalizeSpawn` |
| `spawn.set_home_to_cage` | bool | false | 尽量把重生点写回该实体的“家”概念（见 2.5） |
| `spawn.max_attempts` | int | -1 | 生成失败最大尝试次数；-1=无限 |
| `spawn.retry_interval_ticks` | int | 20 | 失败后重试间隔 |
| `visual.show_entity` | bool | true | 方块上是否渲染幽灵生物预览 |
| `visual.show_item` | bool | true | 方块上是否渲染所需钥匙物品 |
| `visual.entity_scale` | string/double | `auto` | 预览缩放；`auto` 按生物体积自动缩放 |
| `duplicate.mode` | string | `keep_existing` | 死亡后若附近已有同实体笼时：保留旧笼 / 替换 / 允许多个 |
| `duplicate.search_radius` | int | 16 | 重复检查半径 |

### 2.4 生成位置规则

| `placement.mode` | 行为 |
|---|---|
| `death` | 使用实体死亡位置 + `offset` |
| `home` | 必须解析到 HomePos/老巢，否则本次不生成 |
| `death_or_home` | 先尝试解析 HomePos，失败则退回死亡位置 |

- `HomePosLocator` 的解析优先级：
  1. 原版 `Mob` 的“限制活动区域”（restrict center，若存在且有效）。
  2. 可选的 Cataclysm `IHomeEntity` 反射兼容（见 2.5）。
  3. 无 HomePos 概念时返回空。
- 对普通生物（如僵尸、监守者），通常没有 HomePos，因此默认 `death_or_home` 会退回死亡点。

### 2.5 通用 HomePos 与 Cataclysm 兼容

- **不引入强依赖：** 不 `import` Cataclysm 任何类。
- 采用 **反射 + 类名探测**：
  ```java
  // 仅示意；实际放在 HomePosLocator 中并捕获所有异常
  if (Class.forName("com.github.L_Ender.cataclysm.entity.etc.IHomeEntity")
          .isInstance(entity)) {
      Object home = entity.getClass().getMethod("getHomePos").invoke(entity);
      if (home instanceof GlobalPos gp) return gp;
  }
  ```
- 该代码只在 Cataclysm 存在时生效；Cataclysm 不存在时 `Class.forName` 抛错并安全跳过。
- `spawn.set_home_to_cage` 开启时，也通过类似反射调用 `setHomePos(GlobalPos)`，让生成的 Cataclysm Boss 把重生笼位置作为新家。
- **默认不启用**，也不内置 Cataclysm 条目。

### 2.6 多条目冲突与覆盖

| 情况 | 规则 |
|---|---|
| 同一个文件路径出现在多个数据包 | 使用原版数据包覆盖规则：高优先级数据包覆盖低优先级 |
| 不同文件但匹配同一个 `entity` | 按 `priority` 从高到低；相同则按条目 ID 字典序，仅保留第一个生效，并 `LOGGER.warn` |
| `enabled=false` | 该条目直接忽略；若用于覆盖，可把同路径文件设为 `enabled=false` |
| 实体 ID 在当前环境不存在 | 跳过该条目并 `warn`，不崩溃 |
| JSON 字段错误/类型错误 | 仅跳过该文件，记录文件名和原因，不阻止 `/reload` |

### 2.7 完整 JSON 示例（监守者）

文件：`data/my_dungeon_pack/boss_respawner/entries/minecraft/warden.json`

```json
{
  "schema_version": 1,
  "enabled": true,
  "entity": "minecraft:warden",
  "activation": {
    "item": "minecraft:echo_shard",
    "amount": 1,
    "consume": true
  },
  "placement": {
    "mode": "death_or_home",
    "offset": [0, 1, 0],
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": true,
    "avoid_blocks": ["cataclysm:boss_respawner"]
  },
  "death": {
    "player_kill_only": true,
    "dimensions": ["minecraft:overworld", "minecraft:deep_dark"],
    "biomes": []
  },
  "spawn": {
    "delay_ticks": 80,
    "require_player_nearby": true,
    "player_range": 24.0,
    "allow_peaceful": false,
    "count": 1,
    "spawn_offset": [0, 2, 0],
    "nbt": "{Glowing:1b}",
    "finalize_spawn": true,
    "set_home_to_cage": false,
    "max_attempts": -1,
    "retry_interval_ticks": 20
  },
  "visual": {
    "show_entity": true,
    "show_item": true,
    "entity_scale": "auto"
  },
  "duplicate": {
    "mode": "keep_existing",
    "search_radius": 24
  }
}
```

> 注意：`dimensions` 中 `"minecraft:deep_dark` 不是维度 ID，仅为示例占位，真实使用时请填写实际维度。

---

## 3. 方块与方块实体设计

### 3.1 方块

| 项目 | 建议值 |
|---|---|
| 方块 ID | `boss_respawner:boss_respawner` |
| BlockItem ID | `boss_respawner:boss_respawner` |
| 硬度/爆炸抗性 | `strength(-1.0F, 3600000.0F)`（不可破坏，类似灾变） |
| 战利品表 | `noLootTable()`，无掉落 |
| 声音 | `SoundType.STONE` |
| 地图颜色 | `MapColor.METAL` |
| 方块状态 | `lit`（BooleanProperty，默认 false） |
| 碰撞 | 默认完整方块碰撞（若不希望挡路可加 `noCollission()`，本设计暂不加） |
| 渲染形状 | `RenderShape.ENTITYBLOCK_ANIMATED` |
| 红石/含水 | 不需要；不实现 `waterlogged` |

### 3.2 方块实体 NBT 设计

`BossRespawnerBlockEntity` 保存以下数据（均写入 NBT，以便跨存档/重启）：

```text
{
  EntityType: "minecraft:warden",       // 要生成的实体
  KeyItem: { id: "minecraft:echo_shard", count: 1 },
  SpawnRule: {
    DelayTicks: 80,
    RequirePlayerNearby: 1b,
    PlayerRange: 24.0d,
    AllowPeaceful: 0b,
    Count: 1,
    SpawnOffset: [0, 2, 0],
    Nbt: "{Glowing:1b}",
    FinalizeSpawn: 1b,
    SetHomeToCage: 0b,
    MaxAttempts: -1,
    RetryIntervalTicks: 20
  },
  Ticks: 0,
  Attempts: 0,
  SourcePos: [x, y, z]                  // 可选：用于调试/重复检查
}
```

### 3.3 方块实体 tick 逻辑

```
每个 tick：
  tickCount++

  if 服务端 and 方块状态 lit == true:
      if 难度 == PEACEFUL and !allowPeaceful: return
      if requirePlayerNearby and 范围内无玩家: return

      attemptTicks++
      if attemptTicks >= delayTicks:
          attemptTicks = 0
          if RespawnExecutor.trySpawn(level, pos, this):
              level.destroyBlock(pos, false)   // 生成成功，销毁重生笼
              markRemoved / spawned = true
          else:
              attempts++
              if maxAttempts >= 0 and attempts >= maxAttempts:
                  // 可选：保持 lit，但停止尝试 / 记录日志
                  disabledByMaxAttempts = true
```

### 3.4 右键交互逻辑

```
useItemOn(stack, state, level, pos, player, hand):
  如果 state.lit == true:
      return PASS

  仅服务端继续：
    获取 BE
    如果 BE 未初始化或 keyItem 为空：return FAIL
    如果 stack.item != keyItem.item：return PASS  // 避免吞掉其他物品交互
    如果 stack.count < amount：return FAIL

    // 消耗
    if !player.instabuild and activation.consume:
        stack.shrink(amount)

    // 点亮 + 同步 + 动画
    level.setBlock(pos, state.setValue(LIT, true), 2|3)
    level.blockEvent(pos, block, 1, 0)   // 客户端开始开启动画
    level.gameEvent(GameEvent.BLOCK_CHANGE, pos, ...)
    return SUCCESS
```

### 3.5 数据驱动配置如何写入方块实体

1. `LivingDeathHandler` 从 `RespawnRuleManager` 拿到 `RespawnEntry`。
2. `PlacementResolver` 找到安全 `BlockPos`。
3. `serverLevel.setBlock(pos, ModBlocks.BOSS_RESPAWNER.get().defaultBlockState(), 2)`。
4. `getBlockEntity(pos)` 强转为 `BossRespawnerBlockEntity`。
5. 调用 `be.setRuntimeEntry(entityId, activationItem, amount, spawnRule, duplicateRule, visual)`。
6. `be.setChanged()` + 同步。

### 3.6 如何兼容任意模组实体

- 方块实体只存实体 Registry ID 字符串，不存 `EntityType<?>` 对象引用（NBT 只保存字符串）。
- 生成时通过 `BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(id))` 重新解析。
- 若实体类型在生成时已不存在/未注册，则生成失败并保持点亮等待重试或记日志。

---

## 4. 死亡事件与生成逻辑

### 4.1 使用的事件

- 主监听：`LivingDeathEvent`（NeoForge `NeoForge.EVENT_BUS`）
- 优先级：`EventPriority.LOWEST`
- 原因：先让其它模组有机会取消死亡；若事件已取消则跳过，避免“假死”也放笼。

```java
@SubscribeEvent(priority = EventPriority.LOWEST)
public static void onLivingDeath(LivingDeathEvent event) {
    if (event.isCanceled()) return;
    LivingEntity entity = event.getEntity();
    if (entity.level().isClientSide) return;

    RespawnRuleManager.INSTANCE
        .findEntry(entity.getType())
        .filter(entry -> matchesDeathCondition(entry, entity, event.getSource()))
        .ifPresent(entry -> RespawnCagePlacer.place(serverLevel, entity, entry));
}
```

### 4.2 避免重复生成

| 场景 | 方案 |
|---|---|
| 同一实体只死亡一次 | 事件只触发一次；不需要额外去重 |
| 同一实体类型被多条 JSON 匹配 | 数据层已按 `priority` 去重 |
| 死亡点附近已有同实体重生笼 | 按 `duplicate.mode`：`keep_existing` 默认跳过 |
| 子实体/分身也符合条目 | 由 JSON 作者控制：不要给子实体配同一条目；可在未来增加 `blacklisted_entity` 字段 |
| 多阶段 Boss “假死” | 默认按真实死亡事件；若某 Boss 死亡后不真正移除实体，需要 JSON 作者或模组侧特殊处理（见 4.5） |

### 4.3 安全放置算法

```
findSafePos(serverLevel, origin, entry.placement):
  candidate = origin.offset(entry.placement.offset)
  1. 若 chunk 未加载：返回空
  2. 垂直向下扫描 search_down：
      对于每个 y 从 candidate.y 向下：
        如果下方方块 isFaceSturdy(UP) 且当前位置可替换/空气
        且（avoidFluids == false 或当前位置不是流体）
        且（requireGround == false 或满足）
        则接受
  3. 若失败，从 candidate.y 向上扫描 search_up
  4. 若仍失败，在 horizontal_radius 内做螺旋水平搜索
  5. 最终检查 avoid_blocks：若目标方块或下方属于避免列表则跳过
  6. 返回 BlockPos
```

- 若最终找不到安全点，本次死亡不生成笼，并 `LOGGER.info`。
- 放置时使用 `serverLevel.setBlock(pos, state, 2)`；随后立刻写方块实体。

### 4.4 与 Cataclysm 原版重生笼的关系（刻意允许共存）

- **设计决策：同一个 Boss 可以同时存在两套 Boss 重生笼，这是刻意设计，不是 Bug。**
- 默认不内置 Cataclysm 条目，避免未配置时自动启用。
- 但如果数据包作者显式配置了 Cataclysm Boss，本模组会按配置生成自己的重生笼；此时该 Boss 可能同时拥有：
  - Cataclysm 原版重生笼；
  - 本模组重生笼。
- 两套笼可共存，玩家可分别使用；是否配置由数据包作者决定。
- 数据层支持 `placement.avoid_blocks`，建议默认值包含 `"cataclysm:boss_respawner"`。
- 模组 TOML 提供全局 `foreignCageBlockIds`，它仅用于“不要把本模组笼直接放在其它模组的笼/祭坛方块上”，**不阻止**在附近另找位置放置第二个笼。
- 不使用任何 Cataclysm 类，只做 Registry ID 字符串比较。

### 4.5 模组 Boss 特殊死亡流程 / 多阶段 / 子实体

| 问题 | 应对策略 |
|---|---|
| Boss 死亡动画后才真正结算 | 若只在 `LivingDeathEvent` 放置，可能过早；可后续增加 `delay_ticks_after_death` 或监听 `EntityRemovedEvent` 扩展 |
| 多阶段 Boss（一阶段“死亡”后进入二阶段） | 该“死亡”通常不是最终移除；事件可能被取消，或实体并未真正死亡；配合 JSON 作者只配最终形态 |
| 子实体/召唤物 | 不自动过滤，JSON 作者可通过不配置子实体 ID 来避免 |
| 击杀判定 | `player_kill_only` 支持 `DamageSource.getEntity()` / `getIndirectEntity()` 任一为玩家 |
| 实体尺寸很大 | 放置/生成时读取 `getBbWidth/getBbHeight`，`spawn_offset` 可手动调高；默认 `[0,1,0]` |
| 实体只能在其结构维度生成 | 用 `death.dimensions` 限制；如需“只能在家里生成”，用 `placement.mode=home` |

---

## 5. 美术资源移植方案

### 5.1 方案比较

| 维度 | 方案 A：引入 Lionfish API | 方案 B：移植到 NeoForge 原生渲染（推荐） |
|---|---|---|
| 视觉效果还原度 | 高，几乎 1:1 | 中高；单方块简单模型可做到接近 1:1 |
| 开发量 | 低（复制/调整模型与动画类） | 中（把 `AdvancedModelBox` 转 `ModelPart`，把动画转 `AnimationDefinition`） |
| 外部依赖 | 需要 Lionfish API | 无 |
| 独立模组定位 | 仍算轻依赖库，但不是 Cataclysm 附属 | 更彻底独立 |
| 后续扩展多套方块 | 可复用 Lionfish | 可复用 Vanilla 模型体系 |
| 风险 | Lionfish 仍可能 API 变动；服务器若未装会加载失败 | 需手动处理 UV/mirror/插值差异，但工作量有限 |

**已确认采用方案 B（不引入 Lionfish API）。**
理由：
1. 本模组核心价值是“通用 + 独立”，少一个运行库更符合定位。
2. 重生笼只是一个方块实体，模型只有 8 个部件、一段短动画，完全可以用 Vanilla `ModelPart` + `AnimationDefinition` 重写。
3. 避免 Lionfish 的客户端/server 全量依赖和版本耦合。
4. 如果后续要移植 Cataclysm 大型实体动画，再单独评估引入 Lionfish 也不迟；架构上把客户端渲染隔离好即可。

> 美术资源授权状态：**已确认全部获得 Cataclysm 作者授权，可以使用并移植。** 但代码仍按“参考后重写”的方式处理，不直接照搬类实现。

### 5.2 需要从 Cataclysm 复制/参考的文件（方案 B）

| Cataclysm 文件 | 用途 | 本模组处理 |
|---|---|---|
| `client/model/block/Boss_Respawn_Spawner_Model.java` | 模型部件结构 | 重写为 Vanilla `ModelPart`，不直接复制类 |
| `client/animation/Boss_Respawn_Spawner_Animation.java` | 开启动画关键帧 | 转为 Vanilla `AnimationDefinition`，或手写插值 |
| `client/render/blockentity/Boss_Respawn_Spawn_Renderer.java` | 方块实体渲染 | 重写 `BlockEntityRenderer` |
| `client/render/CMItemstackRenderer.java`（仅重生笼部分） | 手持/物品栏模型渲染 | 新建独立 `BossRespawnerItemRenderer` |
| `textures/block/boss_respawner.png` | 贴图 | 获得授权后复制到 `assets/boss_respawner/textures/block/` |
| `blockstates/boss_respawner.json` | 方块状态 | 重写为 `boss_respawner:block/boss_respawner` |
| `models/item/boss_respawner.json` | 物品模型 | 改为 `builtin/entity` + 自定义渲染器 |
| 各语言文件中的重生笼名称 | 本地化 | 重写 en_us/zh_cn |

> **不要复制** `Boss_Respawn_Spawner_Block.java` / `Block_Entity.java` 的实现代码，只作为行为参考。

### 5.3 资源与代码存放结构（方案 B）

```
src/main/java/com/zonlong/bossrespawner/client
├── model
│   ├── BossRespawnerModel.java          // Vanilla ModelPart 树
│   └── BossRespawnerAnimations.java     // Vanilla AnimationDefinition
├── render
│   ├── BossRespawnerBlockEntityRenderer.java
│   └── BossRespawnerItemRenderer.java
└── ClientRegister.java                  // 注册 BER / IClientItemExtensions

src/main/resources/assets/boss_respawner
├── blockstates/boss_respawner.json
├── models/item/boss_respawner.json
├── models/block/boss_respawner.json
├── textures/block/boss_respawner.png
└── lang/...
```

### 5.4 授权风险与替代方案

- **风险点：** Cataclysm 仓库没有明确开放授权文件；`boss_respawner.png` 以及 Blockbench 模型/动画属于 Cataclysm 作者作品。未获授权前直接复制到独立模组中可能构成侵权。
- **必须做的事：** 联系 Cataclysm 作者（L_Ender）获得美术资源再分发/修改许可，并在模组 CREDITS 中署名。
- **无授权替代方案：**
  1. 自己用 Blockbench 做一个“风格相似但不直接复制”的重生笼模型/贴图。
  2. 使用原版/CC0 素材组合一个通用“基座 + 锁链 + 灵魂火焰”模型。
  3. 先使用临时占位贴图（如原版 `spawner` 纹理）开发逻辑，待授权后再替换美术。

---

## 6. 注册与物品栏策略

### 6.1 注册 ID

| 注册项 | ID |
|---|---|
| Block | `boss_respawner:boss_respawner` |
| BlockEntityType | `boss_respawner:boss_respawner` |
| BlockItem | `boss_respawner:boss_respawner` |

- 使用 NeoForge `DeferredRegister.Blocks` / `DeferredRegister.Items` / `DeferredRegister<BlockEntityType<?>>`。

### 6.2 物品栏策略

- 本阶段**不创建专属 Creative Tab**。
- **已确认：加入原版“刷怪蛋”Creative Tab**，即 `CreativeModeTabs.SPAWN_EGGS`。
- 获取方式：
  - 创造模式“刷怪蛋”标签页直接可见/可取。
  - 管理员/测试：`/give @s boss_respawner:boss_respawner`
  - 整合包作者：通过配方、Loot、任务等自行发放。

### 6.3 物品属性

```java
new Item.Properties()
    .fireResistant()
    .rarity(Rarity.EPIC)
```

- `fireResistant()`：避免岩浆/火焰意外销毁。
- `Rarity.EPIC`：与灾变原版一致，提示这是机制类物品。
- 不设置堆叠上限之外的额外组件；BlockItem 默认 64。

---

## 7. 配置与兼容性

### 7.1 TOML 与 JSON 分工

| 配置层 | 内容 | 粒度 |
|---|---|---|
| TOML（common） | 全局总开关、日志、全局兼容黑名单、全局安全默认值 | 模组级 |
| JSON（数据包） | 每个目标生物的行为规则 | 条目级/数据包级 |

### 7.2 建议 TOML 配置

```toml
[general]
# 总开关；false 时完全禁用所有放置/生成逻辑
enableMod = true
# 是否打印放置/跳过日志
logPlacement = true

[compat]
# 视为“其它模组的重生笼/祭坛”的方块 ID；放置前若附近有这些方块则跳过
foreignCageBlockIds = ["cataclysm:boss_respawner"]

[limits]
# 每个已加载区块内最多同时存在的本模组重生笼数量；-1 = 不限
maxCagesPerChunk = -1
```

### 7.3 “默认不干扰任何生物”的保证

- 最终发布版：模组 JAR 的 `data` 目录**不内置任何启用条目**。
- 当前开发阶段：为便于验收，**临时在 `src/main/resources/data/boss_respawner/boss_respawner/entries/minecraft/warden.json` 放置一个启用的监守者示例条目**。
- 发布前清理任务：将该示例条目移除，或改为 `"enabled": false`，以保证“不装任何 JSON 默认不干扰任何生物”。
- JSON 管理器为空时，死亡事件直接返回。
- 即使玩家只装本模组，不装任何数据包，也不会有任何 Boss 被额外放置重生笼。

### 7.4 对 Cataclysm / 其它模组的共存策略

- 不引用 Cataclysm/Lionfish 的类（渲染方案 B 下）。
- 只按 Registry ID 匹配实体；没装对应模组时该 ID 不存在，条目被跳过。
- **刻意允许共存：** 如果数据包作者显式配置了 Cataclysm Boss，同一 Boss 可能同时存在 Cataclysm 原版重生笼和本模组重生笼；这是设计选择，不是冲突/Bug。
- `foreignCageBlockIds` 默认包含 `cataclysm:boss_respawner`，仅用于避免把本模组笼直接放在 Cataclysm 重生笼/祭坛所在方块上。
- 本模组不会自动为 Cataclysm Boss 生成条目；数据包作者必须显式配置才会启用。

### 7.5 对没有 HomePos 的普通生物

- 默认 `placement.mode = death_or_home`：解析不到 HomePos 就使用死亡点。
- 普通生物没有“老巢”也能工作。
- 想完全只在死亡点生成，用 `placement.mode = death`。

---

## 8. 实施步骤

### 阶段 0：项目清理与基础注册

**目标：** 清除 MDK 示例内容，建立干净基础。

**新增/修改：**
- 修改：`UniversalBossRespawner.java`（删除示例方块/物品/Tab）
- 修改：`Config.java`（替换为真实 TOML 配置）
- 修改：`UniversalBossRespawnerClient.java`
- 新增：`init/ModBlocks.java`、`init/ModItems.java`、`init/ModBlockEntities.java`
- 修改：`neoforge.mods.toml`、`en_us.json`、新增 `zh_cn.json`

**验收标准：**
- `runClient` 正常启动。
- `/give @s boss_respawner:boss_respawner` 可获得方块物品。
- 手动放置后能看见方块（可先用临时占位模型）。
- 无示例 Tab、无示例物品日志。

**风险点：**
- DeferredRegister 初始化顺序；BlockEntity 与 Block 的绑定。
- 若方块完全隐藏，需用命令验证。

---

### 阶段 1：数据加载层

**目标：** 实现 JSON 读取、校验、缓存、`/reload` 支持。

**新增：**
- `data/RespawnEntry.java` 及 `ActivationRule`、`PlacementRule`、`DeathRule`、`SpawnRule`、`VisualRule`、`DuplicateRule`
- `data/RespawnRuleManager.java`
- `data/RespawnRuleReloadListener.java`
- `event/DataPackHandler.java`

**修改：**
- 主类注册 `AddReloadListenerEvent`

**验收标准：**
- 放入一个测试 JSON，启动后日志显示“Loaded N respawn entries”。
- `/reload` 后修改 JSON 可立即生效。
- 非法 JSON / 未知实体 ID 只警告不崩溃。

**风险点：**
- JSON Codec 复杂字段解析；建议先用手写 Gson + 逐字段校验，稳定后再考虑 Codec。
- 数据包事件注册时机。

---

### 阶段 2：方块 / 方块实体最小可用版

**目标：** 不依赖美术，先跑通“放置 → 右键 → 计时 → 生成实体”的核心循环。

**新增：**
- `block/BossRespawnerBlock.java`
- `blockentity/BossRespawnerBlockEntity.java`
- `spawn/RespawnExecutor.java`
- 临时 `blockstates` / `model`（可先引用原版 spawner 纹理占位）

**修改：**
- `init/ModBlockEntities.java`
- `init/ModItems.java`

**验收标准：**
- 使用 `/setblock` 或命令放置方块。
- 通过命令/测试 NBT 写入 `EntityType`、`KeyItem`、`SpawnRule`。
- 手持正确物品右键后 `lit=true`，等待延迟后成功生成对应实体并销毁方块。

**风险点：**
- 方块实体 tick 在客户端/服务端双端运行，必须用 `!level.isClientSide` 守卫生成逻辑。
- NBT 读写与 `HolderLookup.Provider` API 变更。

---

### 阶段 3：死亡事件接入

**目标：** 打通“生物死亡 → 安全放置笼 → 方块实体写入配置”。

**新增：**
- `event/LivingDeathHandler.java`
- `placement/PlacementResolver.java`
- `placement/HomePosLocator.java`

**验收标准：**
- 用测试 JSON 配置 `minecraft:zombie` 或 `minecraft:warden`，击杀后正确出现重生笼。
- 岩浆、水中、墙内、半空中等位置不会生成危险笼。
- `duplicate.mode=keep_existing` 时不会堆叠多个笼。

**风险点：**
- 事件取消顺序。
- 安全放置算法可能在高空中水平搜索失败。
- 与 Cataclysm 原版机制冲突；通过 TOML 黑名单验证。

---

### 阶段 4：美术资源移植（需先确认授权与方案）

**目标：** 让方块/物品具有正式外观。

**前置条件：** 确认方案 A/B，并获得 Cataclysm 美术授权（若复制原资源）。

**新增/修改（方案 B）：**
- `client/model/BossRespawnerModel.java`
- `client/model/BossRespawnerAnimations.java`
- `assets/boss_respawner/textures/block/boss_respawner.png`
- `assets/boss_respawner/models/item/boss_respawner.json`
- `assets/boss_respawner/blockstates/boss_respawner.json`

**验收标准：**
- 放置后能看到正式模型与贴图。
- 物品栏/手持显示正确模型。
- 无 missing model / missing texture。

**风险点：**
- 美术授权缺失。
- Vanilla `ModelPart` 与 Lionfish `AdvancedModelBox` 的 UV 镜像/旋转点差异。

---

### 阶段 5：动画与客户端渲染

**目标：** 开启动画、幽灵生物预览、钥匙物品展示。

**新增：**
- `client/render/BossRespawnerBlockEntityRenderer.java`
- `client/render/BossRespawnerItemRenderer.java`
- `client/ClientRegister.java`

**修改：**
- `blockentity/BossRespawnerBlockEntity.java`（增加 blockEvent 动画同步字段）
- 主类客户端扩展注册

**验收标准：**
- 右键点亮后播放开启动画。
- 方块实体上显示目标生物的幽灵预览和所需物品（若 JSON `visual` 开启）。
- 手持物品在 GUI/第一/第三人称显示正确。

**风险点：**
- 客户端与服务端动画状态不同步。
- 幽灵预览创建任意实体可能带来性能/副作用，需要缓存并限制 tick。
- `BlockEntityWithoutLevelRenderer` 注册方式在 NeoForge 1.21.1 的 API 差异。

---

### 阶段 6：测试与兼容性验证

**目标：** 全链路回归与多模组兼容。

**测试项：**
- 原版生物（僵尸/监守者）死亡生成。
- 数据包 `/reload` 前后行为。
- 重启存档后已放置笼仍可工作（NBT 快照）。
- 和平难度、无玩家、玩家过远等条件。
- 与 Cataclysm 同装时，允许同一 Boss 同时存在两套重生笼（刻意设计）；`foreignCageBlockIds` 只防止位置重叠。
- 缺少目标模组时，JSON 条目被安全跳过。
- 非法 JSON 不阻断服务器启动。

**新增：**
- `src/test` 或 `run` 下的测试数据包
- 可选 GameTest / 手动验收清单

**验收标准：**
- 上述测试全部通过。
- 服务器端无报错、无类加载异常。

**风险点：**
- 其它模组实体 finalizeSpawn 需要特殊上下文。
- 客户端模型对超大实体预览的性能。

---

## 9. 已确认决策

1. **美术方案：** 已确认使用方案 B（原生 Vanilla 渲染，不引入 Lionfish）。
2. **Cataclysm 美术授权：** 已确认全部美术资源获得授权，可以移植。
3. **默认物品栏：** 已确认加入原版“刷怪蛋”Creative Tab（`CreativeModeTabs.SPAWN_EGGS`）。
4. **默认 JSON：** 设计上最终不内置条目；当前开发阶段临时放置一个启用的监守者示例条目，发布前移除或禁用。
5. **多套重生笼共存：** 已确认同一个 Boss 可同时存在多套 Boss 重生笼（如灾变原版 + 本模组），这是刻意设计，不属于冲突或 Bug。

---

## 10. 风险登记表

| 风险 | 等级 | 缓解 |
|---|---|---|
| Cataclysm 美术资源版权 | 高 | 先授权；否则原创替代 |
| 任意实体 `finalizeSpawn` 失败 | 中 | 失败保持 lit 重试；提供 NBT/自定义 |
| 同一 Boss 存在两套重生笼 | 低 | 刻意设计；默认不自动启用其它模组条目，可显式配置或禁用 |
| HomePos 无统一标准 | 中 | `death_or_home` 默认，反射可选 |
| 方块实体 NBT 跨版本 | 低 | 存字符串 ID + 快照，向前兼容 |
| 客户端幽灵实体性能 | 低 | 缓存显示实体，限制渲染频率 |
