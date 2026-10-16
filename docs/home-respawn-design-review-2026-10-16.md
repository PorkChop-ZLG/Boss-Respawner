# Home 出生点模式设计 —— 评审与修改建议

**评审对象：** [`docs/home-respawn-design.md`](home-respawn-design.md)（2026-10-16）
**配套交接文档：** [`docs/plans/2026-10-16-deepseek-home-respawn-handoff.md`](plans/2026-10-16-deepseek-home-respawn-handoff.md)
**评审日期：** 2026-10-16
**评审方式：** 对照当前源码 + NeoForge 21.1.219 / Minecraft 1.21.1 反编译源码逐条核实
**证据基础：** 反编译源码解压在 `Boss-Respawner/build/tmp-review/`（该目录是构建产物，执行 `gradlew clean` 后需按下面方式重新生成）

> 重新生成反编译源码的方式：
> ```powershell
> # 先构建一次以产出 sources jar
> .\gradlew.bat compileJava
> # 再从 build/moddev/artifacts/neoforge-<版本>-sources.jar 解压 .java 文件到 build/tmp-review/
> ```

---

## 0. 总体结论

**方案方向正确，不需要推翻。** 用「本模组自己的 SavedData 记录 UUID → 出生点」代替读取第三方 `HomePos`，规避了反射和改写第三方 NBT，是正确的工程取舍；`loadedFromDisk()` 的判断、SavedData 的挂载点也经核实无误（见第 4 节）。

但有 **1 处严重技术缺陷**、**1 处内部硬矛盾**、**3 处会削弱模组核心承诺的漏洞**，以及若干需补齐的空白。以下逐条给出可直接落笔的修改建议。

### 处理优先级

| 优先级 | 条目 | 说明 |
|---|---|---|
| **必须先修** | R1、R2、R3 | R1 会伤到服务器；R2 是自相矛盾；R3 是相对现状的行为倒退 |
| **建议先修** | R4、R5、R7 | 影响模组核心承诺与长期可维护性 |
| **可文档化** | R6、R8、R9 | 至少要在 §10 或 README 写清，不能沉默 |

---

## 1. 问题总览

| 编号 | 严重度 | 问题一句话 | 需改设计文档位置 |
|---|---|---|---|
| R1 | 🔴 阻塞 | home 模式强制放置会在服务端主线程**同步生成未加载区块** | §5.1 / §5.2 / §5.3 / §4.3 |
| R2 | 🟠 高 | `placement.mode` 既被声明为「必需」又被定义为「可缺省」 | §4.1 / §4.2 |
| R3 | 🟠 高 | 强制放置不再避让其他模组方块，会覆盖灾变祭坛 | §5.3 / §5.4 / §8 |
| R4 | 🟠 高 | 默认 `home` + 「无记录不放置」与 §1 的「确保生成」承诺冲突 | §1 / §5.2 / §10 |
| R5 | 🟡 中 | §7.3 的 reload 补登记只是「建议」，漏掉会静默失效 | §7.3 |
| R6 | 🟡 中 | 共享同一 Home 的多只 Boss 会塌缩到同一个方块 | §5.2 / §10 |
| R7 | 🟡 中 | Home 记录无回收策略，且 SavedData 每次保存整表重写 | §6 / §10 |
| R8 | 🟡 中 | 「本模组尚未正式发布」这一破坏兼容的前提**待确认** | §4 顶部 |
| R9 | ⚪ 低 | 示例与正文矛盾、death 模式无完整示例、`dataVersion` 未定义 | §4.1 / §4.2 / §6 |

---

## 2. 逐条详述

### R1 🔴 home 模式的强制放置会在服务端主线程同步生成未加载区块

#### 问题

设计规定 `home` 是默认模式、`search_failure_fallback` 默认 `true`，且「搜索失败后直接放置在 `base`」。但 `base` 是 Home 位置——**Boss 死在离出生点较远的地方时，Home 所在区块并未加载**。此时：

1. 搜索阶段会**全部失败**（搜索代码只接受已加载候选）；
2. 于是必然落入「强制放置」分支；
3. 强制放置直接写方块，而方块写入会**同步加载/生成**该区块，**阻塞服务端主线程**。

这正好是这个模式想解决的那类场景，因此不是理论风险。

#### 证据链

设计侧：

