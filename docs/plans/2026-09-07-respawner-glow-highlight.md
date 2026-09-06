# Boss 重生笼发光高亮显示 Implementation Plan

**Goal:** 为本模组 Boss 重生笼增加“原版实体发光描边式高亮”，使玩家在配置距离内可以透过方块看到重生笼；开关与距离配置在 common TOML 中，默认开启且距离 32 格。

**Architecture:** 使用 NeoForge 1.21.1 已有的 BlockEntity 自定义描边钩子 `hasCustomOutlineRendering(Player)` + Minecraft 原版 `OutlineBufferSource`。当配置开启且玩家在距离内时，重生笼方块实体渲染器将重生笼模型通过 `OutlineBufferSource` 渲染，原版 outline 后处理管线生成白色发光描边并使其透过方块可见。

**Approach:** 方案 A（用户已确认）。不引入 Mixin、不新增自定义 shader、不修改数据驱动 JSON。

---

### Task 1: 在 common 配置中新增高亮开关与距离

**Files:**
- Modify: `src/main/java/com/zonlong/bossrespawner/Config.java`

**Steps:**
1. 在 `Config.java` 的 `[general]` 配置段中新增两个配置值：
   - `public static final ModConfigSpec.BooleanValue HIGHLIGHT_BOSS_RESPAWNER`
     - TOML 路径：`general.highlightBossRespawner`
     - 默认值：`true`
     - translation：`boss_respawner.configuration.general.highlightBossRespawner`
     - comment：说明“在客户端用原版发光描边高亮重生笼，但配置放在 common 配置中”
   - `public static final ModConfigSpec.IntValue HIGHLIGHT_BOSS_RESPAWNER_RANGE`
     - TOML 路径：`general.highlightBossRespawnerRange`
     - 默认值：`32`
     - 范围：`1..256`
     - translation：`boss_respawner.configuration.general.highlightBossRespawnerRange`
     - comment：说明“高亮显示距离，默认 32 格”
2. 不新增 client config；不新增 data-driven JSON 字段。

**Verification:**
- 运行 `.\gradlew.bat compileJava`，编译通过。
- 查看生成的 `config/boss_respawner-common.toml` 应出现：
  ```toml
  [general]
  highlightBossRespawner = true
  highlightBossRespawnerRange = 32
  ```

---

### Task 2: 在 BossRespawnerBlockEntity 中启用自定义描边

**Files:**
- Modify: `src/main/java/com/zonlong/bossrespawner/blockentity/BossRespawnerBlockEntity.java`

**Steps:**
1. 覆写 NeoForge BlockEntity 扩展方法：
   ```java
   @Override
   public boolean hasCustomOutlineRendering(Player player) {
       if (!Config.HIGHLIGHT_BOSS_RESPAWNER.getAsBoolean()) {
           return false;
       }
       int range = Config.HIGHLIGHT_BOSS_RESPAWNER_RANGE.get();
       return player.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= (double) range * range;
   }
   ```
2. 该方法用于：
   - 让 `LevelRenderer` 知道该 BlockEntity 请求 outline 后处理；
   - 作为渲染器判断“是否使用 OutlineBufferSource”的统一条件。
3. 该逻辑只影响客户端渲染；方法在服务端不会参与实际世界渲染。

**Verification:**
- 运行 `.\gradlew.bat compileJava`，编译通过。
- 无行为变化直到 Task 3 完成。

---

### Task 3: 在 BlockEntityRenderer 中通过 OutlineBufferSource 渲染重生笼模型

**Files:**
- Modify: `src/main/java/com/zonlong/bossrespawner/client/render/BossRespawnerBlockEntityRenderer.java`

**Steps:**
1. 新增 import：
   ```java
   import net.minecraft.client.renderer.OutlineBufferSource;
   ```
2. 在 `render(...)` 中判断是否启用高亮：
   ```java
   Minecraft minecraft = Minecraft.getInstance();
   MultiBufferSource cageBuffer = buffer;
   if (minecraft.player != null && be.hasCustomOutlineRendering(minecraft.player)) {
       OutlineBufferSource outline = minecraft.renderBuffers().outlineBufferSource();
       outline.setColor(255, 255, 255, 255);
       cageBuffer = outline;
   }
   ```
3. 将渲染重生笼模型的调用从 `buffer` 改为 `cageBuffer`：
   ```java
   MODEL.renderToBuffer(
       poseStack,
       cageBuffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
       packedLight,
       packedOverlay
   );
   ```
