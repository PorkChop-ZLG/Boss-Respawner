# Home 出生点重生笼 · 人工实机验收清单

**被测版本：** `boss_respawner-0.1.0.jar`（`gradlew build` 产物，SHA 见交付报告）
**目标环境：** Minecraft 1.21.1 / NeoForge 21.1.219 / Java 21
**配套测试数据包：** [`docs/examples/acceptance-datapack/`](examples/acceptance-datapack/)
**覆盖来源：** 执行文档「三、测试与验收标准 → 行为验收」全部 21 条，加上实现特有的若干条

---

## 0. 准备

### 0.1 安装

1. 构建产物：`build/libs/boss_respawner-0.1.0.jar` → 丢进整合包的 `mods/`。
2. 建议同时装 **Cataclysm**（用于 C6 共存回归，以及 P6 里"强制放置确实会覆盖灾变祭坛"这一条 —— v4 已删除保护底线，祭坛不再是例外）。用 `gradlew runClient` 启动时会自动带上 `build.gradle` 里声明的可选依赖（灾变 / 传奇怪物 / Mowzie's / 暮色森林 / 首领崛起）。

### 0.2 打开调试日志（**强烈建议，本清单大部分证据来自日志**）

编辑 `config/boss_respawner-common.toml`：

```toml
[debug]
debugInfo = true
```

或在游戏内 `模组列表 → 通用Boss重生笼 → 配置` 里勾选「调试信息」，然后重启客户端/服务端。

打开后放置决策会以 `[BossRespawner-Debug]` 前缀写入日志，本清单中标注「日志」的预期项都指这个。

### 0.3 关键路径

| 内容 | 路径 |
|---|---|
| 日志 | `logs/latest.log`（运行目录下） |
| Home 数据库 | `saves/<存档名>/data/boss_respawner_homes.dat` |
| 全局配置 | `config/boss_respawner-common.toml` |

跟踪 UUID 不需要打开 `.dat`——日志里的 `Registered home via entity-join: entity=... uuid=... dimension=... pos=...` 就是全部信息。

### 0.4 安装验收数据包

把 `docs/examples/acceptance-datapack/` **整个目录**复制到：

```
saves/<存档名>/datapacks/acceptance-datapack/
```

然后 `/reload`，用 `/datapack list` 确认它处于 enabled 状态。

数据包内容：

| 文件 | 用途 | 期望 |
|---|---|---|
| `01_home_ravager.json` | home 模式主流程（劫掠兽，钥匙=绿宝石，不消耗） | 正常加载 |
| `02_death_zombie.json` | death 模式对照（僵尸，钥匙=腐肉） | 正常加载 |
| `03_force_place_over_avoid.json` | `force_place:true` + `avoid_blocks:["minecraft:chest"]`（掠夺者）—— 强制放置跳过搜索，**无视避让列表** | 正常加载 |
| `04_fallback_overwrites.json` | `force_place:false` + `search_failure_fallback:true` + 搜索半径全 0 + `avoid_blocks:["minecraft:chest"]`（卫道士）—— 两趟搜索必然失败，由**兜底写入**覆盖目标 | 正常加载 |
| `05_no_fallback.json` | `search_failure_fallback:false` + 搜索半径全 0（烈焰人）—— 与 04 只差一个开关，结果是**不放置** | 正常加载 |
| `06_duplicate_keep_existing.json` | `duplicate.mode: keep_existing`（女巫） | 正常加载 |
| `07_rejected_v1_schema.json` | `schema_version: 1` | **被拒绝** |
| `08_rejected_flat_placement.json` | v2 但扁平 `placement` | **被拒绝** |
| `09_rejected_missing_mode.json` | 有 `placement.home` 但缺 `mode` | **被拒绝** |
| `10_rejected_v3_schema.json` | `schema_version: 3` 且 `placement.home` 仍带已删除的 `protected_blocks`（其余部分完全是合法 v4） | **被拒绝**（版本门 + 迁移提示） |

