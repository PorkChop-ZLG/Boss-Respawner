# Universal Boss Respawner（通用Boss重生笼）

一个数据驱动的 NeoForge 1.21.1 模组：生物/Boss 死亡后，可按照 JSON 配置生成“重生笼”；玩家使用指定物品右键激活后，重生笼会重新召唤该生物。

- **Mod ID：** `boss_respawner`
- **环境：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21
- **定位：** 独立模组，不强依赖 Cataclysm 或任何具体 Boss 模组。

> 当前开发阶段默认带有一个启用的 `minecraft:warden` 示例条目。
> **正式发布前请移除或禁用** `src/main/resources/data/boss_respawner/boss_respawner/entries/minecraft/warden.json`，
> 以保持“默认不干扰任何生物”的设计目标。

---

## 功能

- 生物死亡后按 JSON 规则放置未激活重生笼。
- 方块实体保存实体类型、钥匙物品与生成参数快照。
- 手持正确物品右键：消耗物品、点亮方块、播放动画。
- 到达延迟后尝试生成目标实体；失败保持点亮并重试。
- 生成成功自动销毁重生笼。

## 获取方块

本模组将重生笼 BlockItem 放入原版“刷怪蛋”创造模式标签页。

也可通过命令获取：

```mcfunction
/give @s boss_respawner:boss_respawner
```

## JSON 配置

数据包目录：

```
data/<namespace>/boss_respawner/entries/<任意文件名>.json
```

监听目录会递归加载所有 `.json`，执行 `/reload` 后生效。

### 示例：监守者

文件：`data/my_pack/boss_respawner/entries/minecraft/warden.json`

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
    "player_kill_only": false,
    "dimensions": [],
    "biomes": []
  },
  "spawn": {
    "delay_ticks": 60,
    "require_player_nearby": true,
    "player_range": 16.0,
    "allow_peaceful": false,
    "count": 1,
    "spawn_offset": [0, 1, 0],
    "nbt": "",
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
    "search_radius": 16
  }
}
```

### 常用字段速查

| 字段 | 说明 |
|---|---|
| `entity` | 目标实体 ID，可用字符串或数组 |
| `activation.item` | 激活所需物品 ID |
| `activation.amount` | 每次激活消耗数量 |
| `placement.mode` | `death` / `home` / `death_or_home` |
| `spawn.delay_ticks` | 点亮后等待 tick 数 |
| `spawn.nbt` | SNBT 格式的额外实体 NBT，例如 `"{Health:100f}"` |
| `duplicate.mode` | `keep_existing` / `replace_existing` / `allow_multiple` |

完整字段说明见 `docs/plans/2026-09-05-universal-boss-respawner-design.md`。

## TOML 全局配置

生成于 `config/boss_respawner-common.toml`：

```toml
[general]
enableMod = true
logPlacement = true

[compat]
foreignCageBlockIds = ["cataclysm:boss_respawner"]

[limits]
maxCagesPerChunk = -1
```

## 开发验证

```bat
gradlew.bat compileJava
gradlew.bat build
gradlew.bat runServer
gradlew.bat runClient
```

## 美术资源

本模组使用的重生笼贴图来自 Cataclysm，并已获得作者授权移植。模型与动画代码按 Vanilla `ModelPart` / `AnimationDefinition` 重写，不依赖 Lionfish API。
