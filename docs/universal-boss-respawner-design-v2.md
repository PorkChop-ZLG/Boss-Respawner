# 通用 Boss 重生笼 设计文档 v2

**日期：** 2026-09-05  
**状态：** 替代初版设计中的“HomePos 解析 / visual 配置 / 每区块限制”

---

## 1. 设计原则

- 通用、数据驱动。
- 不硬依赖任何具体模组。
- 同一个 Boss 可同时存在多套重生笼，这是刻意设计。
- 本模组只在**生物死亡地点附近**生成重生笼。
- 默认最多尝试 10 次；失败后恢复未点亮、重置，并给附近玩家发翻译警告。

---

## 2. JSON 配置

数据包目录：

```text
data/<namespace>/boss_respawner/entries/*.json
```

### 示例

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
    "offset": [0, 1, 0],
    "search_down": 32,
    "search_up": 16,
    "horizontal_radius": 4,
    "require_ground": true,
    "avoid_fluids": true,
    "avoid_blocks": ["cataclysm:boss_respawner"]
  },
  "death": {
    "player_kill_only": false,
    "dimensions": [],
    "biomes": []
  },
  "spawn": {
    "delay_ticks": 20,
    "require_player_nearby": true,
    "player_range": 9.0,
    "allow_peaceful": false,
    "count": 1,
    "spawn_offset": [0, 0, 0],
    "nbt": "",
    "finalize_spawn": true,
    "max_attempts": 20,
    "retry_interval_ticks": 4
  },
  "duplicate": {
    "mode": "allow_multiple",
    "search_radius": 16
  }
}
```

### 字段说明

| 字段 | 说明 |
|---|---|
| `entity` | 目标实体 ID，字符串或数组 |
| `activation.item` | 钥匙物品 ID |
| `activation.amount` | 消耗数量 |
| `activation.consume` | 是否消耗 |
| `placement.offset` | 死亡点偏移 |
| `spawn.delay_ticks` | 点亮到尝试生成的延迟，默认 20 |
| `spawn.player_range` | 附近玩家检测范围，默认 9 |
| `spawn.spawn_offset` | 生成实体相对重生笼的偏移，默认 `[0,0,0]` |
| `spawn.max_attempts` | 最大尝试次数，默认 20；失败后重置为未点亮 |
| `spawn.retry_interval_ticks` | 失败后重试间隔，默认 4 tick |
| `spawn.nbt` | 生成实体的额外 SNBT |
| `duplicate.mode` | `keep_existing` / `replace_existing` / `allow_multiple`；默认 `allow_multiple`，与灾变一致不做重复检查 |

**已移除：**
- `placement.mode`（不再支持 home / death_or_home）
- `visual.*`（始终渲染幽灵生物与钥匙物品）
- `spawn.set_home_to_cage`
- `max_cages_per_chunk`

---

## 3. 核心机制

1. `LivingDeathEvent`（LOWEST）读取 JSON 规则。
2. 在死亡点附近安全找位。
3. 放置 `boss_respawner:boss_respawner`。
4. 方块实体保存配置快照。
5. 玩家右键激活。
6. BE tick 延迟后尝试生成。
7. 成功销毁；失败重试。
8. 达到 `max_attempts` 后恢复未点亮，重置，聊天 + 日志警告。

---

## 4. 客户端

- 始终渲染幽灵生物预览和钥匙物品。
- 使用 Vanilla `ModelPart` / `AnimationDefinition`，不依赖 Lionfish。
- 渲染中使用 BlockEntity 缓存的 `EntityType` / `Item`，避免每帧注册表查询。

---

## 5. TOML

```toml
[general]
enableMod = true
# Debug logging: prints Boss respawner debug information when enabled.
logPlacement = false

[compat]
foreignCageBlockIds = ["cataclysm:boss_respawner"]
```

---

## 6. 兼容性

- Cataclysm / Legendary Monsters 为可选运行时依赖。
- 同一 Boss 可同时拥有多套重生笼，刻意允许。
- `foreignCageBlockIds` 只避免把本模组笼直接放在其它模组笼/祭坛方块上。
- 缺少目标模组时，JSON 条目在 `/reload` 阶段因实体或物品未注册而被跳过。