数据包里所有 key 物品都设了 `consume: false`，测试时不用反复刷物品。

### 0.5 常用命令速查

```mcfunction
# 取方块
/give @s boss_respawner:boss_respawner

# 召唤带标签的测试生物（标签方便批量操作）
/summon minecraft:ravager ~ ~ ~ {PersistenceRequired:1b,Tags:["acc"]}

# 批量击杀 / 传送到指定坐标
/kill @e[type=minecraft:ravager,tag=acc]
/tp @e[type=minecraft:ravager,tag=acc] <x> <y> <z>

# 查看某处是不是重生笼（注意：会加载该区块！见 D1 的说明）
/execute if block <x> <y> <z> boss_respawner:boss_respawner

# 查看当前区块坐标（换算：方块坐标 >> 4）
/fill ...
```

### 0.6 单项测试之间的重置

**关闭世界后**删除 `saves/<存档名>/data/boss_respawner_homes.dat`，即可清空所有出生点记录与待放置任务。

> 游戏运行中删除无效：数据已被读进内存，下次保存会重新写出。

---

## 1. 冒烟测试

### S1 模组可用性与高亮 【实机】

1. 进入世界，`/give @s boss_respawner:boss_respawner`，放在地面。
2. 用手打它。

| # | 预期 |
|---|---|
| S1-a | 能拿到方块，能正常放置 |
| S1-b | 生存模式下**打不掉**（这是设计行为，README 有说明） |
| S1-c | 默认配置下有白色发光描边；用方块挡住视线仍能透过方块看到 |
| S1-d | 把 `highlightBossRespawner` 改为 `false` 后描边消失；把 `highlightBossRespawnerRange` 调到 5 后走远即消失 |

---

## 2. Schema v4 解析（负向路径）

`/reload` 后查看日志。四条负向规则应当各有明确的警告，且都不影响其他规则。

| # | 预期日志（关键片段） |
|---|---|
| R1 | `Respawn entry acceptance:07_rejected_v1_schema uses unsupported schema_version 1 (expected 4), skipping - schema v1 put the placement fields directly under "placement"; see README for the v1 -> v4 mapping` |
| R2 | `Respawn entry acceptance:08_rejected_flat_placement uses unsupported schema_version 2 (expected 4), skipping - schema v2 put the spawn fields directly under "spawn"; ... see README for the v2 -> v4 mapping` |
| R3 | `Respawn entry acceptance:09_rejected_missing_mode uses unsupported schema_version 2 (expected 4), skipping` |
| R4 | `Respawn entry acceptance:10_rejected_v3_schema uses unsupported schema_version 3 (expected 4), skipping - schema v3 still had placement.<mode>.protected_blocks; v4 removed it, and avoid_blocks is now the only block list. See README for the v3 -> v4 mapping` |

> 08 与 09 的文件结构分别是"v1 扁平 placement"与"缺 mode"，但它们的 `schema_version` 是 2，因此在 v4 下**先被版本门拒绝**。那两条结构专属的警告由单元测试 `RespawnEntrySchemaTest` 用合法 v4 规则体钉住，实机不必构造。

### R5 内置 32 条全部加载成功 【日志】

| # | 预期 |
|---|---|
| R5-a | 日志出现 `Loaded 32 boss respawn entries (...)` —— 这里统计的是**全部**条目（含验收数据包），实际数字 = 32 + 验收包里成功加载的 6 条 = **38**。若少于 38，说明有内置条目被拒绝，需查日志定位。 |
| R5-b | 日志中**不应**出现任何内置条目（`legendary_monsters:*` / `mowziesmobs:*` / `twilightforest:*` / `block_factorys_bosses:*`）的 skipping 警告 |

> 注：内置条目里引用的实体和物品需要对应模组在场。若未装传奇怪物/Mowzie's/暮色森林/首领崛起，这些条目会因「实体未注册」被跳过——这是预期行为，不是缺陷。**要完整验证 R5 请装齐可选依赖。**

