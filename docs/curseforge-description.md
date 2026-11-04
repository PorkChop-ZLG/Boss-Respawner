# Universal Boss Respawner

**A standalone, data-driven revival system for bosses and mobs from other mods.**

This mod extracts Cataclysm's Boss Respawner into an independent system that can revive bosses and mobs from other mods — and it is fully data-driven. **Cataclysm is not required.**

Have you ever found a boss structure on a server, only to discover that the boss has already been defeated, leaving you to search for another one? This mod solves that problem.

Designed for modpack authors and server owners: create a data pack following the documented format, and a configured boss will generate a Boss Respawner cage when defeated. Use the configured revival item to summon the boss again, turning formerly one-time bosses into repeatable encounters.

## Built-in Mod Support

The mod ships with 30 rules. Boss names and revival items are listed below; the matching item IDs are in the built-in JSON files.

### Twilight Forest

| Boss | Revival Item |
|---|---|
| Naga | Magic Map Focus |
| Twilight Lich | Magic Map Focus |
| Minoshroom | Magic Map Focus |
| Hydra | Magic Map Focus |
| Phantom Knight | Magic Map Focus |
| Ur-Ghast | Magic Map Focus |
| Alpha Yeti | Magic Map Focus |
| Snow Queen | Magic Map Focus |
| Quest Ram | Magic Map Focus |

The eight progression bosses use Twilight Forest's own `HomePos` anchor. The cage is placed beside the boss's reward chest after the chest has spawned; the Lich replaces its chest with its spawner, so collect the loot first.

### Legendary Monsters

| Boss | Revival Item |
|---|---|
| Cloud Golem | Eye of Air |
| Possessed Paladin | Eye of Ghost |
| Annihilation Pursuer | Eye of Annihilation |
| Beheaded Knight | Eye of Ghost |
| Resurrected Knight | Eye of Ghost |
| Dune Sentinel | Eye of Sandstorm |
| Frostbitten Golem | Eye of Frost |
| Overgrown Colossus | Eye of Moss |
| Ancient Guardian | Eye of Many Ribs |
| Lava Eater | Eye of Magma |
| Skeletosaurus | Eye of Bones |
| Withered Abomination | Eye of Soul |
| Endersent | Eye of Chorus |
| Shulker Mimic | Eye of Shulker |

The Obliterator is a summoned boss and does not need a respawn cage.

### Mowzie's Mobs

| Boss | Revival Item |
|---|---|
| Ferrous Wroughtnaut | Captured Grottol |
| Frostmaw | Glowing Jelly |
| Umvuthi | Naga Fang |
| Sculptor | Bluff Rod |

### Bosses Rise

| Boss | Revival Item |
|---|---|
| Ashlord, The Infernal Dragon | Ancient Trial Key |
| Sirok, The Sandworm | Ancient Trial Key |
| Helvar, the Underworld Knight | Ancient Trial Key |

Bosses Rise's Yeti and Kraken are not supported: their custom death and ship-anchor logic cannot be reproduced reliably by a static cage.

## Jade Integration

Jade is optional. When it is installed, looking at a respawn cage shows:

- the boss it will revive;
- the required key item, with the amount when more than one is needed.

The information works on multiplayer servers and needs no extra configuration.

## Data-Driven: Add Any Boss

Every rule is a JSON file. You can support a boss from another mod without writing Java code.

### 1. Create a data pack

Create a normal data pack with a `pack.mcmeta` file:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My Boss Respawner rules"
  }
}
```

`pack_format` 48 is for Minecraft 1.21.1.

### 2. Add a rule

Place the rule at:

```text
data/my_pack/boss_respawner/entries/my_boss.json
```

A minimal rule looks like this:

```json
{
  "schema_version": 4,
  "entity": "modid:boss",
  "activation": {
    "item": "modid:key"
  },
  "placement": {
    "mode": "home",
    "home": {}
  },
  "spawn": {
    "mode": "summon",
    "summon": {}
  }
}
```

Replace `modid:boss` and `modid:key` with the IDs from the boss mod. `home` returns the boss to where it was first observed; use `"mode": "death"` with a `death` object if the boss has no fixed arena. Omit optional fields to use defaults.

### 3. Reload and test

Put the data pack in the world's `datapacks` folder and run `/reload`. Add the rule before the boss is first seen; reloading also registers currently loaded bosses. Use the built-in JSON files as complete templates.

## Installation

- **Minecraft:** 1.21.1
- **Loader:** NeoForge 21.1.219 or newer
- **Java:** 21
- Install on both the client and the server.
- Cataclysm, Twilight Forest, Legendary Monsters, Mowzie's Mobs, Bosses Rise, and Jade are all optional.
- The respawn cage is an admin/creative block: it is not craftable or breakable in survival. Use `/give` or the creative spawn-egg tab when setting up an arena.

## Configuration

The common config includes:

- a master switch for new placement and spawning;
- a glowing outline for cages, with adjustable range;
- debug logging.

Per-rule settings live in the JSON: placement mode, search ranges, key item, spawn count, duplicate handling, and more. The defaults work out of the box.

## Good to Know

- Cataclysm is not required; it is only the inspiration for the original respawner concept.
- Bosses Rise's Yeti and Kraken are intentionally unsupported and have no built-in rules.
- The Lich's reward chest is replaced by its spawner; loot it before activating the cage.
- Cages cannot be broken in survival. Test custom placements before putting them on a live server.
- Rules for mods that are not installed are skipped safely.

## Credits

Inspired by Cataclysm's boss respawner. Thanks to the authors of Cataclysm, Twilight Forest, Legendary Monsters, Mowzie's Mobs, Bosses Rise, and Jade.

All mod names and trademarks belong to their respective owners.
