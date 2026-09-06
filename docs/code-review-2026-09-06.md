# Universal Boss Respawner 代码审查问题报告（2026-09-06）

**审查对象：** `D:\Minecraft\Boss-Respawner`  
**审查方式：** 三路并行子代理代码审查  
**审查范围：**

1. 第一次代码审查报告中的问题是否已修复；
2. 第一次审查后的新增/修改是否引入新问题；
3. 旧 v1 设计残留是否与当前 v2 设计冲突。

**总体结论：**

- 第一次审查中的绝大部分 P1/P2 问题已经修复，核心链路基本正常。
- 当前代码方向正确：默认 `allow_multiple`、DebugLog、真实方块校验均已落地。
- 但仍存在若干值得处理的问题，其中 **#1 最需要优先修复**。

---

## 审查问题列表

### #1 [高] `removeExistingCages()` 可能在遍历 BlockEntity 时修改集合，存在 `ConcurrentModificationException` 风险

**文件：**

- `src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java`

**问题描述：**

```java
for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
    ...
    level.destroyBlock(cage.getBlockPos(), false);
}
```

`LevelChunk.getBlockEntities()` 返回的是 chunk 内部 BlockEntity 集合的视图。  
`level.destroyBlock(...)` 会触发 BlockEntity 移除，可能在同一遍历过程中修改底层集合，从而抛出 `ConcurrentModificationException`。

该异常会被 `LivingDeathHandler` 的 `catch (Exception)` 吞掉，最终可能表现为：

- 旧笼没有删除干净；
- 新笼没有正常放置；
- 偶发性功能失效。

**建议修复：**

先收集需要删除的坐标，循环结束后统一销毁：

```java
List<BlockPos> toDestroy = new ArrayList<>();
for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
    if (blockEntity instanceof BossRespawnerBlockEntity cage
            && entityTypeId.equals(cage.getEntityTypeId())
            && cage.getBlockPos().distSqr(center) <= radiusSq
            && level.getBlockState(cage.getBlockPos()).is(ModBlocks.BOSS_RESPAWNER.get())) {
        toDestroy.add(cage.getBlockPos());
    }
}
for (BlockPos pos : toDestroy) {
    level.destroyBlock(pos, false);
}
```

---

### #2 [中] `duplicate.mode` 未做白名单校验，未知值会被当成 `replace_existing`

**文件：**

- `src/main/java/com/zonlong/bossrespawner/data/RespawnEntry.java`
- `src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java`

**问题描述：**

`DuplicateRule.fromJson()` 只读取字符串，不校验是否为合法值。  
而 `RespawnCagePlacer` 的逻辑是：

- 不是 `allow_multiple` 就进行重复检查；
- 发现已有笼且不是 `keep_existing` 时，就执行 `removeExistingCages()`。

因此任何拼写错误，例如：

```json
"mode": "keep_existings"
```

都会被当作 `replace_existing`，导致误删已有重生笼。

**建议修复：**

在解析时做白名单校验：

```java
String mode = getString(obj, "mode", "allow_multiple");
if (!Set.of("allow_multiple", "keep_existing", "replace_existing").contains(mode)) {
    mode = "allow_multiple";
}
```

或者在 reload 阶段跳过非法条目并输出警告。

---

### #3 [中] `maxAttempts` 负值未钳制，`0` 还会被映射成旧默认值 `10`

**文件：**

- `src/main/java/com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java`
- `src/main/java/com/zonlong/bossrespawner/data/RespawnEntry.java`

**问题描述：**

当前 `normalizeMaxAttempts()`：

```java
private static int normalizeMaxAttempts(int value) {
    if (value == 0) {
        return 10;
    }
    return value;
}
```

- `0` 被映射为 `10`，与当前 v2 默认值 `20` 不一致；
- 负值原样返回，会被判定为“无限重试”，但文档未说明该语义；
- 与第一次修复计划中“`maxAttempts >= 1` 或 `-1`”的目标不一致。

**建议修复：**

明确钳制规则，例如：

```java
private static int normalizeMaxAttempts(int value) {
    if (value <= 0) {
        return value == 0 ? 10 : -1;
    }
    return value;
}
```

或者直接改为：

```java
if (value < 0) {
    return -1;
}
if (value == 0) {
    return 20;
}
return value;
```

并在文档中说明 `-1` 表示无限重试。

---

### #4 [中] 配置项显示名、常量名与 TOML 键名不一致

**文件：**

- `src/main/java/com/zonlong/bossrespawner/Config.java`
- `src/main/resources/assets/boss_respawner/lang/en_us.json`
- `src/main/resources/assets/boss_respawner/lang/zh_cn.json`
- `README.md`

**问题描述：**

当前实现：

```java
public static final ModConfigSpec.BooleanValue DEBUG_INFO = BUILDER
        .translation("boss_respawner.configuration.general.debugInfo")
        .define("general.logPlacement", false);
```

