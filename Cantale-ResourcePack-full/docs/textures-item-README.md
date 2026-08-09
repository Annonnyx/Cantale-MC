# Cantale item textures

Drop Blockbench PNGs here when ready (do not commit empty/fake binaries).

## Crate keys (CMD 100–104)

| File | CMD | Model |
|------|-----|-------|
| `crate_vote.png` | 100 | `cantale:item/crate_vote` |
| `crate_rare.png` | 101 | `cantale:item/crate_rare` |
| `crate_epic.png` | 102 | `cantale:item/crate_epic` |
| `crate_mythic.png` | 103 | `cantale:item/crate_mythic` |
| `crate_legendary.png` | 104 | `cantale:item/crate_legendary` |

Until PNGs exist, each model uses a **vanilla** `layer0` fallback (same pattern as garde armor placeholders). When ready: replace `layer0` in `assets/cantale/models/item/crate_*.json` with `cantale:item/crate_*` and add the matching PNG here, then rezip the pack.
