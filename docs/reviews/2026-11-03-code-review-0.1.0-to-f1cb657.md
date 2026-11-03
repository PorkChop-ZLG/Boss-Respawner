# Boss-Respawner 代码审查报告

**审查范围：** `0830d71^..f1cb657`，即 `0.1.0版本发布！`（0830d71，含该提交）至当前 HEAD（f1cb657），共 14 个提交。  
**参考源码：** NeoForge（`D:\Minecraft\.neoforge-src`、`.tmp-neoforge-src`）、Cataclysm（`D:\Minecraft\开源模组参考文件\Cataclysm`）、Twilight Forest（`...\TwilightForest-1.21.1`）。  
**方式：** 主审 + 3 个受限子代理；只审查，不修复；本轮没有修改任何代码。  
**结论：** 代码审查 2 Critical、5 High、6 Medium、6 Low；设计一致性检查 3 High、4 Medium、6 Low（文末）。

## Critical

1. **世界生成线程直接修改 `BossHomeSavedData`，存在数据竞争/存档损坏。** `EntityHomeRegistrationHandler.onEntityJoinLevel:54-70,96-98` 在 `EntityJoinLevelEvent` 中调用 `BossHomeSavedData.get/registerIfAbsent`；NeoForge 的 `ChunkStatusTasks.full` 在 `CompletableFuture.supplyAsync` 中执行（`.tmp-neoforge-src/.../ChunkStatusTasks.java:191`），经 `ServerLevel.addWorldGenChunkEntities` / `PersistentEntitySectionManager.addEntity` 触发该事件（`PersistentEntitySectionManager.java:78-80,119-124`），而 `BossHomeSavedData.homes` 是普通 `HashMap`（`BossHomeSavedData.java:70-71,139-146`），服务端线程同时在 autosave/placement 中读写。触发：home 规则匹配到结构生成放入世界的实体；可致 `ConcurrentModificationException`、anchor 丢失或存档损坏。  
2. **force/fallback 完全绕过外来笼保护，可永久覆盖不可破坏方块/箱子；0.1.0 的安全检查已被移除。** `RespawnCagePlacer.java:204-246,332-336` 的 force/fallback 写入不做任何方块检查；`ModBlocks.java:17-22` 的笼子是 `strength(-1.0F, 3600000.0F)` + `noLootTable`，被替换的方块/箱子无掉落，笼子不可破坏且生存不可获得。0.1.0 的 `Config.FOREIGN_CAGE_BLOCK_IDS` + `isBlockedByForeignCage` 曾拒绝覆盖其他模组的笼子/祭坛（默认 `cataclysm:boss_respawner`），该保护在 `6874a82` 被删除，`avoid_blocks` 只是搜索第一趟偏好，force 跳过搜索、第二趟又丢弃它。触发：8 条 TF 的 `placement.force_place:true`（cage 在 `HomePos+[0,-1,1]`）或任意 fallback 默认 true 的 home 规则；Cataclysm 的 `cataclysm:boss_respawner`（`Cataclysm/.../ModBlocks.java:226-232`，不可破坏并负责 Boss 重生）可被永久覆盖。`spawn.transform.force_place` 还会清空 `HomePos`/`HomePos.below()` 的非空箱子（`BossRespawnerBlockEntity.java:355-358,405-429`）：巫妖的 `spawner_offset:[0,-1,0]` 正是 TF 奖励箱格（`EntityUtil.bossChestLocation` = `HomePos.below()`），激活前未取走即永久丢失奖励箱。  

## High

