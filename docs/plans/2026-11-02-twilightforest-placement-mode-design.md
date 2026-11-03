# Twilight Forest 放置模式（B 方案）设计

**日期：** 2026-11-02
**状态：** 已批准并实现（最终版 2026-11-03/04：8 条全部 force；巫妖刷怪笼在奖励箱格，其余 7 条在 HomePos；笼子与箱子同一水平面；无 home 重设）
**方案：** B —— 固定锚点 + 死亡结算 + 笼子旁置；最终 `placement.offset = [0,-1,1]`、`placement.force_place = true`、`require_ground = false`、`spawn.transform.force_place = true`；巫妖 `spawner_offset:[0,-1,0]`，其余 7 条 `[0,0,0]`；不再做 home 重设/旧笼迁移。

## 问题

暮色森林的 Boss 在死亡动画结束后才由实体自己把战利品放进奖励箱，箱子位置是 Boss 自己
`HomePos` 的下方一格。此前的实现：

- 在 `LivingDeathEvent` 就放笼，靠 `place_delay_ticks` 猜死亡动画长度（TF 各 Boss 从 20 到
  295 tick 不等）；
- 用本模组首次观察到的脚底位置当锚点，而不是 TF 的 `HomePos`；
- 给 6 只幻影骑士各放一个笼；
- 用 `summon` 复活，丢掉 TF 的 home / 队伍状态；
- 笼子可能落在奖励箱正上方，使箱子被 `isRedstoneConductor` 判定为 blocked，永远打不开。

## 目标 / 非目标

**目标**

- TF Boss 死后，在奖励箱旁边正确出现一个重生笼；笼子本身不覆盖、不清空奖励箱。**最终版由巫妖的 `spawn.transform.force_place:true` 替换 `HomePos.below()` 的奖励箱格；其余 7 条在 `HomePos`，仅当该格本身是箱子时 force 替换。这是明确接受的取舍。**
- 激活后由 TF 自己的 boss spawner 复活 Boss，home 不随复活次数漂移。
- 幻影骑士只产生 1 个笼；巫妖影子克隆不产生笼。
- 不引入 mixin；TF 未加载时本模组完全不受影响。

**非目标**

- 不要求玩家清空奖励箱才出笼（这是 B+ 的行为，未采用）。
- 不做通用“其它模组奖励箱”适配；`twilightforest` 模式是 TF 专用。
- 不修改 TF 实体或方块数据；只读 `HomePos` / `MasterLich`。

## 架构

### 新放置模式 `placement.mode: "twilightforest"`

1. `LivingDeathHandler` 在死亡条件通过后，若模式是 `twilightforest`，交给
   `TwilightSettlementService.schedule(...)`，**不在死亡事件里放笼**。
2. `schedule`：
   - 过滤 Lich 影子克隆（`MasterLich`）与非终局幻影骑士（64 格内还有存活同类）；
   - 只读实体的 `HomePos`（`{dimension, pos}`）；缺失/跨维度时退回死亡位置；
   - 目标 = `HomePos + placement.offset`（最终 `[0,-1,1]`，即奖励箱南侧一格、同一水平面）；
   - 建立一个**持久化** `PendingPlacementTask`，额外携带 `WaitForEntityUuid`、
     `DeathDimension`、`DeathPos`。
3. 等待：`PendingPlacementService` 在 `EntityLeaveLevelEvent`（`RemovalReason.KILLED`）时把任务
   转为普通待放置任务；同时每 tick 的 `stillWaitingForRemoval` 作为兜底。TF 的箱子在
   `remove(KILLED)` 内、实体真正移除之前生成，因此“已移除”保证箱子已存在。
4. 放置：沿用现有 `RespawnCagePlacer.executeDeferred` 管线（搜索、重复检查、快照、defer）。
   最终 8 条 TF 主线使用 `placement.force_place: true` 和 `[0,-1,1]`，不搜索；`search_failure_fallback:false` 保留为语义说明，在 force 路径下不参与。`spawn.transform.force_place` 为 `true`；巫妖 `spawner_offset` 为 `[0,-1,0]`，其余 7 条为 `[0,0,0]`；详见修复计划。

### `spawn.transform.at`

