![logo](Branding/title.png)

# Spawn Radar

Spawn Radar is a Fabric client-side tool for technical players who want to squeeze every drop of efficiency out of mob farms. It scans the world for vanilla spawners, groups them into activation clusters, and renders fully interactive overlays so you can design multi-spawner contraptions without guesswork.

## Highlights

- **Smart scanning** – Run `/radar:scan` to discover every spawner inside a configurable radius. Spawn Radar groups overlapping activation spheres into numbered clusters so you immediately know which grinders can share a kill chamber.
- **Interactive HUD panel** – Open chat to reveal a draggable, paginated panel that lists clusters and individual spawners. Each entry supports hover + click interactions, volume toggles, and highlight management.
- **Visualization overlays** – Enable per-spawner spawn volume, mob-cap volume, and activation-region outlines. Colours, opacity, and outline thickness are all configurable.
- **Rich command palette** – Quickly rescan, toggle highlights, inspect clusters, reset data, or open a detailed help guide. Tab completion offers context-aware suggestions for every argument.
- **Deep configuration** – A full Cloth Config screen lets you tune scanning behaviour, HUD layout, icon style, highlight defaults, colour palettes, and more. Tooltips explain every option.

## Commands

All commands are client-side (`/radar:*`).

| Command | Description |
| --- | --- |
| `/radar:scan [proximity\|size] [radius]` | Scan for spawners and rebuild clusters. Optional sorting controls ordering, optional radius (1‑256 chunks) overrides the configured default. |
| `/radar:toggle <id\|all>` | Toggle HUD highlight for a specific cluster or every cluster at once. Suggestions include `all` and each known cluster id. |
| `/radar:info <id>` | Display a rich summary of a cluster: highlight status, quick action buttons, and a list of every spawner with copyable coordinates and clickable teleport shortcuts. |
| `/radar:reset` | Clear the block bank, cached clusters, highlights, and HUD state. Useful when switching worlds or after major terrain edits. |
| `/radar:help [scan\|toggle\|info\|reset]` | Show the overview guide or drill down into per-command usage with extra details about arguments. |

## HUD & Visualization Cheatsheet

- **Cluster panel** – Open chat to reveal it, drag to reposition, use the pagination buttons to flip pages, and click entries to highlight/toggle clusters. Each spawner row includes:
  - Mob puppet or spawn-egg icon (configurable)
  - Teleport buttons that run `/tp`
  - Optional spawn-volume / mob-cap-volume toggles when the HUD is focused
- **Highlight styles** – Switch between solid boxes or outlines, choose colours and opacity, and optionally enable per-spawner spawn/mob-cap volumes (default state plus manual overrides via HUD toggles).

## Configuration

Spawn Radar exposes virtually everything through the in-game Cloth Config screen (`Mod Menu → Spawn Radar`):

- Scanning radius, thread count, auto-highlight behaviour, frustum culling, cluster sorting order
- HUD layout (element count, offsets, left/right alignment)
- Spawner icon mode (3D mob puppet vs spawn egg texture)
- Highlight style (outline vs solid), outline thickness, colour palette, per-feature opacity/colour
- Volume toggles and defaults for spawn/mob-cap visualizers
## License

Spawn Radar is licensed under the [MIT License](LICENSE). You can use, modify, and redistribute it provided you retain the original copyright notice and license text.
