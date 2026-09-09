// toterra worldgen "configured_feature/meadow_of_tie" — minimal configured_feature
// schema: a simple_block feature placing diamond blocks. The MC-shell registrar
// builds a vanilla ConfiguredFeature and registers it into the CONFIGURED_FEATURE
// datapack registry (the E2E gate asserts registry visibility).
type tie<data>
worldgen = [
  type = "minecraft:configured_feature",
  feature = "minecraft:simple_block",
  block = "minecraft:diamond_block",
]