1. **`trySpawn` 直接调用 `Mob.finalizeSpawn`，绕过 NeoForge `FinalizeSpawnEvent`。** `BossRespawnerBlockEntity.java:560-566` 调用已标记 `@Deprecated @ApiStatus.OverrideOnly` 的 `Mob.finalizeSpawn`；NeoForge 要求外部调用走 `EventHooks.finalizeMobSpawn/finalizeMobSpawnSpawner`（`BaseSpawner.java:157-169` 即如此）。依赖 `FinalizeSpawnEvent` 的模组 Boss（属性/装备/BossBar/难度缩放/取消）会半初始化，取消也会被忽略。  
2. **钥匙在结果确定前就被扣除；Peaceful/缺实体/transform 失败都不退款。** `BossRespawnerBlock.java:80-107` 在 `activate()` 成功后立即 `KeyItemPayment.drain`；但 `BossRespawnerBlockEntity.activate:240-259` 对 summon 不检查 Peaceful/实体类型，`tick:181-183` 在 Peaceful 直接 return（不 reset），`finishTransform:372-453` 在目标被占/区块卸载/方块缺失时 `resetToUnlit` 也不退款、不设置 `lastTransformFailure`（玩家无提示）。`ModBlocks.java:20` 的笼子不可破坏，和平难度一次右键即钥匙丢失 + 笼子永久 lit。  
3. **Home 记录在失败/无笼/非死亡移除路径上永久泄漏。** `BossHomeSavedData.homes`（`BossHomeSavedData.java:70-71`）只在成功写入（`RespawnCagePlacer.java:257`）、目标维度不存在（191）或 TF 克隆/非最终骑士跳过（`TwilightSettlementService.java:71-81`）时删除。`LivingDeathHandler.java:60-66` 死亡条件不满足直接 return；`RespawnCagePlacer` 的 `ABANDONED`（219、239、249-254：无安全点、TF 锚点拒绝、`keep_existing`、写入失败）都不 `dropHomeRecord`；`EntityHomeRegistrationHandler` 也没有在 despawn/discard 时清理。6 个内置 `keep_existing` 条目使这个问题在正常游玩中就会持续累积。  
4. **数据包数值无上限，`count`/`search_down`/`search_up`/`horizontal_radius`/`spawn_offset` 可令服务器卡死或 OOM。** `RespawnEntry.java:323-332,549-560` 只做 `Math.max(0/1, ...)`；`PlacementResolver.search:78-110` 按这些值逐格同步循环，`BossRespawnerBlockEntity.trySpawn:540-573` 按 `count` 创建实体，`spawn_offset` 可把实体传送到任意远。`Integer.MAX_VALUE` 会使 `dy++`/`i++`/`r++` 溢出后条件仍成立，形成无限循环；大 `count` 直接 OOM。  
5. **`Config.ENABLE_MOD` 不是真正的总开关。** `Config.java:16-19` 承诺关闭后停止所有 placement/spawning，但全项目只有 `LivingDeathHandler.java:32` 与 `RespawnCagePlacer.java:78` 两处检查；已点亮笼子（`BossRespawnerBlockEntity.tick:160-209,240-259`）、`BossRespawnerBlock.useItemOn:61-123`、已排队任务（`PendingPlacementService:146-317`）、home 登记（`EntityHomeRegistrationHandler:53-70`）都不检查。关闭后现有笼子仍会召唤/转换，deferred 任务仍会落笼，home 仍增长。  

## Medium