4. 幽灵生物预览与钥匙物品继续使用原 `buffer` 参数渲染，避免整个 Boss 幽灵或物品也被描边。
5. 不做任何服务端、数据包或注册表修改。

**Verification:**
- 运行 `.\gradlew.bat compileJava`，编译通过。
- 运行 `.\gradlew.bat runClient`：
  1. 获取并放置一个 Boss 重生笼，或击杀一个已配置实体生成重生笼；
  2. 让重生笼与玩家之间隔实体方块；
  3. 默认配置下应看到白色/发光描边透过方块显示；
  4. 将配置 `highlightBossRespawner = false` 后消失；
  5. 将 `highlightBossRespawnerRange` 调小并远离重生笼，确认超出距离后消失。

---

### Task 4: 更新中英文配置界面语言

**Files:**
- Modify: `src/main/resources/assets/boss_respawner/lang/en_us.json`
- Modify: `src/main/resources/assets/boss_respawner/lang/zh_cn.json`

**Steps:**
1. 在 `en_us.json` 中新增：
   ```json
   "boss_respawner.configuration.general.highlightBossRespawner": "Highlight Boss Respawner",
   "boss_respawner.configuration.general.highlightBossRespawner.tooltip": "Show a vanilla-style glowing outline around Boss Respawner blocks through walls.",
   "boss_respawner.configuration.general.highlightBossRespawnerRange": "Highlight Distance",
   "boss_respawner.configuration.general.highlightBossRespawnerRange.tooltip": "Maximum distance in blocks at which Boss Respawner blocks are outlined. Default: 32."
   ```
2. 在 `zh_cn.json` 中新增：
   ```json
   "boss_respawner.configuration.general.highlightBossRespawner": "高亮Boss重生笼",
   "boss_respawner.configuration.general.highlightBossRespawner.tooltip": "使用原版发光描边让玩家可以透过方块看到Boss重生笼。",
   "boss_respawner.configuration.general.highlightBossRespawnerRange": "高亮距离",
   "boss_respawner.configuration.general.highlightBossRespawnerRange.tooltip": "Boss重生笼高亮显示的最大距离，单位为格。默认：32。"
   ```
3. 保持 JSON 合法。

**Verification:**
- 使用 `pwsh` 解析两个 JSON 文件确认无语法错误。
- 运行 `.\gradlew.bat build`，资源打包无异常。
- 游戏内 NeoForge 配置界面可看到新配置项及 tooltip。

---

### Task 5: 更新 README 功能说明与 TOML 配置示例

**Files:**
- Modify: `README.md`

**Steps:**
1. 在功能列表新增一条：
   - “重生笼可被原版发光描边高亮，玩家可透过方块看到；开关与距离在 common 配置中调整。”
2. 更新 TOML 配置示例：
   ```toml
   [general]
   # 调试信息...
   enableMod = true
   logPlacement = false
   # 使用原版发光描边高亮 Boss 重生笼（客户端显示效果，配置在 common）
   highlightBossRespawner = true
   # 高亮显示距离，默认 32 格
   highlightBossRespawnerRange = 32
   ```
3. 说明该功能不是数据驱动 JSON 配置，而是全局 common 配置。

**Verification:**
- 阅读 README 渲染/内容无遗漏。
- `git diff README.md` 可确认只包含预期文档改动。

---

### Task 6: 清理并整体验证

**Files:**
- No source changes.

**Steps:**
1. 删除调研期间可能遗留的临时文件（如 `build/research-src/` 或意外解压到项目根目录的 `net/` 目录）。
2. 运行：
   ```bat
   gradlew.bat compileJava
   gradlew.bat build
   ```
3. 运行 `gradlew.bat runClient` 手动验收：
   - 默认配置下重生笼显示原版发光描边；
   - 关闭配置后不再显示；
   - 调整距离后仅配置距离内显示；
   - 已点亮与未点亮重生笼都显示（用户已确认目标为“所有重生笼”）。

**Verification:**
- `git status` 中不存在意外新增的 `net/` 或源码根目录 `.java` 文件。
- Gradle build 成功。

---

## Notes / Non-Goals

- 不在 `RespawnEntry` / JSON schema 中加入任何高亮配置。
- 不新增 client config 文件。
- 不修改生成逻辑、方块逻辑或服务端 NBT。
- 不引入第三方渲染库或自定义 shader。
- 若实际运行发现 BlockEntity 描边无法通过原版 outline 后处理正确呈现，将回退到方案 B 并重新讨论。