- 配置界面显示为“调试信息 / Debug Information”；
- Java 常量名为 `DEBUG_INFO`；
- 但 TOML 实际键名仍是 `general.logPlacement`。

影响：

1. 用户可能误以为配置文件应写 `debugInfo = true`；
2. 旧用户配置中如果已有 `logPlacement = true`，升级后会自动开启大量调试日志，行为变化比预期大。

**建议修复：**

- 如果允许配置迁移，将 TOML 键改为 `general.debugInfo`；
- 如果保留旧键兼容，必须在 README 和配置注释中明确说明：
  - `general.logPlacement` 现在表示“调试信息”；
  - 默认值已从 `true` 改为 `false`。

---

### #5 [中] 重复检测只扫描已加载区块，未加载区块中的旧笼会漏检

**文件：**

- `src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java`
- `README.md`

**问题描述：**

`hasExistingCage()` / `removeExistingCages()` 只通过：

```java
level.getChunkSource().getChunkNow(cx, cz)
```

获取已加载区块。

影响：

- `keep_existing`：如果旧笼在未加载区块，仍会放置新笼，产生重复；
- `replace_existing`：无法删除未加载区块中的旧笼。

这是性能优化后的取舍，但在启用 `keep_existing` / `replace_existing` 时可能与用户预期不一致。

**建议修复：**

- 在文档中明确说明“重复检测仅覆盖已加载区块”；
- 或者评估是否需要对未加载区块做受控加载/队列处理。

---

### #6 [中] DebugLog 关闭时参数仍会求值，开启后高频路径可能刷屏

**文件：**

- `src/main/java/com/zonlong/bossrespawner/DebugLog.java`
- `src/main/java/com/zonlong/bossrespawner/event/LivingDeathHandler.java`
- `src/main/java/com/zonlong/bossrespawner/placement/PlacementResolver.java`
- `src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java`

**问题描述：**

- `DebugLog.info()` 内部虽受配置开关控制，但 Java 在调用前仍会构造 `Object...` 数组并执行全部实参表达式；
- 例如 `LivingDeathHandler` 对每个实体死亡都会计算 `entityId`、`blockPosition()`，即使没有匹配规则；
- 开启 debug 后，`PlacementResolver` 会对每个被拒绝的候选点打印日志，刷怪塔/群体死亡场景会生成大量无意义日志。

**建议修复：**

- 增加 `DebugLog.isEnabled()`，在高频调用处前置判断；
- 或改为接收 `Supplier<String>`，只在启用时格式化；
- 对高频候选点日志可降低到 `LOGGER.debug` 级别。

---

### #7 [中] v2 设计文档内部矛盾：默认最大尝试次数写成 10，实现是 20

**文件：**

- `docs/universal-boss-respawner-design-v2.md`

**问题描述：**

- 第 14 行写“默认最多尝试 10 次”；
- 但同文件示例、README、代码实际默认值都是 `20`。

**建议修复：**

将第 14 行改为“默认最多尝试 20 次”。

---

### #8 [中] `docs/cataclysm-boss-respawner-comparison.md` 仍以“当前本模组”口吻描述大量已删除功能

**文件：**

- `docs/cataclysm-boss-respawner-comparison.md`

**问题描述：**

该文档仍包含以下旧设计内容：

- `HomePosLocator`
- `placement.mode` / `death / home / death_or_home`
- `visual.*`
- `spawn.set_home_to_cage`
- `Config.MAX_CAGES_PER_CHUNK`
- 旧 NBT 字段 `SetHomeToCage`、`Stopped`

这些功能在 v2 中已全部移除，但文档没有明确标注为历史内容。

**建议修复：**

- 要么将整篇对比文档更新为 v2 当前实现；
- 要么在文件顶部添加醒目提示：“本文保留 v1 对比内容，当前实现以 v2 为准；文中 HomePos / visual / 每区块限制 / set_home_to_cage 均已移除。”

---

### #9 [低] 同优先级条目冲突时仍无警告

**文件：**

- `src/main/java/com/zonlong/bossrespawner/data/RespawnRuleReloadListener.java`

**问题描述：**

`shouldReplace()` 在同优先级时静默选择字典序更小的条目，没有输出任何警告。  
多个数据包同时定义匹配同一实体的条目时，排查比较困难。

**建议修复：**

同优先级发生覆盖时输出 `LOGGER.warn`。

---

### #10 [低] 手动放置的重生笼仍是“死方块”

**文件：**

- `src/main/java/com/zonlong/bossrespawner/init/ModItems.java`
- `src/main/java/com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java`

**问题描述：**

玩家可以手动放置 `boss_respawner`，但手动放置的方块实体没有实体类型、钥匙物品数据，`matchesKeyItem()` 恒为 false，永远无法激活。

这是第一次审查中已经列出但尚未处理的问题。

**建议修复：**

