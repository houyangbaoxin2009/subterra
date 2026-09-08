// toterra recipe "example" — schema carried as raw td; MC-side registrar
// interprets it (schema-free by design).
type tie<data>
recipe = [
  type = "minecraft:crafting_shaped",
  pattern = [ "SS", "S " ],
  result = [ item = "minecraft:stick", count = 4 ],
]