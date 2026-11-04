# 更新日志

本文件记录 Universal Boss Respawner 的主要版本变更。版本号遵循语义化版本；0.2.0 是自 0.1.0 以来的首个功能/兼容性大版本。

## [0.2.0] - 2026-11-04

### 新增

- 新增 `twilightforest` 放置模式：支持暮色森林 8 个主线 Boss（娜迦、巫妖、米诺菇、九头蛇、幻影骑士、暮初恶魂、雪怪首领、冰雪女王）与谜题羊。Boss 死亡后等待实体 `remove(KILLED)`，在 `HomePos + [0,-1,1]` 强制放置；巫妖刷怪笼 `spawner_offset:[0,-1,0]`，其余 7 条 `[0,0,0]`。
- 新增首领崛起兼容条目：炼狱飞龙、巨型沙虫、冥界骑士（`home` 模式，允许悬空放置）。
- 新增 Home 出生点模式、持久化待放置队列和跨区块重生笼索引。
- 新增 Jade 可选信息显示（未安装 Jade 时不影响其他功能）。
- 新增 `enableMod` 总开关与完整的 JSON 搜索/放置/召唤参数；内置规则共 30 条。

### 变更

- JSON 升级为 schema v4：`placement` 与 `spawn` 改为嵌套结构；旧 v1-v3 规则需要按 README 的迁移表改写，不能只改 `schema_version`。
- 移除 `protected_blocks` 与全局 `foreignCageBlockIds` 硬保护，统一为每条规则的 `avoid_blocks` 搜索偏好。`force_place` / 兜底仍可覆盖方块；0.1.0 的“外来笼/祭坛不可覆盖”不再保证，这是明确接受的行为取舍。
- 暮色森林 8 条主线改为 `placement.force_place:true`、`[0,-1,1]`、`require_ground:false`；巫妖按设计替换奖励箱格，其余 7 条保留奖励箱。
- `enableMod` 现在是真正的总开关：关闭后停止新放置/召唤，已点亮笼子和排队任务暂停，重新开启后继续。
- 首领崛起 3 条已支持规则允许悬空；雪怪、海怪不接入。

### 修复

- 修复 Home 登记在区块 worker 线程访问 `SavedData` 的线程安全问题。
- 修复 `FinalizeSpawnEvent` 未触发导致联动模组无法修改 Boss 血量/属性的问题。
- 修复 Peaceful、实体缺失、钥匙缺失、transform 失败等路径的钥匙扣除/退款、提示与笼子重置。
- 修复 Home 记录在死亡、放弃放置、discard 等路径的清理和泄漏。
- 修复数据包数值无上限、跨区块重复检查、deferred 重试、UUID 覆盖、NBT/位置顺序、`setBlock` 失败回滚等问题。
- 修复暮色森林、Home、跨维度、未加载区块和服务器重启后的放置边界行为。
- 同步 README、设计文档、映射表、人工验收记录和文档索引。

### 移除

- 移除首领崛起雪怪（Yeti）条目：其自定义死亡使用 `discard()`，不触发 `LivingDeathEvent`，无法放置重生笼。
- 移除首领崛起海怪（Kraken）条目：其 `ship_position`/`ship_facing` 船锚点和开场阶段无法用静态 JSON 可靠恢复，实测复活位置错误。
- 移除 `protected_blocks` / `foreignCageBlockIds` 保护机制（见上）。

### 兼容性与测试

- Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21。
- 可选联动：Cataclysm、Legendary Monsters、Mowzie's Mobs、Twilight Forest、Bosses Rise、Jade；均不是硬依赖。
- 116 项单元测试全部通过；内置 JSON 30 条（雪怪、海怪除外）。

## [0.1.0] - 2026-09-07

- 首个公开版本：数据驱动的 Boss 重生笼、JSON 规则、死亡点放置和复活物品机制。
