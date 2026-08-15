# MoreShips (Project Beacon)

Custom modular ships for **Minecraft Paper 26.2**. Place a Ship Core on water,
fit a hull, add modules, finalize — then board, sail and fight.

Vanilla boats are untouched.

## Download

Latest build: [Releases](https://github.com/AdrianDrempetic2025/MoreShips/releases)

- `MoreShips-X.Y.Z-alpha.jar` → server `plugins/` folder (Paper 26.2, Java 21)
- `MoreShips-pack.zip` → resource pack with the custom ship/cannon models
  (the server pushes it automatically when `resourcepack.url` is set in
  `config.yml`; otherwise apply it manually)

## Gameplay

1. Craft a **Small Ship Core** (boat shape + 3 iron on top) and right-click
   water with it
2. Right-click the ship with any solid block (e.g. planks) to apply the
   **hull** — hardness = more HP, slower ship
3. Right-click the ship to open the **module UI** and fit modules
4. `/moreships finalize` while looking at the ship — configuration locks,
   modules activate
5. Right-click the ship to **board** and steer (WASD, shift to dismount)

### Modules

| Module | Effect |
|---|---|
| Engine | faster + quicker acceleration (stacks, capped) |
| Health | +100 max HP, slower |
| Cannon | +1 seat, slower — a full turret (see below) |
| Seat | +1 passenger seat |
| Cargo | 27-slot storage per module |

### Cannons

- Right-click the cannon to **sit** — its inventory opens (ammo top, your
  inventory below)
- Ammo: **Cannonballs** (4 blackstone + 1 iron = 8). Fuel: any furnace fuel
  (coal = 4 charged shots). 1 cannonball = 1 shot; fuel burns only when firing
- **Left-click** fires where you look (180° arc); the barrel follows your aim
- Shift to dismount

### Recipes

Run `/moreships recipes` in game for the visual recipe book.

## Commands

`/moreships give <type>` · `info` · `finalize` · `module list|remove|move` ·
`cargo` · `cannon` · `recipes` · `reload`

## Building from source

```powershell
./gradlew build          # jar + tests (build/libs/)
python tools/make_pack.py   # resource pack (build/libs/MoreShips-pack.zip)
```

Assets (Blockbench models + textures) live in `assets/`.