1. **`retry_interval_ticks` 在 `retry >= delay` 时失效，`delay_ticks:0` 会每 tick 重试。** `BossRespawnerBlockEntity.java:168-207` 用 `litTicks = max(0, delayTicks - retryIntervalTicks)` 实现退避；当 `retryIntervalTicks >= delayTicks`（含合法的 `delay_ticks:0`）时 `litTicks` 为 0，下一次 tick 立即再次尝试，`retry_interval_ticks` 被忽略；`max_attempts:-1` 时每个 tick 重试并刷日志。  
2. **未注册/被移除的实体 ID 会落到 `minecraft:pig`。** `BossRespawnerBlockEntity.getCachedEntityType:736-744` 用 `BuiltInRegistries.ENTITY_TYPE.get(id)`；这是 `DefaultedRegistry`，未知 id 返回默认值 `EntityType.PIG`（`BuiltInRegistries.java:141-142`），不会返回 null。`RespawnRuleReloadListener` 只过滤新条目，已放置的 cage/持久化 task 在目标模组移除后会召唤并渲染一只猪；`getCachedKeyItem` 同样会把未知物品解析成 `Items.AIR`，使 `matchesKeyItem(EMPTY)` 为 true（只靠 `canPay` 挡住）。  
3. **缺少 `onlyOpCanSetNbt()` 覆盖，非 OP 可通过 `block_entity_data` 注入 `TransformBlock`/`TransformNbt`/`SpawnNbt`。** `BossRespawnerBlockEntity` 没有覆盖该方法；vanilla `BlockItem.java:167-181` 仅在 BE 返回 true 时限制非 OP 应用组件，命令方块/刷怪笼会覆盖，本 BE 不会。非 OP 若通过数据包战利品、kit 或其他模组获得带该组件的笼子物品，可把 `transform.nbt` 设成命令方块的 `Command` 或任意方块实体数据，形成权限提升/任意方块行为。  
4. **`replace_existing` 会无补偿摧毁已点亮的付费笼子，且跨区块边界会漏检。** `RespawnCagePlacer.java:309-329,400-423` 用 `destroyBlock(pos,false)` 删除旧笼子，不退款、不掉落；`existingCagePositions` 只扫描已加载区块，笼子刚跨区块边界时不会被发现，导致重复放置。  
5. **deferred 任务不会过期；`Level.isLoaded` 在建筑高度外永远 false。** `PendingPlacementTask`（`PendingPlacementTask.java:28-39`）没有 expiry；`BossHomeSavedData.pending` 与 `PendingPlacementService` 只等待目标区块/实体移除。`RespawnCagePlacer.java:195` 的 `!level.isLoaded(request.base())` 在 `base` 位于建筑高度外时恒为 true（`Level.isLoaded` 对越界坐标返回 false），任务会永久 defer；`PendingPlacementService.java:295` 的 TF `stillWaitingForRemoval` 也可能永远为 true。  
6. **`spawn.nbt` 中的 `UUID` 会劫持/删除真实 Boss 的 home。** `BossRespawnerBlockEntity.java:560-570,624-636` 先 `entity.load(spawnNbt)`（可从 `/data get entity` 复制 UUID），再 `bindHomeAnchor` 用 `registerOrReplace` 覆盖该 UUID 的真实 home，最后 `addFreshEntity` 因 UUID 已存在而失败，`releaseHomeAnchors` 又删除该 home。触发：数据包中的 `spawn.nbt` 带 `UUID`。  

## Low

