// toterra loot table "chest/bonus" — td chest schema with a single pool and two
// weighted entries; the MC-shell registrar builds a vanilla LootTable from it
// (LootDataManager merge deferred to the deep-wiring block).
type tie<data>
loot_table = [
  type = "minecraft:chest",
  pools = [
    [
      rolls = 1,
      entries = [
        [ item = "minecraft:emerald", weight = 2 ],
        [ item = "minecraft:diamond", weight = 1 ],
      ],
    ],
  ],
]