- 新增 `at: "cage" | "anchor"`，默认 `"cage"`（旧行为）。
- `"anchor"`：动画结束后**删掉笼子**，把目标方块放到笼子保存的 home 锚点。
- 笼子放在 `HomePos + [0,-1,1]`，spawner 放在 `HomePos`（巫妖在 `HomePos + [0,-1,0]`）；TF spawner 把新 Boss 的 home 设为原 `HomePos`，所以下一轮箱子仍在 `HomePos.below()`，不漂移。
- 锚点/目标被占据时：普通（非 force）规则会拒绝激活并保留笼子；最终 8 条全部 `spawn.transform.force_place:true`，非空箱子会被直接替换（巫妖替换奖励箱；其余 7 条只会在 `HomePos` 本身是箱子时替换），非箱子方块仍拒绝。

### 幻影骑士 / 巫妖

- 幻影骑士：只有“64 格内无其他存活 `twilightforest:knight_phantom`”的那一次死亡才建任务；
  其余 5 只只清理自己的 home 记录。箱子仍由 TF 的最后一只生成，只出 1 个笼。
- 巫妖：`MasterLich` 存在即影子克隆，跳过；本体正常结算。

## Schema

保持 `schema_version: 4`（加法变更，旧 v4 文件无需迁移）：

- `placement.mode` 新增 `"twilightforest"`；`placement.twilightforest` 与 `death` / `home`
  共用字段集。
- `spawn.transform.at` 新增，默认 `"cage"`。
- `PendingPlacementTask` 新增可选 `WaitForEntityUuid` / `DeathDimension` / `DeathPos`；
  旧任务无这些字段时按普通延迟任务加载。
- 8 条暮色主线 JSON：8 条 `spawn.mode: transform` + `at: "anchor"` + `spawn.transform.force_place:true`；巫妖 `spawner_offset:[0,-1,0]`（替换奖励箱格），其余 7 条 `[0,0,0]`（在 HomePos，奖励箱保留）；无 home 重设；谜题羊不是 TF Boss，使用 `home` + `summon`。
  `place_delay_ticks: 0`（等待实体移除取代了猜延迟）。

## 数据流

```
death → TF mode → 过滤克隆/非终局骑士 → 读 HomePos
      → 持久化任务 (target = HomePos + offset, wait = UUID)
      → 实体 remove(KILLED) → 任务 ready
      → 目标区块加载 → force 写入 → 笼子在箱子旁边
      → 激活 → 动画 → 删笼子，spawner 放到 HomePos（巫妖放到 HomePos + [0,-1,0]）
      → TF spawner 生成 Boss，home = HomePos
      → 下次死亡，箱子仍在 HomePos.below()
```

## 错误处理

- 无 `HomePos`：退回死亡位置并输出警告；箱子若在死亡位置，激活时按“锚点被占据”策略处理。
- 目标区块未加载：持久化 defer，不强制加载；重启后继续。目标在建筑高度外：立即失败并清理 home，不创建任务。
- 死亡动画期间区块卸载：任务保留 `WaitForEntityUuid`，实体重新加载并完成后触发。
- 搜索失败：只对非 force 自定义规则有意义；8 条 TF 主线用 `placement.force_place:true`，不走搜索，`search_failure_fallback:false` 只是保留语义。非 force 规则搜索失败且 fallback=false 时本次不放并警告。
- 锚点非空：最终 8 条 `spawn.transform.force_place:true`，非空箱子会被替换（巫妖替换奖励箱；其余 7 条只会在 `HomePos` 本身是箱子时替换）；非箱子方块仍拒绝。

## 测试

- 单元：`twilightforest` 模式解析、`transform.at` / `spawner_offset` / `force_place` 解析/快照、`PendingPlacementTask` 等待字段
  往返、`HomePos` / `MasterLich` NBT 契约、8 条主线 TF 内置 JSON + 谜题羊 `home` 的解析。
- 构建：`gradlew.bat test`（当前 115 项全绿）。
- 实机（2026-11-03/04 已完成）：8 个 TF 主线 Boss + 谜题羊、四个联动模组全部验证；箱子可开/笼子在旁/激活后 Boss 正确生成/home 不漂移；幻影骑士只出 1 笼；巫妖克隆不触发；8 条主线允许悬空；巫妖的奖励箱替换、其余 7 条的奖励箱保留。日志复核见 `docs/reviews/2026-11-03-code-review-0.1.0-to-f1cb657.md`。

## 后续

最终实现与历史迭代的差异见
[2026-11-02 Twilight Forest 模式问题诊断与修复计划](2026-11-02-twilightforest-mode-fix-plan.md)；该计划 §10-13 保留为历史记录。

