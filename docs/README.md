# Boss-Respawner 文档索引

> **状态（2026-11-04）：** 代码审查修复与设计一致性修复完成后，当前行为以本页“当前有效”列出的文档和源码为准；“历史”文档仅记录当时的推理，不应作为实现依据。

## 当前有效

| 文档 | 用途 |
|---|---|
| [README.md](../README.md) | 玩家/整合包作者的主文档：JSON schema、三种放置模式、`avoid_blocks`、Home/TF 规则 |
| [home-respawn-design.md](home-respawn-design.md) | v4 基础设计：`death` / `home`、两趟搜索、defer、Home 登记 |
| [plans/2026-11-02-twilightforest-placement-mode-design.md](plans/2026-11-02-twilightforest-placement-mode-design.md) | 暮色森林 B 方案最终设计（8 条 force、`[0,-1,1]`、巫妖 `[0,-1,0]`、其余 7 条 `[0,0,0]`） |
| [plans/2026-11-02-twilightforest-mode-fix-plan.md](plans/2026-11-02-twilightforest-mode-fix-plan.md) | TF 修复迭代记录；**最终行为见第 14 节**，第 10-13 节保留为历史 |
| [twilight-forest-mapping.md](twilight-forest-mapping.md) | 暮色森林 9 条规则的映射与箱子策略 |
| [bosses-rise-mapping.md](bosses-rise-mapping.md) | 首领崛起 3 条已支持规则的映射；雪怪、海怪不兼容（`yeti.json` / `kraken.json` 已删除）；原版箱子避让是明确接受的限制 |
| [legendary-monsters-eye-mapping.md](legendary-monsters-eye-mapping.md) | 传奇怪物钥匙/实体映射 |
| [mowzies-mobs-mapping.md](mowzies-mobs-mapping.md) | Mowzie's Mobs 钥匙/实体映射 |
| [manual-acceptance-2026-10-16.md](manual-acceptance-2026-10-16.md) | 人工验收清单；顶部有 2026-11-03/04 实机执行状态 |
| [cataclysm-boss-respawner-comparison.md](cataclysm-boss-respawner-comparison.md) | 与 Cataclysm 的对比；顶部有 v4 更新说明，历史对比章节仅作参考 |
| [reviews/2026-11-03-code-review-0.1.0-to-f1cb657.md](reviews/2026-11-03-code-review-0.1.0-to-f1cb657.md) | 0.1.0→HEAD 代码审查、修复项与设计一致性检查 |

## 历史文档（不作为当前实现依据）

| 文档 | 说明 |
|---|---|
| [code-review-2026-09-05.md](code-review-2026-09-05.md) | v2/v3 时代审查；文中 `foreignCageBlockIds`、`protected_blocks` 等已删除 |
| [code-review-2026-09-06.md](code-review-2026-09-06.md) | v2/v3 时代复审 |
| [fix-plan-2026-09-06.md](fix-plan-2026-09-06.md) | v2/v3 时代修复方案 |
| [home-respawn-design-review-2026-10-16.md](home-respawn-design-review-2026-10-16.md) | v2 设计评审；R3 保护底线已在 v4 推翻删除 |
| [universal-boss-respawner-design-v2.md](universal-boss-respawner-design-v2.md) | v2 设计稿；顶部注明不再反映当前实现 |
| [plans/2026-09-05-universal-boss-respawner-design.md](plans/2026-09-05-universal-boss-respawner-design.md) | v1 历史设计稿 |
| [plans/2026-09-05-bug-fix-plan.md](plans/2026-09-05-bug-fix-plan.md) | v1 历史修复计划 |
| [plans/2026-09-07-respawner-glow-highlight.md](plans/2026-09-07-respawner-glow-highlight.md) | 高亮功能实现记录（功能已实现） |
| [plans/2026-10-16-deepseek-home-respawn-handoff.md](plans/2026-10-16-deepseek-home-respawn-handoff.md) | v2 交接文档；顶部注明历史边界 |

## 维护规则

1. 新行为先改代码与测试，再同步 README 和“当前有效”文档；历史文档不要直接改写成新行为，必要时在顶部加历史说明。
2. 涉及 `protected_blocks`、`Config.foreignCageBlockIds`、`Config.PROTECTED_BLOCK_IDS`、旧的“不可覆盖”底线时，先确认是否为历史叙述；当前只有 `avoid_blocks`，且它只是搜索偏好。
3. 8 条暮色主线使用 `placement.force_place:true`；`search_failure_fallback:false` 是保留语义，不是运行时保护。`spawn.transform.force_place` 与放置 force 是两个阶段，改动时不要混用。
4. 当前测试基线：`gradlew.bat test` **116 项**；构建产物 `build/libs/boss_respawner-0.1.0.jar`。
