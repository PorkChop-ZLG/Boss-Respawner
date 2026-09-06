# 传奇怪物（Legendary Monsters）Boss/精英怪 → 眼球物品映射表

本文档用于后续制作“Boss 重生笼联动”时的参考，列出传奇怪物模组中：

- 3 个 Boss；
- 12 个精英怪；
- 与它们对应的 12 个眼球物品。

眼球物品允许重复：同一个眼球物品可以对应多个 Boss/精英怪。

---

## 一、眼球物品 → 结构 → 生物总表

| 眼球物品 | 物品 ID | 追踪结构 | 对应生物 | 类型 |
|---|---|---:|---|---|
| Eye of Air | `legendary_monsters:eye_of_air` | `legendary_monsters:cloudy_temple` | Cloud Golem（云筑魔像） | Boss |
| Eye of Annihilation | `legendary_monsters:eye_of_annihilation` | `legendary_monsters:space_station` | The Obliterator（湮灭构造体） | Boss |
| Eye of Annihilation | `legendary_monsters:eye_of_annihilation` | `legendary_monsters:space_station` | Annihilation Pursuer（湮灭猎影） | 精英怪 |
| Eye of Bones | `legendary_monsters:eye_of_bones` | `legendary_monsters:skeletosaurus_nest` | Skeletosaurus（骸骨巨龙） | 精英怪 |
| Eye of Chorus | `legendary_monsters:eye_of_chorus` | `legendary_monsters:ancient_tower_remains` | Endersent（紫颂遣使） | 精英怪 |
| Eye of Frost | `legendary_monsters:eye_of_frost` | `legendary_monsters:frostbitten_temple` | Frostbitten Golem（霜冻傀儡） | 精英怪 |
| Eye of Ghost | `legendary_monsters:eye_of_ghost` | `legendary_monsters:collapsed_kingdom` | Possessed Paladin（堕落圣骑） | Boss |
| Eye of Ghost | `legendary_monsters:eye_of_ghost` | `legendary_monsters:collapsed_kingdom` | Beheaded Knight（无头骑士） | 精英怪 |
| Eye of Ghost | `legendary_monsters:eye_of_ghost` | `legendary_monsters:collapsed_kingdom` | Resurrected Knight（复生骑士） | 精英怪 |
| Eye of Magma | `legendary_monsters:eye_of_magma` | `legendary_monsters:lava_eater_spawn` | Lava Eater（噬焰蜥） | 精英怪 |
| Eye of Many Ribs | `legendary_monsters:eye_of_many_ribs` | `legendary_monsters:ancient_stronghold` | Ancient Guardian（荒古守卫者） | 精英怪 |
| Eye of Moss | `legendary_monsters:eye_of_moss` | `legendary_monsters:mossy_temple` | Overgrown Colossus（蔓生巨像） | 精英怪 |
| Eye of Sandstorm | `legendary_monsters:eye_of_sandstorm` | `legendary_monsters:ruined_pyramid` | Dune Sentinel（沙丘哨兵） | 精英怪 |
| Eye of Shulker | `legendary_monsters:eye_of_shulker` | `legendary_monsters:shulker_tower` | Shulker Mimic（潜影拟态者） | 精英怪 |
| Eye of Soul | `legendary_monsters:eye_of_soul` | `legendary_monsters:soul_fortress_remains` | Withered Abomination（凋零恶煞） | 精英怪 |

---

## 二、按 3 个 Boss 查看

| Boss | 实体 ID | 对应眼球 | 结构 |
|---|---|---:|---|
| Cloud Golem（云筑魔像） | `legendary_monsters:cloud_golem` | `legendary_monsters:eye_of_air` | `cloudy_temple` |
| Possessed Paladin（堕落圣骑） | `legendary_monsters:posessed_paladin` | `legendary_monsters:eye_of_ghost` | `collapsed_kingdom` |
| The Obliterator（湮灭构造体） | `legendary_monsters:the_obliterator` | `legendary_monsters:eye_of_annihilation` | `space_station` |

---

## 三、按 12 个精英怪查看