---

## 3. Home 出生点登记

### H1 首次观察即登记 【实机+日志】

1. 站到一处空地，记下坐标 A。
2. `/summon minecraft:ravager ~ ~ ~ {PersistenceRequired:1b,Tags:["acc"]}`

| # | 预期 |
|---|---|
| H1-a | 日志出现 `Registered home via entity-join: entity=minecraft:ravager uuid=... dimension=minecraft:overworld pos=<A> loadedFromDisk=false` |
| H1-b | `pos` 与 A 一致 |

### H2 移动后不覆盖原出生点 【实机+日志】

1. 承接 H1，把劫掠兽 `/tp` 到 200 格外（坐标 B），或退出重进让区块重载。

| # | 预期 |
|---|---|
| H2-a | 日志出现 `Home already registered for entity=minecraft:ravager uuid=...; keeping the original anchor` |
| H2-b | 后续在 B 击杀它时，重生笼出现在 **A** 附近，而不是 B（这一条与 P1 合并验证） |

### H3 旧存档 Boss 首次加载时登记当前位置 【实机+日志】

1. **先禁用验收数据包**（`/datapack disable "file/acceptance-datapack"`），确保劫掠兽没有规则。
2. 在坐标 C 召唤一只劫掠兽，让它所在区块卸载（走远）。
3. 重新启用数据包并 `/reload`。
4. 走回 C 让区块加载。

| # | 预期 |
|---|---|
| H3-a | 区块加载时出现 `Registered home via entity-join: ... pos=<C> loadedFromDisk=true` —— `loadedFromDisk=true` 说明它是**从存档读出来的**实体，仍然被登记了 |
| H3-b | 若把它 `/tp` 到 D 之后再重载区块，日志是 `keeping the original anchor`，出生点仍是 C |

> 这一条专门验证「不能用 `loadedFromDisk()` 跳过登记」。

### H4 `/reload` 后补登记已加载实体 【实机+日志】

1. 禁用数据包，在坐标 E 召唤一只女巫（`minecraft:witch`），**保持区块加载**（站在旁边）。
2. 启用数据包并 `/reload`。

| # | 预期 |
|---|---|
| H4-a | 下一个 tick 出现 `Home back-fill after rule reload: registered=1 alreadyKnown=... failed=0 (total anchors=...)` |
| H4-b | `registered` 至少为 1（那只女巫被补登记了） |
| H4-c | 补登记期间**没有**新的区块被加载（你不应看到远处地形突然生成，也没有卡顿） |

---

## 4. 放置语义

### P1 home 模式锚定出生点，而不是死亡点 【实机+日志】

**这是 home 模式存在的理由，最重要的一条。**

1. 在坐标 A 召唤劫掠兽（登记 home=A）。
2. `/tp` 它到 200 格外坐标 B（**仍在加载范围内**，例如 A 与 B 距离 100 格且你站在 B 附近）。
3. 在 B 击杀它。

| # | 预期 |
|---|---|
| P1-a | 日志出现 `Placement search started: base=<A附近> ...` —— **base 是 A 不是 B** |
| P1-b | 重生笼出现在 A 附近，B 处**没有**重生笼 |
| P1-c | 若 A 处搜索失败，日志是强制放置到 A，**绝不会**回退到 B |

### P2 death 模式仍锚定死亡位置 【实机+日志】

1. 在坐标 F 击杀一只僵尸。

| # | 预期 |
|---|---|
| P2-a | 日志 `Placement search started: base=<F>` |
| P2-b | 重生笼出现在 F 附近 |

### P3 `force_place` 跳过搜索，直接落在目标格 【实机+日志】

用 `03_force_place_over_avoid.json`（掠夺者，`offset:[0,-1,0]`、`force_place:true`、`avoid_blocks:["minecraft:chest"]`）：

