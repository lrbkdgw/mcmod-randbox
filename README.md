# Random Box / 随机战利品

NeoForge 1.20.1 mod. The first opening of a vanilla loot chest starts an unskippable slot-style reveal. Chests are Common/Rare/Epic/Legendary/Mythic (50/30/12/6.5/1.5%), contain 3/4/5/6/8 draws, emit tier-coloured particles, and unopened nearby chests emit a beacon beam.

Items receive deterministic quality from their vanilla rarity. Higher chest tiers bias each candidate by `base × (1 + quality × bonus)`, where bonuses are 0%, 50%, 100%, 250%, 1000%. Existing loot-table provenance is preserved: candidates are generated from the chest's original table. Reveal time is uniformly 0.6–1.8 seconds per item.

Commands (permission level 2):
* `/RandomBox SetNewBox <x y z> <0..5> <loot_table>` (`0` rolls normal probabilities)
* `/RandomBox GUI`

The editor adds item/weight entries to named tables in world saved data. Entries supplement that table's generated candidates.

## Build
Artifacts are produced by GitHub Actions. Locally: Java 17 and `gradle build`.
