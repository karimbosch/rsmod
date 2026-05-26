# Auric RSPS Reuse Research Log

Status: updated after the source adaptation strategy change. Compatible mature source adaptation is now preferred over handcrafted reconstruction.

## Current License Position

Auric/RSMod is currently ISC-style licensed. Direct third-party code reuse is only acceptable when
the source has a clear official license that is compatible with that direction and the attribution
requirements are preserved.

## Verified Sources

| Source | Local path | License status | Direct copy status |
| --- | --- | --- | --- |
| Alter | `.research-sources/alter` | BSD-2-Clause license file present | Allowed with attribution. High-priority RSMod-derived source. |
| RSBox | `.research-sources/rsbox` | MIT license file present | Allowed with attribution. Prefer selective adaptation. |
| Apollo | `.research-sources/apollo` | ISC-style license file present | Allowed with attribution. Prefer selective adaptation. |
| RuneJS | `.research-sources/runejs-server` | GPL-3.0 license file present | Do not copy into Auric by default. Study only. |
| Darkan server | `.research-sources/darkan-server` | GPL-3.0 license file present | Do not copy into Auric by default. Study only. |
| Darkan world server | `.research-sources/darkan-world-server` | GPL-3.0 license file present | Do not copy into Auric by default. Study only. |
| runetopic/osrs-server | `.research-sources/runetopic-osrs-server` | No license file found locally | Study only until official license is verified. |
| Near Reality OSS source | `.research-sources/near-reality-oss/RSPSProject-master` | No clear license verified locally; old Java/RuneSource-style source | Study only. Clean-room behavior rewrites only. |

## First Reading Pass Conclusions

### RSBox

Useful patterns:

- Thin engine/content split.
- Script/plugin framing.
- Action queue idea for separating player intent from execution.
- Login/session concepts, but Auric already has stable RSMod login and should not replace it.

Do not copy wholesale:

- Network/session/gamepack code conflicts with RSMod's existing stack.
- Action queue code is incomplete/minimal and not better than RSMod protected access queues.

Best Auric use:

- Reference for content/plugin boundaries.
- Selectively adapt small utility ideas only when RSMod does not already provide an equivalent.

### Apollo

Useful patterns:

- Mature modular server boundaries.
- Conservative service/tooling organization.
- Good reference for long-term infrastructure discipline.

Best Auric use:

- Architecture reference for service ownership and testability.
- Possible future tooling reuse if a specific utility is needed and attribution is added.

### RuneJS

Useful patterns:

- Strong action taxonomy: button, npc, object, item, item-on-object, item-on-item, magic-on-npc,
  player command, equipment change, prayer, region change.
- Clear cancellation model for player actions.
- Useful shop pricing and config-loader concepts.

Direct copy:

- Blocked by GPL-3.0 unless Auric intentionally accepts GPL obligations.

Best Auric use:

- Conceptual reference only. Recreate ideas in Auric-native Kotlin/RSMod style.

### runetopic/osrs-server

Useful patterns:

- Kotlin command module style.
- Ktor/Guice modular boundaries.
- NPC spawn resource loading concept.
- Zone object/floor item update organization.

Direct copy:

- Blocked until an official license is verified.

Best Auric use:

- Reference only. Useful for comparing architecture, not for code import.

### Near Reality OSS source

Useful map:

- Skills: `src/com/rs2/model/content/skills/**`; prayer bone XP behavior is in `skills/prayer/BoneBurying.java`.
- Shops: `data/content/shops.xml`, loaded by `ShopManager`; useful as shop/vendor inventory reference only.
- NPC spawns: `data/npcs/spawn-config.cfg` and `data/spawns.txt`; useful for old 317-era spawn density ideas, not direct ids.
- NPC drops/combat: `data/npcs/npcDrops*.xml` and `data/ruby/npc-combat.rb`.
- Objects: `data/content/objects.xml`, `src/com/rs2/model/objects/functions/**`, and packet dispatch in `net/packet/packets`.
- Item handlers: `src/com/rs2/model/players/item/functions/**`.
- Commands: `src/com/rs2/model/players/commands/**`; mostly old staff utility ideas.
- Wilderness/minigames/combat: `model/content/combat/**`, `model/content/minigames/**`, plus object functions such as webs/obelisk/doors.

Do not reuse:

- Networking, login/session, cache, SQL, packet handlers, old client assumptions, hardcoded data, or bundled Java 8-era jars.

Auric priority from this pass:

1. Convert command-only alpha helpers into clicked gameplay interactions.
2. Fill low-risk skilling basics such as bone burying variants and altar/object interactions.
3. Add focused starter-area NPC/shop data in RSMod TOML/resources.
4. Keep combat/minigames as behavior references after core session, interaction, and economy loops are stable.

## Immediate Reuse Policy

1. Copy no code from leaked, stolen, paid, private, unclear-license, or unclear-provenance sources into Auric.
2. Alter, RSBox, Apollo, and OpenRS2 may be copied or adapted aggressively when the code maps cleanly and attribution/license notices are preserved.
3. GPL sources may be used as behavioral references unless Auric explicitly accepts GPL obligations for copied/derived work.
4. Existing RSMod/Auric systems remain the primary integration surface; imported systems should be adapted into current lifecycle, persistence, tracing, and transaction boundaries.
5. Every copied source file or substantial snippet must include source attribution and license notice.

## Candidate Systems To Build From Research

1. Interaction taxonomy adapted from compatible mature sources around RSMod hooks:
   item, object, npc, button, item-on-item, item-on-object, item-on-npc, spell-on-target.
2. Command registry cleanup:
   preserve RSMod command hooks, but organize commands by rank and operational risk.
3. Spawn/content data discipline:
   keep static world data in TOML/resources first; add runtime admin spawn tools separately.
4. GE UI behavior:
   backend is now self-tested; next work should port/adapt the most complete compatible offer lifecycle and use GPL/no-license sources only for behavioral comparison.