1. 在地面放一个**箱子**。
2. 站在箱子正上方召唤掠夺者（`/summon minecraft:pillager ~ ~ ~ {PersistenceRequired:1b,Tags:["acc"]}`，确保站在箱子上）。
3. 击杀它。

| # | 预期 |
|---|---|
| P3-a | 规则 `offset:[0,-1,0]` + `force_place:true` → 重生笼**正好出现在箱子那一格**，箱子被替换 |
| P3-b | 日志 `Placement search` 不出现（`force_place:true` 完全不执行搜索），直接 `Placed ... respawn cage at <箱子坐标>` |

### P4 `force_place` 无视 `avoid_blocks`，且旧方块内容蒸发 【实机+日志】

承接 P3：该规则的 `avoid_blocks` **包含** `minecraft:chest`，而 v4 里已经没有任何"保护"字段。

1. 击杀前先往那个箱子里放几件**可辨认的物品**（例如命名过的钻石），并记下箱子坐标。
2. 按 P3 击杀掠夺者。

| # | 预期 |
|---|---|
| P4-a | 箱子仍被替换 → 证明 `avoid_blocks` 对 `force_place` 完全无效（它压根不参与这条路径） |
| P4-b | 打掉重生笼附近/用 `/data get block` 检查：原箱子内容**不在地上、不掉落、不转移**，直接消失 |
| P4-c | 日志里**没有**任何 `Refusing to place` 之类的拒绝行（v4 已删除该分支） |

### P5 兜底写入同样覆盖被避让的方块 【实机+日志】

用 `04_fallback_overwrites.json`（卫道士，`offset:[0,-1,0]`、`force_place:false`、`search_failure_fallback:true`、搜索半径全 0）：

1. 同样放一个箱子，站在箱子正上方召唤卫道士（`/summon minecraft:vindicator ~ ~ ~ {PersistenceRequired:1b,Tags:["acc"]}`）。
2. 击杀它。

| # | 预期 |
|---|---|
| P5-a | 规则 `offset:[0,-1,0]` → base 就是箱子那一格；候选位既不是空气也不可替换，**两趟搜索都找不到候选** |
| P5-b | 因为 `search_failure_fallback:true`，重生笼**仍然出现在箱子那一格**，箱子被替换 |
| P5-c | 日志按顺序出现：`Placement search started: ... avoid=[minecraft:chest]` → `No placement found near ... while avoiding [minecraft:chest]; retrying with the avoid filter relaxed` → `Relaxed search near ... found nothing either` → `No safe placement found; forcing respawn cage at <箱子坐标>` → `Placed ... respawn cage at <箱子坐标>` |

> P5 与 P7 用的是同一种"搜索必然失败"的形状（半径全 0 + `require_ground:true`），唯一区别是 `search_failure_fallback` 的取值 —— 这一对是判定"兜底开关是否真的决定放不放"的关键。

### P6 避让不会让笼子消失：两趟搜索的放宽行为 【实机+日志，需临时改规则】

> 这一条是 v4 的核心保证，值得花 5 分钟做。**做完请把文件改回去。**

1. 把 `03_force_place_over_avoid.json` 临时改成：
   - `"force_place": false`
   - `"offset": [0, 0, 0]`
   - `"search_down": 0`、`"search_up": 0`、`"horizontal_radius": 0`
   - `"require_ground": true`
   - 保留 `"avoid_blocks": ["minecraft:chest"]`
2. `/reload`，然后**站在箱子正上方**（base 就是你所站的那一格空气，下方是箱子）召唤掠夺者并击杀。

| # | 预期 |
|---|---|
| P6-a | 第 1 趟搜索因为"下方是 `avoid_blocks` 里的箱子"而跳过唯一的候选；第 2 趟忽略避让，**接受该候选** |
| P6-b | 重生笼出现在**箱子上面那一格**，箱子**原样保留**（内容也在）—— 这就是"避让只会换个位置，不会让笼子消失" |
| P6-c | 日志出现 `retrying with the avoid filter relaxed` 与 `Relaxed search near <坐标> succeeded` |
| P6-d | 不存在任何"拒绝放置"的日志行 |