1. **`writeCage` 在方块已放置但 BE 缺失时返回 false，留下裸笼子；`EntityJoinLevelEvent` 取消未检查。** `RespawnCagePlacer.java:334-346` 在 `setBlock` 成功后若 BE 不匹配直接返回 false，不回滚方块；`EntityHomeRegistrationHandler.java:53-70` 没有 `event.isCanceled()` 检查，而 NeoForge 的 `EntityJoinLevelEvent` 是 cancellable，`builtinMobSpawnBlocker` 会在 HIGHEST 取消 spawn-cancelled Mob，导致无效 home。  
2. **`addFreshEntity` 不处理 passengers，且召唤生物不设为 persistent。** `BossRespawnerBlockEntity.java:575-583` 用 `addFreshEntity` 而不是 `tryAddFreshEntityWithPassengers`；vanilla spawner 用后者（`BaseSpawner.java:169`），带 `Passengers` 的 NBT 实体不会把乘客加入世界。`Mob.finalizeSpawn` 也不会 `setPersistenceRequired`，`Mob.checkDespawn` 仍可在玩家远离时移除笼子召唤的自定义 Boss，留下孤儿 home。  
3. **`readOnly` 只阻止保存、不阻止内存修改；`homeDimension` 从未与当前维度比较。** `BossHomeSavedData.java:267-279` 在 `dataVersion` 更新时只抑制写盘，`registerIfAbsent/remove/enqueue` 仍修改内存，放置仍使用这些数据，关服时全部静默丢弃。`BossRespawnerBlockEntity` 保存了 `homeDimension`（297-336、401-436、616-636）但 `canTransform/finishTransform/bindHomeAnchor` 都不比较它；被结构方块/命令跨维度移动的 cage 会在错误维度操作同坐标方块。  
4. **8 条 TF 的 `placement.force_place:true` 让 `avoid_blocks` 和 `search_failure_fallback:false` 完全失效。** `RespawnCagePlacer.java:204-206` 在 force 时直接 `chosen=base`，不调用 `findPlacementPos`；18 项 `avoid_blocks` 与 fallback 配置从不参与。`BuiltinEntriesTest` 仍断言这些 avoid 列表“必须保留”（220-222、266、286-290），形成测试/文档与实际行为不一致。此外，7 条非巫妖 spawner 位于 `HomePos`、奖励箱在 `HomePos.below()`，全碰撞刷怪笼会临时封住箱子（`ChestBlock.isChestBlockedAt` 使用 `isCollisionShapeFullBlock`），直到 spawner tick 销毁自身。  
5. **Lich 的 Javadoc 仍写 `[0,1,0]`，与实际 `[0,-1,0]` 相反。** `RespawnEntry.java:588-590` 的 API 注释说 Lich 需要放在 home 上方一格，`lich.json:62-66` 与 `LichSpawnerBlockEntity.spawnMyBoss` 的实际行为（先检查 below 是否空气，再 `move(UP)`）需要 `[0,-1,0]`；维护者按注释改 JSON 会重新引入 home 漂移。  
6. **生命周期行为没有自动化测试覆盖。** `src/test/java` 下的 placement/transform/payment 测试基本是 JSON 解析或源码文本断言（如 `CageIdentityTest:43-51`、`SpawnNbtOrderingTest:50-70`），没有执行 `tick`、`execute`、`finishTransform` 或付款路径的 GameTest/单测；`docs/manual-acceptance-2026-10-16.md:478-507` 的实机验收表也未填写。上述 High 3/6、Medium 1/5 都是这类空白容易隐藏的缺陷。  

## 设计一致性检查：无用、过时、自相矛盾

> 第二轮检查，针对“最终版已部分偏离最初设计”。以下为人、文档、代码之间的对齐问题；只记录，不修复；部分与上面的代码审查结论交叉引用。

### High

1. **README 仍把暮色森林的 `search_failure_fallback:false` 描述成“宁可这一次不放，也不覆盖奖励箱”，与最终 8 条 `placement.force_place:true` 冲突。** `README.md:30` 说 TF 模式的例外是 `search_failure_fallback:false`；但最终 8 条 TF 规则全部 `placement.force_place:true`（如 `lich.json:19-20`），`RespawnCagePlacer.java:204-206` 直接 `chosen=base`，不执行搜索，`search_failure_fallback` 根本不参与；`README.md:106/109` 与 `docs/twilight-forest-mapping.md:79` 也自己写明“兜底分支在 force 路径下不会用到”“只是保留为语义说明”。更严重的是巫妖 `spawn.transform.force_place:true` 会替换奖励箱格（`README.md:108/496`），与第 30 行的“不覆盖奖励箱”直接相反。`docs/twilight-forest-mapping.md:210` 的“`search_failure_fallback` 为 false（不覆盖奖励箱）”是同一处旧说法。  
2. **`general.enableMod=false` 的文档/语言文件仍承诺“关闭后不会生成或召唤任何重生笼”，与代码只有两处检查冲突。** `README.md:624`、`lang/en_us.json:20`、`lang/zh_cn.json:20`、`Config.java:16-19` 都把它描述为总开关；实际只有 `LivingDeathHandler.java:32` 与 `RespawnCagePlacer.java:78` 检查，已点亮笼子、`BossRespawnerBlock.useItemOn`、deferred 任务、home 登记仍工作（见代码审查 High 5）。这是“文档承诺 > 实现”的直接矛盾，而不是普通遗漏。  
3. **v1→v4 迁移表把 `foreignCageBlockIds` 映射到 `avoid_blocks`，暗示旧保护仍存在；实际 0.1.0 的硬检查已被删除。** `README.md:595` 写“全局 `foreignCageBlockIds` → `placement.<mode>.avoid_blocks`”；但 0.1.0 的 `Config.FOREIGN_CAGE_BLOCK_IDS` + `isBlockedByForeignCage` 是 force/fallback 写入前的硬拒绝，v4 的 `avoid_blocks` 只是搜索第一趟偏好（`README.md:406-407`、`home-respawn-design.md:356-419`），force/兜底完全不看它。迁移表没有提示“语义从硬保护降级为搜索偏好”，属于危险的自相矛盾（对应代码审查 Critical 2）。  