- [`home-respawn-design.md:145`](home-respawn-design.md#L145) `mode` 默认 `home`
- [`home-respawn-design.md:164`](home-respawn-design.md#L164) `search_failure_fallback` 默认必须为 `true`
- [`home-respawn-design.md:195`](home-respawn-design.md#L195) 搜索失败且 fallback 为 true → 直接放置在 `base`
- [`home-respawn-design.md:210`](home-respawn-design.md#L210) 强制放置「直接在 `base` 设置重生笼方块」
- [`home-respawn-design.md:215`](home-respawn-design.md#L215) 只列了「目标维度不可用 / 坐标超出世界边界 / 底层世界操作失败」三种失败，**未考虑区块未加载**

代码侧（当前实现）：

```java
// PlacementResolver.java:83  搜索阶段用 isLoaded 拒绝未加载候选
if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) { ... return false; }
```

引擎侧（`build/tmp-review/`）：

```java
// Level.java:239   setBlock → this.getChunkAt(pos)
// Level.java:202   getChunk(x,z) → getChunk(x,z, ChunkStatus.FULL)      ← requireChunk = true
// ServerChunkCache.java:158-159
CompletableFuture<ChunkResult<ChunkAccess>> f = this.getChunkFutureMainThread(x, z, chunkStatus, requireChunk);
this.mainThreadProcessor.managedBlock(f::isDone);   // ← 主线程在此空转，直到区块生成完成
```

对照印证 —— 非加载式查询是有明确区分的：

```java
// ChunkSource.java:27-32
/** @return {@code true} if a chunk is loaded at the provided position, without forcing a chunk load. */
public boolean hasChunk(int chunkX, int chunkZ) {
    return this.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;   // ← requireChunk = false
}
```

并且 NeoForge 自己在 `EntityJoinLevelEvent` 的 javadoc 里就警告过同类问题（见第 4 节第 3 条），说明「在意外时机触发区块加载」是这个平台上的已知陷阱。

#### 建议修改

在 §5 增加一条**硬性约束**，并新增一个字段区分「搜索失败」与「目标区块未加载」两种不同情况。

1. **§5.3 强制放置**增加硬性条款：

   > 强制放置在写入方块前**必须**确认目标位置的区块已加载。**未加载时不得调用任何会触发区块加载的世界写入接口**（`setBlock`、`getBlockState` 之外的写入路径等）。区块未加载**不视为**「底层世界操作失败」，二者必须分开处理。

2. **§5.1 / §5.2** 把「基准点所在区块未加载」列为独立分支，并前置检查：

   - `force_place: true`：写入前检查 `base` 区块；未加载则按 `unloaded_target` 策略处理。
   - `force_place: false`：先检查 `base` 区块；未加载则**不必执行搜索**（必然全部失败），直接按 `unloaded_target` 处理。

3. **§4.3 新增字段**：

   | 字段 | 类型 | 默认值 | 说明 |
   |---|---|---:|---|
   | `unloaded_target` | string | `"skip"` | 目标区块未加载时的策略 |

   取值：

   - `"skip"`（默认，推荐）：不放置，输出**醒目的**服务端警告（包含实体 ID、UUID、维度、坐标），说明原因。
   - `"defer"`：记入待放置队列，在目标区块真正加载后再完成放置。

4. **若采用 `defer`**，设计里必须写明实现约束（否则实现方极可能写出内存态队列，重启即丢）：

   - 待放置队列**持久化**在同一个 SavedData 中（与 Home 记录同文件或独立 `.dat` 均可）；
   - 触发时机建议用 NeoForge `ChunkEvent.Load`；
   - 必须设置**条目上限**与**过期时间**，避免长期堆积；
   - 过期时输出警告并丢弃。

#### 备选方案与取舍

| 方案 | 评价 |
|---|---|
| 默认 `"skip"`，`"defer"` 作为可选 | **推荐**。改动最小、默认安全，且把「是否牺牲功能」的决定权交给 JSON 作者 |
| 默认 `"defer"` | 功能上最完整，但要求实现持久化队列，成本明显上升 |
| 提供 `"force_load"` 允许同步加载 | 不建议作为默认。若保留，必须显式、默认关闭，并在设计文档中写明后果（主线程停顿，最坏情况触发整片地形生成） |
| 干脆不允许 home 模式跨区块放置 | 不建议，等于放弃 home 模式的主要价值 |

---

### R2 🟠 `placement.mode` 既「必需」又「可缺省」

#### 问题

同一份文档里给了两条互斥的规定。

#### 证据

- [`home-respawn-design.md:111`](home-respawn-design.md#L111)：`entity`、`activation.item`、`placement.mode` 和对应的模式配置块**是必需的**
- [`home-respawn-design.md:145`](home-respawn-design.md#L145)：**缺少 `mode` 时，按默认值 `home` 处理**
- [`handoff:47`](plans/2026-10-16-deepseek-home-respawn-handoff.md#L47)：`mode` 默认值为 `home`

`mode` 不可能既必需又有默认值。

#### 建议修改

**推荐方案 A：`mode` 必填。**

- 保留 [:111](home-respawn-design.md#L111) 的「必需」表述；
- **删除** [:145](home-respawn-design.md#L145) 的第一条「缺少 `mode` 时，按默认值 `home` 处理」；
- 同步修改交接文档 Task 1 第 6 条（`mode` 默认值为 `home`）。

理由：schema v2 已经是一次破坏性变更，引入时把语义定死代价最低；显式 `mode` 也让数据包可读性更好，避免「忘了写 mode 就得到 home 语义」的隐式行为。

**备选方案 B：`mode` 可缺省，默认 `home`。**

- 删除 [:111](home-respawn-design.md#L111) 中的 `placement.mode` 字样，只保留 `entity`、`activation.item` 和「选中模式对应的配置块」为必需；
- 交接文档保持现状。

无论选哪个，都请保留「非法模式 → 跳过整条并 `warn`」的既有规定。

---

### R3 🟠 强制放置不再避让其他模组方块，会覆盖灾变祭坛

#### 问题

设计要求强制放置忽略 `avoid_blocks` 并删除全局 `FOREIGN_CAGE_BLOCK_IDS`。但**当前实现的强制放置是会避让的**，所以这是相对现状的行为倒退：原本会拒绝放置的位置，改完之后会被直接覆盖。

#### 证据

当前实现（强制放置分支里确实有避让检查）：

```java
// RespawnCagePlacer.java:39-52
} else {
    if (!Config.FORCE_PLACE_ON_NO_SAFE_SPOT.getAsBoolean()) { ... return false; }
    int[] offset = entry.placement().offset();
    pos = entity.blockPosition().offset(offset[0], offset[1], offset[2]);
    if (isBlockedByForeignCage(level, pos)) {          // ← 强制放置也会避让
        DebugLog.info("Forced placement blocked at {} by foreignCageBlockIds ...");
        return false;
    }
```

默认值：[`Config.java:40`](../src/main/java/com/zonlong/bossrespawner/Config.java#L40) 的 `foreignCageBlockIds` 默认含 `cataclysm:boss_respawner`。

设计侧：[`home-respawn-design.md:209-210`](home-respawn-design.md#L209-L210) 要求强制放置忽略 `avoid_blocks`；[:217-221](home-respawn-design.md#L217-L221) 要求删除全局 `FOREIGN_CAGE_BLOCK_IDS`。两者叠加 → 迁移后会**覆盖灾变的祭坛方块**。叠加 R1 的默认路径，这会是常见路径而非边角。

#### 建议修改

把**两个不同语义**拆开，不要合并成一个字段：

| 概念 | 语义 | 是否可被 `force_place` 绕过 |
|---|---|---|
| `avoid_blocks` | 搜索时的**偏好**：避开这些方块去找更好的位置 | ✅ 可以绕过（保留设计现有定义） |
| `protected_blocks` | **安全底线**：任何情况下都不得覆盖 | ❌ **不可绕过** |

具体：

1. 在 §5 新增 `protected_blocks` 的定义：命中该列表时，无论 `force_place` 与 `search_failure_fallback` 取值如何，都**放弃放置并输出 `warn`**。
2. 在 §5.4 明确写：`avoid_blocks` 与 `protected_blocks` 是**不同字段、不同语义**，不要用同一个键承担两种职责。
3. **关于配置层级（重要）**：设计希望把放置策略全部下放到 JSON，这对 `avoid_blocks` 是合理的；但 `protected_blocks` 属于**跨规则的安全底线**，不应依赖每条第三方数据包各自记得声明。

   建议：

   - **保留**一个全局 `Config.PROTECTED_BLOCK_IDS`（默认 `["cataclysm:boss_respawner"]`），语义是「本模组永不覆盖这些方块」；
   - 同时允许每条规则用自己的 `protected_blocks` **追加**（不做减法）；
   - §8「保留」列表相应加回这一项，交接文档 Task 6 同步。

   若项目所有者坚持全部下放 JSON，则必须在 §9 要求 18 条内置 JSON **全部显式声明**，并在 README 用醒目段落提示第三方数据包作者——否则「装了本模组后灾变祭坛被顶掉」会成为难以归因的 bug 报告。

---

### R4 🟠 默认 `home` + 「无记录不放置」削弱了模组的核心承诺

#### 问题

§1 承诺「保证默认情况下搜索失败后仍会尝试强制放置」，但 §5.2 又规定「无 Home 记录 → 不放置」。改默认为 `home` 之后，「没有 home 记录」这一分支会让笼子**完全不生成**，而 §1 的承诺在这个分支上不成立。

#### 证据

- [`home-respawn-design.md:28`](home-respawn-design.md#L28)：目标 6 ——「默认使用 Home 模式，并保证默认情况下搜索失败后仍会尝试强制放置」
- [`home-respawn-design.md:197`](home-respawn-design.md#L197)：无 Home 记录：不放置、不使用死亡点，并输出 `warn`
- [`home-respawn-design.md:332`](home-respawn-design.md#L332)：要求 18 个内置条目全部迁移到 `home`
- 现状兜底：[`Config.java:20`](../src/main/java/com/zonlong/bossrespawner/Config.java#L20) `forcePlaceOnNoSafeSpot` 默认 `true`

即：迁移后，全部内置联动都会依赖「必须已经登记过 Home」这一前提；一旦没登记，玩家看不到任何提示，笼子就是不出现。

#### 建议修改

**核心是修正承诺的措辞，而不是把行为改回去**（因为无记录时确实无法确定放置位置）。

1. **§1 目标 6 改写**，去掉无条件承诺，例如：

   > 6. 默认使用 Home 模式；**在存在 Home 记录的前提下**，搜索失败后仍会尝试强制放置。

2. **§5.2「无 Home 记录」分支升级为显眼错误**：不只是 `warn`，建议输出服务端 `ERROR` 或带明确前缀的 `WARN`，内容包含实体 ID、UUID、维度、死亡坐标，并提示「该实体从未被登记，请检查规则加载时机或执行 /reload」。

3. **§7.3 从「建议」升级为必做**（见 R5）——这是「无记录」最主要的产生途径，堵住它比改文案更有效。

4. **§10 已接受限制**新增一条：

   > 若某实体在任何时刻都未被本模组观察到（例如规则在其实体已加载后才通过 `/reload` 添加、且补登记未覆盖），则该实体死亡时**不会**生成重生笼。

5. **README 同步**：在 Home 模式说明处明确写出「没有 Home 记录时不会生成重生笼」，避免使用者误判为 bug。

**不建议**的方案：无记录时回退到死亡点放置。这与 §2 非目标「不在 Home 位置搜索失败后回退到 Boss 死亡位置」冲突，若确实要采用，必须先显式修改 §2 并说明理由。

---

### R5 🟡 §7.3 的 reload 补登记只是「建议」，且交接文档没有对应任务

#### 问题

[`home-respawn-design.md:298`](home-respawn-design.md#L298) 把「规则重载后扫描已加载实体补登记」写成「**建议**」。但交接文档 Task 3（[`handoff:103-117`](plans/2026-10-16-deepseek-home-respawn-handoff.md#L103-L117)）只覆盖 `EntityJoinLevelEvent`，**没有这一项**。

漏掉的后果正好接上 R4：服务器运行中用 `/reload` 新增一条 home 规则，而目标 Boss 已经在世界里、其 join 事件早已发生过 → 没有记录 → 死亡时静默不生成。`/reload` 是这个模组的主要使用方式之一，这个洞很容易被踩到。

#### 建议修改

1. §7.3 改写为**必做**，并补全约束：

   > **7.3 数据包重载后的补登记（必做）**
   >
   > 规则重载完成后，遍历各 `ServerLevel` 当前**已加载**的实体，对满足以下条件的实体补登记：
   > - 实体类型命中一条 `mode: home` 的规则；
   > - 该 UUID 尚未登记。
   >
   > 补登记使用实体的**当前位置**（与首次观察语义一致）。
   >
   > 约束：
   > - **只遍历已加载实体，不得主动加载区块**；
   > - 必须遵守 §7.1 的轻量约束，不得在回调中扫描结构或放置重生笼；
   > - 补登记完成后输出统计日志（补登记数量 / 跳过数量）。

2. 明确「服务器启动」与「数据包重载」两条路径都要覆盖：正常启动时数据包先于世界加载，join 事件通常足够；但重载路径必须显式补登记。

3. **交接文档需要新增一个任务**（见第 5 节）。

---

### R6 🟡 共享同一 Home 的多只 Boss 会塌缩到同一个方块

#### 问题

home 模式下同种 Boss 共享同一 Home，重生体也继承同一 Home。当强制放置生效时，它们会落在**同一个 `base`**；而 `duplicate.mode` 默认 `allow_multiple` 不做重复检查，方块写入会直接覆盖同格并丢弃旧 BlockEntity 数据。结果是多个笼子塌缩成一个。

现状的 `death` 模式因为各次死亡位置不同，这只是低概率边角；home 模式把它变成常态。

#### 证据

- [`home-respawn-design.md:77`](home-respawn-design.md#L77)：新 UUID 继承重生笼对应的原始 Home
- [`home-respawn-design.md:281`](home-respawn-design.md#L281)：`count > 1` 时同一批生成实体都继承同一 Home
- [`home-respawn-design.md:213`](home-respawn-design.md#L213)：`duplicate.mode` 仍保留现状语义
- [`RespawnCagePlacer.java:75`](../src/main/java/com/zonlong/bossrespawner/placement/RespawnCagePlacer.java#L75)：`level.setBlock(pos, ...)` 覆盖同格

#### 建议修改

三选一，**至少要做 A**：

- **A（最低要求，文档化）**：§10 已接受限制新增：

  > home 模式下，同一个 Home 的多次死亡会得到同一个放置基准点。若 `duplicate.mode` 为 `allow_multiple`，其结果是同一格只保留一个重生笼（后放置者覆盖先放置者），而不是并存多个。

- **B（推荐，保留「多笼共存」的设计意图）**：强制放置前先检查目标格是否已有本模组重生笼；若已有，则在**不加载区块**的前提下，于当前已加载范围内寻找最近的可用格；找不到则按失败处理并 `warn`。

- **C**：为 home 模式扩展 `duplicate.mode` 的语义（例如新增 `stack_offset` 行为）。改动面最大，除非确有需求，否则不建议。

---

### R7 🟡 Home 记录无回收策略，且 SavedData 每次保存整表重写

#### 问题

每次重生都会生成新 UUID → 新记录；而死亡实体的记录永远不会被清理（交接文档 [`:134`](plans/2026-10-16-deepseek-home-respawn-handoff.md#L134) 还明确「生成失败不提前删除 Home 数据」）。设计 §10 也没把无界增长列为已接受限制。

单条记录很小，但真正的问题是 **SavedData 保存时整表重写**：

```java
// DimensionDataStorage.java:135-138
public void save() {
    ... p_323450_.save(this.getDataFile(p_323449_), this.registries);
}
```

记录数上万后，每次自动保存都要序列化整张表。

#### 证据

- [`home-respawn-design.md:242`](home-respawn-design.md#L242)：数据结构 `Map<UUID, HomeRecord>`
- [`home-respawn-design.md:75-89`](home-respawn-design.md#L75-L89)：重生后新 UUID 继承 Home（即每次重生 +1 条记录）
- [`DimensionDataStorage.java:135-138`](../build/tmp-review/net/minecraft/world/level/storage/DimensionDataStorage.java#L135-L138)：整表重写

#### 建议修改

**把记录的生命周期绑定到「实体存活期」**，在 §6 新增一小节「记录生命周期」：

| 时机 | 动作 |
|---|---|
| 实体加入世界（`EntityJoinLevelEvent`） | `registerIfAbsent(uuid, ...)` —— 已有则不覆盖 |
| Boss 死亡且**重生笼成功放置**后 | **删除**该 UUID 的记录 |
| 重生笼成功召唤新实体 | 为新 UUID 登记**原始 Home**（不是生成位置） |
| 放置失败（含 R1 的 `skip` 分支） | **保留**记录，不得删除，避免丢失 Home |

这样表的规模大致与「当前存活且已登记的 Boss 数」成正比，而不是与历史死亡总数成正比。

理由：死亡实体的 UUID 不会再出现在世界里；而重生所需的 Home 已经由**重生笼自身的 NBT** 保存（[:281-286](home-respawn-design.md#L281-L286)），SavedData 里的记录对已死亡实体已无用途。

§10 补充一条边界说明：

> 若第三方模组使同一 UUID 的实体复活，该实体将按「首次观察」重新登记位置（原记录已在首次死亡时消费）。

---

### R8 🟡 「本模组尚未正式发布」这一破坏兼容的前提待确认

#### 问题

[`home-respawn-design.md:93`](home-respawn-design.md#L93) 以「由于本模组尚未正式发布」为由，接受 schema v1 不兼容。这个前提需要项目所有者明确确认。

#### 证据（两面都要摆出来）

**倾向「可能已分发」：**

- 存在提交 `0830d71 0.1.0版本发布！`
- [`gradle.properties:35`](../gradle.properties#L35) `mod_version=0.1.0`
- 存在公开远程仓库（`git remote` 指向 GitHub；`neoforge.mods.toml` 有 `displayURL`）
- [`README.md`](../README.md) 用大量篇幅完整记录了 v1 的 schema 与全部字段

**倾向「未分发」：**

- 仓库无任何 git tag
- README 无下载链接、无 CurseForge / Modrinth 引用
- `neoforge.mods.toml` 的 `updateJSONURL`、`issueTrackerURL` 均为注释状态

**结论：无法从仓库自行判定，必须由项目所有者确认。**

#### 建议修改

1. 请项目所有者明确回答：**0.1.0 是否已经对外分发（哪怕只是发给朋友或群里）？**
2. **若已分发**（或不确定）：
   - 保留 schema v1 的解析路径（v1 的扁平 `placement` 可映射为 `mode: death`），或
   - 至少输出**显眼的迁移警告**，并在 README / 更新日志中给出 v1 → v2 的字段对照表。
3. **若确认未分发**：
   - 把 [:93](home-respawn-design.md#L93) 的表述改成可核查的说法（例如「0.1.0 未对外分发，仅本地构建」），便于日后追溯这个决定的依据。
4. **无论哪种情况**，README 都必须包含 v1 → v2 迁移对照表，至少覆盖：
   - `schema_version`: 1 → 2
   - `placement.{offset,search_down,search_up,horizontal_radius,require_ground,avoid_fluids,avoid_blocks}` → `placement.death.*`（或 `placement.home.*`）
   - 全局 `forcePlaceOnNoSafeSpot` → `placement.<mode>.search_failure_fallback`
   - 全局 `foreignCageBlockIds` → `placement.<mode>.avoid_blocks`（及新增的 `protected_blocks`）

---

### R9 ⚪ 文档细节

1. **§4.1 顶层示例自相矛盾**：[`home-respawn-design.md:99-108`](home-respawn-design.md#L99-L108) 把 `activation` / `placement` 都写成 `{}`，但同段的 [:111](home-respawn-design.md#L111) 说它们必需。照抄这个示例会被规则校验拒绝。建议改为一个**完整可用的最小示例**。

2. **`death` 模式缺少完整嵌套示例**：§4.2 只给了 `home` 的示例，而「`mode: death` 时必须存在 `placement.death`」是新引入的结构。请补一个等价的 `death` 完整示例。

3. **§4.3 表格标题易误读**：标题是「模式配置字段」，但这些字段同时适用于 `placement.home` 与 `placement.death` 两个对象。建议明确写成「**两个模式对象共用同一字段集**」，避免实现方为两个模式各写一套不一致的字段。

4. **`dataVersion` 只提了「建议保留」**（[:254-258](home-respawn-design.md#L254-L258)），未定义当前值与不兼容时的行为。建议明确：
   - 当前值（例如 `1`）；
   - 读到**更高**版本时的行为（建议：拒绝加载并输出警告，而不是按低版本强行解析）；
   - 读到**更低**版本时是否需要迁移。

---

## 3. 设计文档需要补齐的内容

除上述各条的具体修改外，建议补充以下空白：

| 主题 | 现状 | 建议 |
|---|---|---|
| 目标区块未加载的处理 | 完全缺失（R1） | 新增 `unloaded_target` 字段与分支说明 |
| `protected_blocks` 语义 | 完全缺失（R3） | 新增字段、语义边界、与 `avoid_blocks` 的区别 |
| Home 记录生命周期 | 只有写入，没有删除（R7） | 新增 §6 小节 |
| 跨维度放置的**正向流程** | [:215](home-respawn-design.md#L215) 只提到失败处理 | 补写：死亡维度 ≠ Home 维度时如何取得目标 `ServerLevel`、何时检查维度可用性 |
| 强制放置命中 `protected_blocks` | 缺失（R3） | 明确「放弃并 warn」 |
| 补登记时机（启动 / reload） | 启动未提，reload 仅「建议」（R5） | 两者都写成必做 |
| `death` 模式完整示例 | 缺失（R9） | 补齐 |
| 解析失败的日志级别 | 统一写「warn」 | 区分「配置错误」（warn，跳过该条）与「运行时异常」（error，需要人工介入），例如 R4 的无记录情况 |

---

## 4. 已核实正确 —— 请勿改动

以下各点在本次评审中**逐条核实过**，是有依据的正确设计，修改设计时不要「顺手改掉」：

1. **`loadedFromDisk()` 不能用来跳过登记**（[:71](home-respawn-design.md#L71)）—— 正确，且理由比文档所述更硬：

   ```
   ChunkSerializer.java:429   level.addLegacyChunkEntities(...)          ← 存档加载路径
     → PersistentEntitySectionManager.java:112-117  addEntity(entity, true)
       → PersistentEntitySectionManager.java:79     new EntityJoinLevelEvent(entity, level, worldGenSpawned)
   ```

   即**凡是从存档读出的实体，`loadedFromDisk()` 一律为 `true`**。若按直觉把它当作跳过条件，旧存档中的 Boss 将永远无法登记，整个 home 模式直接失效。

2. **世界生成期新生成的实体走另一条路径**，且位置就是结构出生点 —— 这让 home 语义成立：

   ```
   ChunkStatusTasks.java:228   level.addWorldGenChunkEntities(...)
     → PersistentEntitySectionManager.java:119-124  addEntity(entity, false)   ← loadedFromDisk = false
   ```

3. **`EntityJoinLevelEvent` 的区块死锁警告必须当作硬约束保留**（支持 §7.1 的轻量要求）：

   > *"Note: This event may be called before the underlying `LevelChunk` is promoted to `ChunkStatus#FULL`. You will cause chunk loading deadlocks if you do not delay your world interactions."*
   > —— `EntityJoinLevelEvent.java:20-22`

   §7.1 的「回调中只进行轻量判断和登记，不同步扫描结构或主动加载远处区块」正好守住了这条，**建议把措辞从「不」升级为明确的禁止项**。

4. **SavedData 挂在 Overworld 的 `DimensionDataStorage`**（[:225](home-respawn-design.md#L225)）—— 是原版惯用法，不是臆造。`MapItemSavedData` / `MapIndex` 等跨维度数据就是这样存的：

   ```
   ServerLevel.java:1377   this.getServer().overworld().getDataStorage().get(...)
   ServerLevel.java:1382   this.getServer().overworld().getDataStorage().set(...)
   ServerLevel.java:1387   this.getServer().overworld().getDataStorage().computeIfAbsent(...)
   ```

5. **不反射第三方、不改写第三方实体 NBT**（§2、[:24-25](home-respawn-design.md#L24-L25)）—— 正确取舍，规避了 v1 设计中 `HomePosLocator` 的反射风险。

6. **重生继承用「新 UUID 映射回原始 Home」而不是把生成点当 Home**（[:77](home-respawn-design.md#L77)）—— 逻辑正确，是避免位置逐轮漂移的关键。

   补充建议：[`:288-292`](home-respawn-design.md#L288-L292) 列了三种实现途径，建议**把第 1 种定为唯一做法**——`type.create()` 之后、`addFreshEntity` 之前即可通过 `getUUID()` 拿到新 UUID 并先登记，这样 join 事件的 `registerIfAbsent` 自然就不会写入错误位置，比「先让它写错再覆盖」更干净、也没有时序竞态。

7. **数据库保存原始登记位置、不含 JSON 偏移**（[:58](home-respawn-design.md#L58)）—— 正确，是偏移不累积的前提。

8. **单条坏记录不应导致整个存档加载失败**（[:263](home-respawn-design.md#L263)）—— 正确，且是本模组这类「只为增强体验」的数据应遵守的原则。

---

## 5. 与交接文档需要同步的修改

评审后，[`docs/plans/2026-10-16-deepseek-home-respawn-handoff.md`](plans/2026-10-16-deepseek-home-respawn-handoff.md) 需要同步：

| 交接文档位置 | 需要的改动 | 对应条目 |
|---|---|---|
| Task 1 第 6 条 | 若采用 R2 方案 A，删除「`mode` 默认值为 `home`」 | R2 |
| Task 3 | **新增**「数据包重载后的补登记」为独立任务或子项 | R5 |
| Task 4 | 新增「重生笼成功放置后删除该 UUID 的 Home 记录」 | R7 |
| Task 5 | 新增「目标区块未加载」分支；明确不得触发区块加载 | R1 |
| Task 6 | 删除项需调整：`PROTECTED_BLOCK_IDS` 应保留而非删除 | R3 |
| Task 7 | 明确 18 条内置 JSON 是否需要显式声明 `protected_blocks` | R3 |
| 三、行为验收 | 按下表增补验收项 | R1/R3/R4/R5/R7 |

---

## 6. 修改后的字段默认值总表（可直接替换 §4.3）

两个模式对象（`placement.home` / `placement.death`）**共用同一字段集**：

| 字段 | 类型 | 默认值 | 说明 |
|---|---|---:|---|
| `offset` | int[3] | `[0,0,0]` | 相对模式基准点的世界坐标偏移 |
| `force_place` | boolean | `false` | 是否跳过搜索，直接在固定坐标放置 |
| `search_failure_fallback` | boolean | `true` | 搜索失败后是否执行强制放置 |
| `unloaded_target` | string | `"skip"` | **新增**。目标区块未加载时的策略：`skip` / `defer` |
| `search_down` | int | `32` | 向下搜索距离 |
| `search_up` | int | `16` | 向上搜索距离 |
| `horizontal_radius` | int | `4` | 水平搜索半径 |
| `require_ground` | boolean | `true` | 搜索时是否要求下方有坚实地面 |
| `avoid_fluids` | boolean | `false` | 搜索时是否避开流体 |
| `avoid_blocks` | string[] | `[]` | **搜索时**避开的方块 ID（可被 `force_place` 绕过） |
| `protected_blocks` | string[] | `[]` | **新增**。**任何情况下**都不得覆盖的方块 ID（`force_place` 也不可绕过） |

顶层字段与 `activation` / `death` / `spawn` / `duplicate` 的默认值保持不变。

---

## 7. 增补后的行为验收清单

在原「三、行为验收」基础上增补以下项：

1. **（R1）** Home 所在区块未加载、`unloaded_target = "skip"` 时：不放置，输出醒目警告，且**服务端主线程未发生区块加载**（可用 Spark 或帧耗时观察，或断言该次死亡前后目标区块仍处于未加载状态）。
2. **（R1）** `unloaded_target = "defer"` 时：目标区块加载后笼子被正确放置；服务器重启后待放置条目仍在（持久化生效）。
3. **（R1）** `force_place = true` 且目标区块未加载时，同样**不触发**区块加载。
4. **（R3）** 强制放置命中 `protected_blocks` 时放弃放置并 `warn`，目标方块保持原样（用灾变祭坛方块验证）。
5. **（R3）** `avoid_blocks` 命中但 `force_place = true` 时，**允许**覆盖（确认两个字段语义确实分离）。
6. **（R4）** 无 Home 记录的实体死亡时：不放置，且日志级别与内容足以让服主定位到「未登记」这一原因。
7. **（R5）** 服务器运行中通过 `/reload` 新增一条 home 规则后，对已加载的匹配实体执行补登记；该实体随后死亡时能正常生成重生笼。
8. **（R5）** 补登记过程**未加载任何新区块**。
9. **（R7）** 实体死亡且重生笼成功放置后，其 UUID 记录从 SavedData 中消失；而该重生笼仍能在 `count` 次召唤后正确继承 Home（即删除记录未影响重生链路）。
10. **（R7）** 放置失败时**不**删除记录。
11. **（R6）** 同一 Home 的多个 Boss 死亡后的实际笼子数量，与 `duplicate.mode` 的语义一致（若采用方案 A，则确认「只保留一个」这一行为被文档明确记录）。

---

## 8. 附：本次评审的证据来源

| 类别 | 位置 |
|---|---|
| 设计文档 | [`docs/home-respawn-design.md`](home-respawn-design.md) |
| 交接文档 | [`docs/plans/2026-10-16-deepseek-home-respawn-handoff.md`](plans/2026-10-16-deepseek-home-respawn-handoff.md) |
| 本模组源码 | `src/main/java/com/zonlong/bossrespawner/**` |
| 反编译引擎源码 | `build/tmp-review/**`（构建产物，`clean` 后需重新解压） |
| 涉及的关键文件 | `Level.java`、`ServerChunkCache.java`、`ChunkSource.java`、`ChunkSerializer.java`、`ChunkStatusTasks.java`、`PersistentEntitySectionManager.java`、`EntityJoinLevelEvent.java`、`DimensionDataStorage.java`、`ServerLevel.java` |