### P7 `search_failure_fallback: false` 时不放置 【实机+日志】

用 `05_no_fallback.json`（烈焰人，搜索半径全 0、`require_ground:true`、`avoid_blocks` 为空）：

1. 在空中召唤烈焰人（`/summon minecraft:blaze ~ ~5 ~ {PersistenceRequired:1b,Tags:["acc"]}`，脚下是空气）。
2. 击杀它。

| # | 预期 |
|---|---|
| P7-a | **没有**重生笼生成 |
| P7-b | 日志 `No safe placement position for minecraft:blaze near <坐标> and search_failure_fallback is false; no respawn cage placed` |
| P7-c | 这是服务端**唯一**一条会主动放弃放置的分支（另两条"停止"分别是"目标区块始终未加载"与 `keep_existing` 命中已有笼，都属设计如此） |

### P8 `keep_existing` 生效 【实机+日志】

用 `06_duplicate_keep_existing.json`（女巫）：

1. 在同一坐标召唤**两只**女巫（分两次 summon，坐标相同）。
2. 依次击杀两只。

| # | 预期 |
|---|---|
| P8-a | 该坐标附近**只有 1 个**重生笼 |
| P8-b | 第二次击杀的日志出现 `Keeping existing minecraft:witch respawn cage near ...` |

### P9 同一 Home 多笼塌缩（`allow_multiple` 的已接受限制）【实机+日志】

用 `01_home_ravager.json`（`allow_multiple`）：

1. 同一坐标召唤两只劫掠兽，依次击杀。

| # | 预期 |
|---|---|
| P9-a | 该坐标最终只有 **1 个**重生笼方块实体（后放置者覆盖先放置者），日志两次都有 `Placed ... respawn cage at <同一坐标>` |

> 这是设计文档 §5.6 与 §10 明确接受的限制，**不是缺陷**；此项用于确认文档描述与实现一致。

### P10 灾变祭坛不再是例外（可选，需装 Cataclysm）【实机】

> v3 里全局 `protectedBlockIds` 默认含 `cataclysm:boss_respawner`，祭坛是"不可覆盖"的；**v4 删除了这条底线**，祭坛会像箱子一样被覆盖。这一条用来确认删除是彻底的、没有残留的隐式保护。

1. 找一个灾变的 `cataclysm:boss_respawner` 祭坛方块，站在它正上方。
2. 用 `03_force_place_over_avoid.json`（掠夺者，`offset:[0,-1,0]`、`force_place:true`）召唤并击杀。

| # | 预期 |
|---|---|
| P10-a | 祭坛**被重生笼替换**，日志里没有任何拒绝/保护相关的行 |
| P10-b | 配置文件 `config/boss_respawner-common.toml` 里**没有** `[compat]` 组、没有 `protectedBlockIds`（见 C4） |

---

## 5. defer：未加载区块的持久化延迟放置

> **本轮设计的核心风险点，请优先做这一组。**

### D1 未加载区块时不放置、不加载、不卡服 【实机+日志】

1. 在坐标 A 召唤劫掠兽，登记 home=A，然后**走远到 500 格外**的坐标 B，确保 A 的区块已卸载。
2. 在 B 处把劫掠兽 `/tp` 过来（或用命令直接传送到 B），在 B 击杀它。

| # | 预期 |
|---|---|
| D1-a | B 处**没有**重生笼 |
| D1-b | 日志出现 `Deferred respawn cage for minecraft:ravager at <A> (chunk not loaded); queue size=1` |
| D1-c | 日志**不出现** `Placed ... respawn cage`，也不出现 `Placement search started` |
| D1-d | 击杀瞬间**没有明显卡顿**。用专用服务端（`gradlew runServer`）测试时，控制台**不应**出现 `Can't keep up! Is the server overloaded?` |
| D1-e | 若在 A 处同步加载过区块，日志会立刻走上 `Placement found via ...` → `Placed`。**看到这两行就说明 D1 失败了。** |