### Medium

1. **`docs/home-respawn-design.md:412` 仍写“32 条内置规则全部 `force_place:false`，暮色森林条目还用 `place_delay_ticks` 等箱子”；最终 8 条 TF 是 `force_place:true`，且 TF 已改为等待实体 `remove(KILLED)`，不靠 `place_delay_ticks`。** 这是基础设计文档与最终 B 方案之间最明显的过时断言；同页的“被明确接受的代价”因此少算了 8 条默认 force 的 TF 规则。  
2. **B 方案设计文档正文仍描述第一版 B，而不是最终版。** `docs/plans/2026-11-02-twilightforest-placement-mode-design.md:25` 的目标是“不覆盖、不清空、不挖掉箱子”，`:45`/`:60` 仍写笼子在 `HomePos + [1,0,0]`，`:52` 写“内置规则 `force_place:false`、`search_failure_fallback:false`”，`:62-63` 写“非空箱子拒绝激活”，`:100` 写“搜索失败 `search_failure_fallback:false` → 本次不放”；而最终是 `[0,-1,1]`、`force_place:true`、巫妖替换奖励箱、其余 7 条在 `HomePos`。第 6 行的“后续修复”只是补丁，正文没有统一，读者按正文实施会回到已被修复的旧行为。  
3. **README 第 389 行说 `search_failure_fallback:false` 是“现在唯一一条会主动放弃放置的分支”，与代码和同文档其它段落冲突。** `RespawnCagePlacer` 还有 `keep_existing`（`:249-254`）、TF 锚点硬守卫（`:227-241`）、目标维度不可用（`:191`）、`writeCage` 失败（`:252-254`）等停止路径；`README.md:197`/`:503` 也分别承认“目标维度不可用”和“keep_existing 附近已有笼不再放置”。“唯一”应改为“搜索路径中唯一会主动放弃的分支”。  
4. **首领崛起的箱子避让仍是未完成设计，且修复计划宣称文档已全部同步。** `docs/bosses-rise-mapping.md:74` 明确写“把 `minecraft:chest` / `trapped_chest` 加进每条规则……这一步尚未执行，等你确认”；5 条 `block_factorys_bosses` 规则至今只 `avoid_blocks:["cataclysm:boss_respawner"]`，结构里的原版箱子不在避让列表。`docs/plans/2026-11-02-twilightforest-mode-fix-plan.md:546` 又说 README、mapping、B 设计、人工验收清单“均已改”，与实际 README 第 30 行、TF mapping 第 210 行仍过时不一致。  

### Low

