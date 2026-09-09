// Subterra p.2.2 seed datapack — mini_dp (pure td, no JSON).
//   Directory layout mirrors vanilla data/<ns>/<kind>/<path>.td; this manifest
//   adds metadata, tie-logic libraries and one additive entry.
type tie<data>
pack = [
  name = "mini_dp",
  title = "Mini Datapack (p.2.2 seed)",
  // tie-logic declaration: lib = tie namespace, dll = path relative to the
  // pack directory → engine.tie loads symbol <lib>$<fn> via FFM.
  tie = [
    [ lib = "dp_logic", dll = "tie/dp_logic_probe.dll" ],
  ],
  entries = [
    // lives outside data/ → registered by the manifest alone, never scanned
    [ kind = "structure", ns = "devkit", path = "obligatory_tower", file = "extra/obligatory_tower.td" ],
  ],
  rules = [ [ k = "wild", v = true ], [ k = "tick", v = 40 ] ],
]