> **不要**在此时用 `/execute if block <A> ...` 去检查 A——那会加载 A 的区块，破坏本项测试。判定依据是日志。

### D2 重启后任务仍存在 【实机+日志】

1. 在 D1 之后（任务已入队、尚未完成），**保存并退出世界**（或重启专用服务端），再进入。

| # | 预期 |
|---|---|
| D2-a | 重新进入后，只要你**没有靠近 A**，A 处依然没有重生笼 |
| D2-b | 日志重新出现 `Loaded N home records and M deferred placements from boss_respawner_homes`，且 `M` ≥ 1 |

### D3 区块加载后任务完成 【实机+日志】

1. 承接 D1/D2，`/tp` 到 A 附近（让 A 区块加载）。

| # | 预期 |
|---|---|
| D3-a | 到达后**下一个服务器 tick**，重生笼出现在 A，日志 `Deferred placement at <A> finished with PLACED` |
| D3-b | 之后不会重复放置（再走开回来，A 处仍只有一个笼子） |

### D4 defer 不绕过 `search_failure_fallback: false` 【实机+日志】

> 验证「延迟执行 = 用快照重跑完整流程」，而不是「等了很久就直接强制放置」。

1. 临时把 `05_no_fallback.json` 的 `require_ground` 保持 `true`，并把该规则改为 `mode: home`（它已经是 home）。
2. 在空中召唤烈焰人（登记 home 在空中），走远卸载区块，然后在远处……**注意**：你需要让它在 home 区块未加载时死亡。可行做法：
   - 在空中坐标 A 召唤烈焰人，走远到 B，用 `/tp @e[type=minecraft:blaze,tag=acc] <B>` 把它拉到 B，在 B 击杀。
3. 此时 home=A 未加载 → 进队。然后走回 A。

| # | 预期 |
|---|---|
| D4-a | 走回 A 后**依然没有**重生笼（因为该规则的坐标搜索半径全 0 且 `require_ground:true` 永远搜不到，而 `search_failure_fallback:false` 禁止强制放置） |
| D4-b | 日志出现 `Deferred placement at <A> finished with ABANDONED`，任务被清除且不再重试 |

> 如果 D4 失败（笼子出现了），说明 defer 执行时绕过了 `search_failure_fallback`，属于严重缺陷。

### D5 去重：同一目标多次死亡只留一条任务 【日志】

1. 承接 D1 的场景（home=A 未加载）。
2. 再召唤**第二只**劫掠兽，也传送到 B 击杀。

| # | 预期 |
|---|---|
| D5-a | 日志两次 `Deferred respawn cage ... queue size=1`（**第二次仍是 1**，不是 2） |
| D5-b | 走到 A 后只出现**一个**重生笼 |

### D6 目标维度不可用（进阶，可选）【代码审阅 + 可选实机】

这一条**很难在普通环境里构造**：维度不可用意味着 `MinecraftServer.getLevel(dimension)` 返回 `null`，通常只在「记录指向的模组维度对应的模组被移除」时出现。

**若你有条件测试：**

1. 装一个带独立维度的模组，进入该维度，用 home 规则登记一只生物的出生点。
2. 击杀它（让它留下记录）。**（可选：让放置也进一次队）**
3. 退出游戏，移除该维度模组，重新进入。

| # | 预期 |
|---|---|
| D6-a | 日志出现 `Target dimension <ns:dim> is unavailable for the respawn cage of ...; no cage placed and no deferred task created.`（ERROR 级别） |
| D6-b | 日志**不出现** `Deferred respawn cage` —— 即没有创建那条永远无法完成的任务 |
| D6-c | 重进后该记录已被清理（日志有 `Removed home record`，或 `.dat` 中记录数减少） |