1. **README 还有几处小范围过时/不准确。** `README.md:516-517` 的加载校验仍写 `placement.mode` 只能为 `death` / `home`，漏掉当前支持的 `twilightforest`（同文档 `:208`/`:361` 已列出）；`:495` 说“`setBlock` 也不会失败”，但 `BossRespawnerBlockEntity.finishTransform:372-453` 不检查 `setBlock` 返回值；`:386` 说“被避让的方块永远不会成为目标位”，对可替换方块（草、雪层等）不成立——第二趟会接受它作为候选。  
2. **存在无用 API / 数据结构。** `RespawnRuleManager.java:14-15,38` 的 `byId` map 与 `byId()` getter 没有任何调用者（`RespawnRuleReloadListener` 只使用局部 `byId`）；`BossRespawnerBlockEntity.java:504-520,596-606,768` 的 `getHomeDimension`/`getHomePos`/`getSpawnerOffset`/`getTransformBlockId`/`getTransformNbt`/`getLitTicks` 没有外部调用者；`BossHomeSavedData.isReadOnly()` 仅测试使用。它们增加维护面，也容易让后续改动误以为这些状态有消费方。  
3. **Javadoc / 状态行仍停留在旧版本。** `RespawnEntry.java:590-595` 仍说巫妖刷怪笼要放在 home 上方一格（`[0,1,0]`），并说 `forcePlace` 仅用于巫妖/米诺菇；实际是 `[0,-1,0]`，且 8 条 TF 全部 `spawn.transform.force_place:true`。`docs/plans/2026-11-02-twilightforest-placement-mode-design.md:4` 仍写“第四版”，`:108` 仍写“实机（待用户）”，而用户已完成 8 个 Boss + 谜题羊实测。`Config.java:11` 的“one non-negotiable safety floor”也仍保留，v4 已无该底线。  
4. **8 条 TF 的搜索字段在 force 下全部不参与，但测试仍把它们当作有效约束。** `require_ground:false`、`search_failure_fallback:false`、18 项 `avoid_blocks`、`search_down`/`search_up`/`horizontal_radius` 在 `placement.force_place:true` 下都不会被读取（`RespawnCagePlacer.java:204-206`）；`BuiltinEntriesTest.java:203-220` 的注释仍说“guard and search_failure_fallback:false still win”“the migrated avoidance … must survive”。作为“字段保留/模板完整性”可以接受，但当前测试注释会误导维护者以为运行时会检查它们（参见代码审查 Low 4）。  
5. **历史评审/计划文档没有统一标记或索引，仍混有已删除配置。** `docs/code-review-2026-09-05.md:446`（`Config.foreignCageBlockIds`）、`docs/code-review-2026-09-06.md`、`docs/fix-plan-2026-09-06.md`、`docs/home-respawn-design-review-2026-10-16.md:414`、`docs/cataclysm-boss-respawner-comparison.md:135`（`Config.FOREIGN_CAGE_BLOCK_IDS`）仍出现 v3/v2 时代的保护/全局配置名；其中部分有“v4 已撤回”顶注，部分没有。建议在 `docs/README` 或文档头部明确“历史文档”与当前文档的边界，避免后续维护者按旧评审重新引入 `protected_blocks`/`foreignCageBlockIds`。  
6. **几处文档/语言文本与实现语义不一致。** `docs/home-respawn-design.md:660` 说 dataVersion 更新会“拒绝读写”，实际 `BossHomeSavedData.readOnly` 只抑制 `save`，读取和内存修改仍发生（见代码审查 Low 3）；`BossHomeSavedData.java:46` 说 home 记录“Entries are consumed when a cage is placed”，但 ABANDONED/非死亡移除路径并不删除（见代码审查 High 3）；`lang/en_us.json:31` 的 `respawn_failed` 用 `%s` 表示位置，代码实际传入的是实体 ID，`lang/zh_cn.json:32` 的 `anchor_occupied` 又只提示“清空奖励箱”，与 en 的“home is occupied”不完全等价。  

## 设计一致性修复状态（2026-11-04）

> 用户已完成 8 个暮色森林主线 Boss + 谜题羊 + 四个联动模组的实机测试，并复核了 `debug.log`。以下为设计一致性检查的闭环记录。