- 在文档中明确说明手动放置的笼子不可用；
- 或者后续提供设置 NBT/交互方式；
- 开发阶段也可先改为“仅通过死亡机制生成”。

---

### #11 [低] `PlacementResolver` 调试日志打印 `int[]` 会显示数组地址

**文件：**

- `src/main/java/com/zonlong/bossrespawner/placement/PlacementResolver.java`

**问题描述：**

日志中直接传入 `placement.offset()` 和 `placement.spawnOffset()`，SLF4J `{}` 对数组会输出类似：

```text
[I@1a2b3c
```

无法直观看到实际坐标。

**建议修复：**

使用 `Arrays.toString(...)`。

---

### #12 [低] `LivingDeathHandler` 中 `entityId == null` 检查基本是死代码

**文件：**

- `src/main/java/com/zonlong/bossrespawner/event/LivingDeathHandler.java`

**问题描述：**

如果 `entityId == null`，`RespawnRuleManager.find()` 必然返回 `null`，前面的 `entry == null` 分支已经提前返回。

**建议修复：**

删除该分支，或把空值检查提前到方法入口统一处理。

---

### #13 [低] `search_radius` 实际被硬编码上限 32，文档未说明

**文件：**

- `src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java`
- `README.md`

**问题描述：**

```java
int r = Math.min(radius, 32);
```

用户配置 `search_radius = 64` 时实际仍只检查 32 格。  
README 只写了“最小 0”，未说明存在 32 格上限。

**建议修复：**

- 在文档中补充“实际最大检查半径为 32”；
- 或者去掉硬编码上限，/ 或改为从配置中读取。

---

### #14 [低] `resetAfterMaxAttempts()` 未显式调用 `sendBlockUpdated`

**文件：**

- `src/main/java/com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java`

**问题描述：**

该方法使用 `level.setBlock(..., 2)` 恢复未点亮状态，通常能同步客户端。  
但如果需要严格保证客户端 BlockEntity 数据同步，建议再调用一次 `sendBlockUpdated`。

**建议：**

可选优化，非阻断问题。

---

### #15 [低] 注册表解析失败时没有 negative cache

**文件：**

- `src/main/java/com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java`

**问题描述：**

`getCachedEntityType()` / `getCachedKeyItem()` 只缓存成功解析的结果。  
如果遇到非法 ID，渲染帧可能会反复尝试注册表解析。

**影响：**

普通数据包路径已由 reload 校验拦截，实际影响很小。

**建议：**

可选优化，可增加失败标记缓存。

---

### #16 [低] reload 的 `Loaded N entries` 信息日志不受 DebugLog 开关控制

**文件：**

- `src/main/java/com/zonlong/bossrespawner/data/RespawnRuleReloadListener.java`

**问题描述：**

```java
UniversalBossRespawner.LOGGER.info("Loaded {} boss respawn entries ...");
```

这是常驻 INFO 日志，不是调试日志。  
如果希望所有日志都受“调试信息”开关控制，需要改为 `DebugLog.info`。

**建议：**

- 保留为常规信息日志也可以；
- 若希望统一控制，则改为 `DebugLog.info(...)`。

---

### #17 [低] `build.gradle` 仍保留 Lionfish API 的本地运行时依赖

**文件：**

- `build.gradle`

**问题描述：**

第 126-129 行仍有：

```gradle
runtimeOnly "curse.maven:lionfish-api-1001614:8345326"
```

它只是本地开发运行时依赖，不是编译期或发布依赖，不违反 v2“不依赖 Lionfish API”的定位。

**建议：**

- 保留也可以，但建议注释说明“仅供本地跑 Cataclysm 测试”；
- 如果想彻底移除 Lionfish 痕迹，可删除该行并确认本地测试不再需要。

---

### #18 [低] 历史文档缺少“历史报告/历史执行记录”标识

**文件：**

- `docs/code-review-2026-09-05.md`
- `docs/plans/2026-09-05-bug-fix-plan.md`

**问题描述：**

- `docs/code-review-2026-09-05.md` 是第一次审查报告，虽然已带修复状态更新，但仍容易让读者误以为其中旧类/旧字段代表当前实现；
- `docs/plans/2026-09-05-bug-fix-plan.md` 是执行记录，文中大量旧字段是“待删除/已删除”上下文。

**建议修复：**

在两类文档开头增加：

```text
本文为历史审查/历史执行记录，文中旧类、旧字段均不代表当前实现，当前行为以 v2 设计文档和 README 为准。
```

---

## 建议处理顺序

| 优先级 | 问题编号 | 建议处理 |
|---|---|---|
| 高 | #1 | 修复 `removeExistingCages()` 集合遍历风险 |
| 中 | #2、#3、#4、#7、#8 | 修复非法配置处理、边界语义、文档一致性 |
| 中 | #5、#6 | 明确已加载区块限制、优化 DebugLog 开销 |
| 低 | #9-#18 | 可批量作为代码质量与文档清理项处理 |