**若无法构造：** 这一条以代码审查为准——`RespawnCagePlacer.execute()` 在 `server().getLevel(...) == null` 时走 `dropHomeRecord` + `ABANDONED`，并在 `defer()` 之前返回，因此不可能入队。请在记录表中标注「未实机验证」。

---

## 6. 重生链路

### B1 重生实体继承原 Home 【实机+日志】

1. 在坐标 A 登记劫掠兽的 home，击杀它，得到 A 处的重生笼。
2. 用绿宝石右键激活，等它召唤出新劫掠兽。

| # | 预期 |
|---|---|
| B1-a | 日志 `Bound respawned entity uuid=<新UUID> to home <A> in minecraft:overworld` |
| B1-b | 再击杀这只**重生出来的**劫掠兽时，重生笼**再次出现在 A**（不是它这次死的坐标），日志 base 仍是 A |
| B1-c | 连续重生 3～4 轮，位置**不漂移**（这一条是 home 模式的核心价值） |

### B2 `addFreshEntity` 之前就已绑定（时序正确性）【日志】

| # | 预期 |
|---|---|
| B2-a | 日志中 `Bound respawned entity uuid=...` 出现在 `trySpawn added N fresh entities for ...` **之前** |
| B2-b | 若顺序反了（先 added 后 Bound），说明 join 事件可能先把生成位置写成 home；此时观察 B1-b 是否漂移即可判定 |

### B3 成功放置后删除旧记录 【日志】

| # | 预期 |
|---|---|
| B3-a | 重生笼在 A 成功放置后，日志出现 `Removed home record for <死亡实体UUID> after successful placement` |
| B3-b | 进入 defer 队列时**不**出现该行（记录被保留，直到最终放置成功） |

### B4 `count > 1` 时同一批共享同一 Home 【日志，可选】

1. 临时把 `01_home_ravager.json` 的 `spawn.count` 改成 2，`/reload`。
2. 走一遍 B1。

| # | 预期 |
|---|---|
| B4-a | 日志出现**两条** `Bound respawned entity uuid=... to home <A>`，且 home 相同 |
| B4-b | 两只都死在原地后，其重生笼都锚定 A |

### B5 已放置的笼子不受 `/reload` 影响 【实机+日志】

1. 按 A 放置一个重生笼（不要激活）。
2. 修改 `01_home_ravager.json`，例如把 `spawn.delay_ticks` 改成 200，`/reload`。
3. 右键激活该笼子。

| # | 预期 |
|---|---|
| B5-a | 召唤延迟仍是**旧值**（20 tick 左右），说明笼子用的是放置当时的规则快照 |
| B5-b | 在 A 处**新**死亡产生的笼子才使用新值 |

---

## 7. 兼容与回归

| # | 项目 | 预期 |
|---|---|---|
| C1 | 旧存档兼容 | 用改动前的存档（有 `DIM-1`/`DIM1`、无 `boss_respawner_homes.dat`）进入，无报错，首次加载后生成该 `.dat` |
| C2 | 手动放置的笼子 | `/give` 出来的笼子右键无反应（没有 BlockEntity 数据）——这是**已知的、设计内的**行为，不是本次改动引入 |
| C3 | 高亮回归 | 关闭 `highlightBossRespawner` 后无描边，且日志中不出现相关异常 |
| C4 | 配置文件生成 | 删除 `config/boss_respawner-common.toml` 后重启，新文件应只包含 `general` 与 `debug` 两组；**不得**出现 `[compat]` 组、`protectedBlockIds`、`forcePlaceOnNoSafeSpot`、`foreignCageBlockIds` |
| C5 | 配置界面本地化 | 游戏内配置界面里 `general` / `debug` 的每个条目都有中文（或英文）标签与 tooltip，无 `missing translation` 字样；**不应**再出现「受保护方块ID」这类已删除配置项的条目 |
| C6 | 与灾变共存 | 装在灾变整合包里，击杀灾变 Boss 后本模组不产生异常堆栈（灾变有自己的笼子机制，两者共存是设计内的） |
| C7 | 旧数据包被拒 | 装一个 v3 时代的第三方数据包（含 `protected_blocks`），`/reload` 后日志对每个条目给出 `unsupported schema_version 3 (expected 4)` 并提示迁移，其余条目不受影响 |