- **High 1 已修复**：`README.md:30`、`docs/twilight-forest-mapping.md:210`、B 方案设计正文已改为“8 条 TF 使用 `placement.force_place:true`；`search_failure_fallback:false` 只是保留语义；笼子不压奖励箱靠 TF 锚点硬守卫/偏移；巫妖的 `spawn.transform.force_place` 替换奖励箱格”。
- **High 2 已闭环**：代码已在 tick/activate/useItemOn/PendingPlacementService/EntityHomeRegistrationHandler/TwilightSettlementService 检查 `Config.ENABLE_MOD`；README、`Config.java` 注释、en_us/zh_cn 的 `enableMod.tooltip` 已改为“关闭停止新放置/召唤；已点亮笼子和排队任务暂停，重新开启继续”。
- **High 3 已记录取舍**：README v1→v4 迁移表已明确“`foreignCageBlockIds` 的硬拒绝检查已删除；`avoid_blocks` 只是搜索偏好；force/兜底仍可覆盖；0.1.0 的‘外来笼不可覆盖’不再保证”。这是用户选择的 **Critical 2 = D** 的明确记录，不再作为待修项。
- **Medium 1 已修复**：`docs/home-respawn-design.md:412` 改为“24 条非 TF `force_place:false`；8 条 TF `force_place:true`，直接放 `HomePos+[0,-1,1]`，不再用 `place_delay_ticks`，改为等待 `remove(KILLED)`”。
- **Medium 2 已修复**：B 方案设计状态改为最终版；正文统一为 `[0,-1,1]`、`placement.force_place:true`、巫妖/其余 7 条 `spawner_offset`、force 替换箱子的最终规则；`106` 改为 `115`；“实机待用户”改为 2026-11-03/04 已完成。
- **Medium 3 已修复**：`README.md:389` 已改为“搜索路径唯一会主动放弃的分支”，并列出 `keep_existing`/TF 锚点/维度/写入失败等停止路径。
- **Medium 4 已修复/记录**：`docs/bosses-rise-mapping.md:74` 已把原版箱子避让标为“2026-11-04 明确接受的设计限制”，不再写“等你确认”；修复计划第 12 节的“均已改”已改为历史记录并指向第 13/14 节最终版。
- **Low 1 已修复**：README 的 `placement.mode` 校验补上 `twilightforest`；“`setBlock` 不会失败”改为 `finishTransform` 检查返回值并退款；“被避让的方块永远不会成为目标位”改为仅对实心方块成立，可替换方块可能在第二趟被接受。
- **Low 2 已修复**：删除 `RespawnRuleManager.byId` 及 `getById()`；删除 `BossRespawnerBlockEntity` 的 `getHomeDimension/getHomePos/getSpawnerOffset/getTransformBlockId/getTransformNbt/getLitTicks`；`BossHomeSavedData.isReadOnly()` 降为包内可见（仅测试使用）。
- **Low 3 已修复**：`RespawnEntry.TransformRule` Javadoc 改为巫妖 `[0,-1,0]`、8 条 TF 都启用 `spawn.transform.force_place`；B 方案状态/实机行已更新；`Config.java` 的“one non-negotiable safety floor”已删除。
- **Low 4 已修复**：`BuiltinEntriesTest` 注释改为“8 条 TF force；fallback/avoid 是模板数据，force 路径不读取”；README 的 force/TF 说明已同步。
- **Low 5 已修复**：新增 `docs/README.md` 当前/历史文档索引；`code-review-2026-09-05/06`、`fix-plan-2026-09-06`、`plans/2026-09-07...`、`cataclysm-boss-respawner-comparison` 顶部已加历史/状态说明；`home-respawn-design-review-2026-10-16` 原有 v4 撤回说明保留。
- **Low 6 已修复**：`docs/home-respawn-design.md:660` 改为“拒绝写入和内存修改”；`BossHomeSavedData` 注释改为“成功放置/放弃/非死亡移除都会清理”；`respawn_failed`/`anchor_occupied` 中英文案已同步。
- **日志复核补充（Low，非泄漏）**：`debug.log` 中 13 条 `No home record` 是联动 Boss 对同一 UUID 连续触发两次 `LivingDeathEvent` 的第二次事件；第一次已成功放笼并删除 home，第二次只是日志噪音。`Removed home record ... after successful placement` 30 条，`ConcurrentModificationException`/`Failed to schedule` 0 条；可选的后续 Low 是同 UUID 短窗口去重或改写该警告文案。
- **验证**：`gradlew.bat test` **115 项全部通过**；`gradlew.bat build` 成功；设计一致性文档已同步。

