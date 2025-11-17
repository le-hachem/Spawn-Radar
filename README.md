![logo](Branding/title.png)

# Spawn Radar
Spawn Radar is a Minecraft Fabric mod that helps you locate mob spawners and group them into clusters. The mod visualizes the areas in which all spawners in a given cluster are active, making it extremely useful for designing efficient mob farms.

## Features
* Locate spawners within a configurable radius around the player.
* Group nearby spawners into clusters based on their overlapping activation areas.
* Visualize clusters and active regions for optimized mob farm design.
* Toggle the visibility of clusters or all spawners at once.
* Get detailed information about specific clusters and teleport to spawners.
* Supports multiple languages.

## Commands
`/radar:scan [sorting] [radius]`
* Scans for spawners around the player and generates clusters.
* `sorting` (optional): Determines cluster sorting. Options: `proximity`, `size`. Defaults to no sorting.
* `radius` (optional): Number of chunks to search around the player. Default is the configured `defaultSearchRadius`.

`/radar:toggle <target>`
* Toggles the visibility of spawners or clusters.
* `target`: `all` or the cluster ID to toggle.

`/radar:info <id>`
* Displays detailed information about a specific cluster.
* Lists all spawners in the cluster with coordinates.
* Click a spawner’s coordinates to teleport to it.

`/radar:reset`
* Clears all stored spawners, clusters, and regions.
* Resets the mod to its original state.

`/radar:help`
* Shows a list of available commands and usage information.

## License
This project is licensed under the [MIT License](LICENSE). You are free to use, modify, and distribute the mod as long as the original license and copyright notice are included.