| 精英怪 | 实体 ID | 对应眼球 | 结构 |
|---|---|---:|---|
| Dune Sentinel（沙丘哨兵） | `legendary_monsters:dune_sentinel` | `legendary_monsters:eye_of_sandstorm` | `ruined_pyramid` |
| Frostbitten Golem（霜冻傀儡） | `legendary_monsters:frostbitten_golem` | `legendary_monsters:eye_of_frost` | `frostbitten_temple` |
| Overgrown Colossus（蔓生巨像） | `legendary_monsters:overgrown_colossus` | `legendary_monsters:eye_of_moss` | `mossy_temple` |
| Ancient Guardian（荒古守卫者） | `legendary_monsters:ancient_guardian` | `legendary_monsters:eye_of_many_ribs` | `ancient_stronghold` |
| Lava Eater（噬焰蜥） | `legendary_monsters:lava_eater` | `legendary_monsters:eye_of_magma` | `lava_eater_spawn` |
| Skeletosaurus（骸骨巨龙） | `legendary_monsters:skeletosaurus` | `legendary_monsters:eye_of_bones` | `skeletosaurus_nest` |
| Withered Abomination（凋零恶煞） | `legendary_monsters:withered_abomination` | `legendary_monsters:eye_of_soul` | `soul_fortress_remains` |
| Endersent（紫颂遣使） | `legendary_monsters:endersent` | `legendary_monsters:eye_of_chorus` | `ancient_tower_remains` |
| Shulker Mimic（潜影拟态者） | `legendary_monsters:shulker_mimic` | `legendary_monsters:eye_of_shulker` | `shulker_tower` |
| Annihilation Pursuer（湮灭猎影） | `legendary_monsters:annihilation_pursuer` | `legendary_monsters:eye_of_annihilation` | `space_station` |
| Beheaded Knight（无头骑士） | `legendary_monsters:beheaded_knight` | `legendary_monsters:eye_of_ghost` | `collapsed_kingdom` |
| Resurrected Knight（复生骑士） | `legendary_monsters:resurrected_knight` | `legendary_monsters:eye_of_ghost` | `collapsed_kingdom` |

---

## 四、眼球物品重复汇总

| 眼球物品 | 复用的怪物 |
|---|---|
| `eye_of_annihilation` | The Obliterator、Annihilation Pursuer |
| `eye_of_ghost` | Possessed Paladin、Beheaded Knight、Resurrected Knight |

其余 10 个眼球物品各对应 1 个怪物。

---

## 五、映射依据

1. 眼球物品源码位于：

   ```text
   D:\Minecraft\Legendary-Monsters-1.21.1-NeoForge\src\main\java\net\miauczel\legendary_monsters\item\custom\Eyes
   ```

   每个眼球物品通过 `findNearestMapStructure` 追踪对应的结构 tag。

2. 结构 tag 定义位于：

   ```text
   D:\Minecraft\Legendary-Monsters-1.21.1-NeoForge\src\main\java\net\miauczel\legendary_monsters\tag\ModStructureTags.java
   ```

3. 结构 tag JSON 位于：

   ```text
   D:\Minecraft\Legendary-Monsters-1.21.1-NeoForge\src\main\resources\data\legendary_monsters\tags\worldgen\structure
   ```

4. 怪物所属结构分组参考源码包路径：

   ```text
   net.miauczel.legendary_monsters.entity.AnimatedMonster.Mobs.*
   ```

   例如：

   - `Mobs.AncientStronghold` → Ancient Guardian；
   - `Mobs.CollapsedKingdom` → Possessed Paladin、Beheaded Knight、Resurrected Knight；
   - `Mobs.SpaceStation` → Annihilation Pursuer；
   - `Mobs.RuinedPyramid` → Dune Sentinel；
   - `Mobs.ShulkerTower` → Shulker Mimic；
   - `Mobs.Chorusling` → Endersent。

5. 参考数值表：

   ```text
   D:\BeLoong\.minecraft\versions\BeLoong\docs\legendary-monsters-stats.md
   ```

---

## 六、后续 BossRespawner 配置建议

后续制作联动时，可将对应眼球物品作为 Boss 重生笼的激活物品。  
例如：

```json
{
  "entity": "legendary_monsters:cloud_golem",
  "activation": {
    "item": "legendary_monsters:eye_of_air"
  }
}
```

对于同一个眼球复用多个怪物的情况，可在多个 `RespawnEntry` 中重复使用同一 `activation.item`，这符合“眼球物品允许重复”的设计。
