# AuraCraft

Craft directly from nearby chests, barrels, and storage containers without moving items into your inventory.

AuraCraft seamlessly links your crafting table to all containers within a 20-block radius. It integrates natively with the vanilla Recipe Book, respects inventory limits, and runs with zero tick lag.

---

## Features

- **Nearby Container Access**: Opening a crafting table gives you instant access to all containers within a 20-block cubic radius (chests, double chests, barrels, shulker boxes, and modded containers).
- **Vanilla Recipe Book Integration**:
  - The "Showing Craftable" filter automatically recognizes items stored in nearby containers.
  - Recipes craftable specifically thanks to surrounding chests are highlighted with a distinct green border and a custom tooltip line.
  - Clicking a recipe automatically pulls ingredients from your chests onto the crafting grid.
- **Inventory Safety & Anti-Duplication**:
  - Uses the Fabric Transfer API with atomic transactions: ingredients are only consumed upon a valid craft.
  - Full inventory friendly: when switching recipes or closing the table, grid items are returned to nearby chests instead of dropping on the floor.
  - Shift-click crafting stops immediately if your inventory runs out of space without wasting chest resources.
- **Zero Lag Architecture**:
  - Chunk-based block entity scanning: never iterates individual blocks in the world.
  - Debounced cache: scans only when opening the table or at 1-second intervals.
- **Languages Supported**:
  - English (`en_us`)
  - French (`fr_fr`)
  - Spanish (`es_es`)

---

## Configuration

The configuration file is located at `config/auracraft.json` and is created automatically on first run:

```json
{
  "radius": 20,
  "pullPriority": "PLAYER_FIRST",
  "highlightLinkedChests": true
}
```

| Option | Type | Default | Description |
|---|---|---|---|
| `radius` | Integer (1–32) | `20` | Detection radius in blocks around the crafting table. |
| `pullPriority` | String | `PLAYER_FIRST` | Consumption order: `PLAYER_FIRST` (use inventory first) or `CONTAINERS_FIRST` (use chests first). |
| `highlightLinkedChests` | Boolean | `true` | Emits subtle enchantment particles above linked chests while using the table. |

---

## Requirements

- **Minecraft**: 26.1
- **Fabric Loader**: 0.19.5 or newer
- **Fabric API**: 0.145.1+26.1 or compatible
- **Java**: 25 or newer

---

## License

This project is licensed under the MIT License.