---

## 8. 记录表

请把结果填在这里回传；失败项请附 `logs/latest.log` 中对应的**连续 30～50 行**。

| 编号 | 项目 | 结果 | 备注 / 日志片段 |
|---|---|---|---|
| S1 | 冒烟 + 高亮 | ☐ 通过 ☐ 失败 | |
| R1–R4 | Schema v4 解析与拒绝（含 v3 结构） | ☐ 通过 ☐ 失败 | |
| R5 | 内置 32 条 + 验收 6 条全部加载 | ☐ 通过 ☐ 失败 | |
| H1 | 首次观察登记 | ☐ 通过 ☐ 失败 | |
| H2 | 移动不覆盖 | ☐ 通过 ☐ 失败 | |
| H3 | 旧存档首次加载登记 | ☐ 通过 ☐ 失败 | |
| H4 | `/reload` 补登记 | ☐ 通过 ☐ 失败 | |
| P1 | home 锚定出生点 | ☐ 通过 ☐ 失败 | |
| P2 | death 锚定死亡点 | ☐ 通过 ☐ 失败 | |
| P3–P4 | `force_place` 跳搜索 / 无视避让 / 旧内容蒸发 | ☐ 通过 ☐ 失败 | |
| P5 | 兜底写入覆盖被避让方块 | ☐ 通过 ☐ 失败 | |
| P6 | 两趟搜索：避让不会让笼子消失 | ☐ 通过 ☐ 失败 | 需临时改 03 的规则，**测完改回** |
| P7 | fallback=false 不放置 | ☐ 通过 ☐ 失败 | |
| P8 | keep_existing | ☐ 通过 ☐ 失败 | |
| P9 | allow_multiple 塌缩 | ☐ 通过 ☐ 失败 | |
| P10 | 灾变祭坛不再是例外 | ☐ 通过 ☐ 未验证 | 需装 Cataclysm |
| **D1** | **未加载不放置/不加载/不卡服** | ☐ 通过 ☐ 失败 | **优先级最高** |
| **D2** | **重启后任务保留** | ☐ 通过 ☐ 失败 | **优先级最高** |
| **D3** | **区块加载后完成放置** | ☐ 通过 ☐ 失败 | **优先级最高** |
| D4 | defer 不绕过 fallback | ☐ 通过 ☐ 失败 | |
| D5 | 去重 | ☐ 通过 ☐ 失败 | |
| D6 | 维度不可用 | ☐ 通过 ☐ 未验证 | 需可移除的模组维度 |
| B1–B2 | 重生继承 Home | ☐ 通过 ☐ 失败 | |
| B3 | 记录删除时机 | ☐ 通过 ☐ 失败 | |
| B4–B5 | count>1 / 快照不受 reload 影响 | ☐ 通过 ☐ 失败 | |
| C1–C7 | 兼容与回归（含配置文件已无 `[compat]`、旧数据包被拒） | ☐ 通过 ☐ 失败 | |

### 失败时请一并提供

1. `logs/latest.log` 中失败前后的连续片段（含 `[BossRespawner-Debug]` 行）。
2. 触发失败的那条 JSON 规则全文。
3. `config/boss_respawner-common.toml` 全文。
4. 若与 defer 相关，额外提供 `saves/<存档名>/data/boss_respawner_homes.dat`（可用 NBTExplorer 打开）。
5. 是否装了可选依赖（灾变 / 传奇怪物 / Mowzie's / 暮色森林 / 首领崛起）及其版本。
