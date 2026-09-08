// toterra recipe "example" — td crafting_shaped declaration, compiled into a
// vanilla ShapedRecipe by the MC-shell registrar and merged into the live
// RecipeManager. Schema-free by design.
type tie<data>
recipe = [
  type = "minecraft:crafting_shaped",
  pattern = [ "SS", "S " ],
  key = [
    [ k = "S", v = [ item = "minecraft:stone" ] ],
  ],
  result = [ item = "minecraft:stick", count = 4 ],
]