# Mowzie's Mobs Boss → 激活物品映射表

**日期：** 2026-09-07  
**用途：** 为 Universal Boss Respawner 与 Mowzie's Mobs 联动时，配置 Boss 重生笼 JSON 条目提供参考。  
**Mowzie's Mobs 源码：** `D:\Minecraft\MowziesMobs-Public`

---

## 一、映射总表

| Boss（中文） | Boss 实体 ID | 激活物品（中文） | 激活物品 ID |
|---|---:|---:|---:|
| 钢铁守护者 | `mowziesmobs:ferrous_wroughtnaut` | 捕获的岩壳居蟹 | `mowziesmobs:captured_grottol` |
| 霜冻巨兽 | `mowziesmobs:frostmaw` | 荧光胶冻 | `mowziesmobs:glowing_jelly` |
| 太阳鸟-乌姆武提 | `mowziesmobs:umvuthi` | 飞蛇毒牙 | `mowziesmobs:naga_fang` |
| 雕刻家-通臂大师 | `mowziesmobs:sculptor` | 泥峭棒 | `mowziesmobs:bluff_rod` |

---

## 二、特殊规则

### 钢铁守护者（Ferrous Wroughtnaut）

钢铁守护者的 Boss 重生笼**允许在水中生成**。

因此对应的 JSON 条目中，`placement.avoid_fluids` 应设置为 `false`，否则水下的死亡点可能因为避流体逻辑而找不到放置位置。

```json
{
  "placement": {
    "avoid_fluids": false
  }
}
```

---

## 三、源码核对依据

### 实体 ID

来源：`D:\Minecraft\MowziesMobs-Public\src\main\java\com\bobmowzie\mowziesmobs\server\entity\EntityHandler.java`

```java
REG.register("ferrous_wroughtnaut", ...)
REG.register("umvuthi", ...)
REG.register("frostmaw", ...)
REG.register("sculptor", ...)
```

### 物品 ID

来源：`D:\Minecraft\MowziesMobs-Public\src\main\java\com\bobmowzie\mowziesmobs\server\item\ItemHandler.java`

```java
REG.register("captured_grottol", ...)
REG.register("glowing_jelly", ...)
REG.register("naga_fang", ...)
REG.register("bluff_rod", ...)
```

---

## 四、后续 JSON 条目建议

当需要为本模组添加 Mowzie's Mobs 的 Boss 重生笼时，数据包目录为：

```
data/mowziesmobs/boss_respawner/entries/<任意文件名>.json
```

推荐示例骨架：

```json
{
  "schema_version": 1,
  "enabled": true,
  "entity": "mowziesmobs:ferrous_wroughtnaut",
  "activation": {
    "item": "mowziesmobs:captured_grottol"
  },
  "placement": {
    "avoid_fluids": false
  }
}
```

> 正式 JSON 文件应在本映射文档确认后单独创建。
