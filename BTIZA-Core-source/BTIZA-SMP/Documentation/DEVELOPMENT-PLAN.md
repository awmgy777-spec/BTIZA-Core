# BTIZA SMP — Development Plan

Server: play.btiza.net · Paper 1.21.11 · MCServerHost · Survival Economy SMP

## 1. Environment Inspection (Step 1)

| Item | Status |
|---|---|
| OS | Ubuntu 24.04.5 LTS |
| Java | OpenJDK 21.0.12 (satisfies Paper 1.21.x requirement) |
| Maven / Gradle | Not installed |
| Network access | Disabled. Cannot download Paper API, Maven Central, or the PaperMC repository |
| Uploaded server files | None. No existing plugins, configs, or world data were provided |
| Working memory | 3.9 GB RAM, 10 GB free disk |

Consequences:
- Source code can be written and structured here.
- JAR compilation is blocked until Maven and the Paper API are available. Options: enable network access for this environment, or build locally/with GitHub Actions using the provided `pom.xml`.
- Live testing on a Paper server is not possible from here. Testing will be limited to what can be verified without a server (unit tests for formatters, price math, and database logic). Anything that depends on live GUI or event behavior will be marked untested until you run it.
- Existing server plugins cannot be checked for conflicts until their file list is provided.

## 2. Architecture (Step 2)

All plugins share one core. Plugins never talk to each other's databases directly.

```
BTIZA-SMP/
  BTIZA-Core/        Shared API: database, economy service, player stats service,
                     GUI framework, config loader, number formatter, permissions,
                     logging, scheduler helpers.
  BTIZA-Economy/     Balances, /pay, /baltop, /eco, Vault economy provider.
  BTIZA-Shop/        /shop, buy/sell GUIs, configurable prices.
  BTIZA-Auction/     /ah, listings, search, history, offline-safe payouts.
  BTIZA-Crates/      /crates, /crate key, /crate give, reward preview and selection.
  BTIZA-Spawners/    Drop-as-item spawners, stacking, ownership, admin commands.
  BTIZA-Ranks/       Stats tracking (kills, deaths, playtime), automatic rank #1, leaderboard.
  BTIZA-RTP/         /rtp with safe-ground search and cooldowns.
  BTIZA-Teams/       /team create|invite|accept|leave|info|home, team chat.
  BTIZA-Scoreboard/  Sidebar scoreboard with placeholders.
  BTIZA-Tools/       TAB, chat formatting, nametag prefixes, 3x3 pickaxe, /btiza admin.
  Documentation/     This plan, permissions, commands, troubleshooting.
  Installation/      Upload instructions and install checklist.
```

Dependency graph:
- BTIZA-Core is required by every BTIZA plugin.
- Vault is optional. BTIZA-Economy registers as the economy provider when Vault is present.
- WorldGuard is optional. RTP and the 3x3 pickaxe use it to avoid protected regions when installed. Without it, they fall back to a configurable blocklist of world names and use basic claim-free checks, and this limitation will be documented.
- No BTIZA plugin depends on another BTIZA plugin except through Core interfaces.

### Shared core services

- Database: one SQLite file at `plugins/BTIZA-Core/btiza.db` in WAL mode. All writes go through a single-writer executor, so concurrent writes cannot corrupt data. Reads that touch the main thread use an in-memory cache loaded asynchronously on join.
- Economy: `EconomyService` with atomic `withdraw`, `deposit`, and `transfer`. Each transfer takes a per-UUID lock and runs as a single database transaction. Amounts are stored as integer cents-style units (long) to avoid floating-point drift.
- Stats: `StatsService` tracks kills, deaths, and playtime. Writes are batched every 30 seconds and flushed on quit and shutdown.
- Number formatting: K, M, B, T, with thresholds and suffixes read from config.
- GUI framework: a reusable 54-slot menu base with the BTIZA border (black stained glass pane), navigation row, and click dispatch that cancels all clicks by default, which blocks item-stealing exploits.
- Config: YAML per plugin, validated on load, with a reload path that does not drop open GUIs.

## 3. Build Order (Steps 3–4)

1. BTIZA-Core: database, economy service, GUI base, formatter, config loader, permission constants.
2. BTIZA-Economy: commands and Vault provider. Tested first because every money feature depends on it.
3. BTIZA-Shop: categories, paging, buy and sell confirmation, editable prices.
4. BTIZA-Ranks: stats collection, automatic rank #1, leaderboard, persistence.
5. BTIZA-Tools: TAB, chat, nametag prefixes, Owner tag.
6. BTIZA-Scoreboard: placeholders and update loop.
7. BTIZA-Teams: teams, invites, team chat, homes.
8. BTIZA-RTP: safe-ground search and cooldowns.
9. BTIZA-Spawners: drop as item, stacking, ownership, anti-duplication.
10. BTIZA-Crates: keys, rewards, selection GUI, confirmation before any reward.
11. BTIZA-Auction: listings, fees, search, history, offline payouts.
12. BTIZA-Tools (pickaxe) and /btiza admin commands.

Each plugin is built, compiled, and unit-tested before the next starts. Nothing is marked as working until it has been verified.

## 4. Key Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Item duplication through spawners, crates, or auction | Custom items carry persistent data tags. Spawner breaking only drops from a validated record. Auction listings are removed from the database before the item is handed over, inside one transaction. |
| Economy race conditions (double spend, /pay spam) | Single-writer database queue and per-UUID locks. Transactions are all-or-nothing. |
| Offline auction payouts | Sales are credited to the seller's stored balance, so the seller does not need to be online. |
| Main-thread blocking from database calls | All database work is async. Results are applied on the main thread through the Bukkit scheduler. |
| Memory leaks from GUI sessions | Sessions are removed on inventory close, quit, and shutdown. Caches have size limits. |
| Mining exploits with the 3x3 pickaxe | Each broken block is checked for permission and region protection. Blocks are only broken if the player could break them normally, and drops are generated through the normal event path. |
| Rank manipulation | Stats increment only on server-verified events (player kills and deaths, not commands). Rank #1 is recalculated automatically. |

## 5. Decisions Needed Before Coding Starts

1. Rank #1 formula. Options: (a) highest K/D with a minimum kill count, (b) highest kills, (c) weighted score such as kills plus playtime. Recommended: K/D with a minimum of 10 kills, with kills as the tiebreaker.
2. Server owner name and whether the Owner rank should be tied to a username or a UUID.
3. Existing plugins. Please list the files in your `plugins/` folder. Overlapping plugins such as EssentialsX, ChestShop, or an existing economy must be disabled or removed for these plugins to work correctly.
4. Whether WorldGuard and Vault are installed or should be treated as required.
5. Currency symbol and display name (default: `$`).
6. Premium category contents. The brief says "special items" with no list. Please provide a list or approve placeholder defaults that you can edit.
7. Network access. Compilation requires it, or you can accept GitHub Actions or a local build.

## 6. Deliverables Checklist (Step 8–10)

- [ ] Source code per module
- [ ] Compiled JARs (pending build environment)
- [ ] Default config files per plugin
- [ ] SQLite schema and migration notes
- [ ] Permission list (Documentation/PERMISSIONS.md)
- [ ] Command list (Documentation/COMMANDS.md)
- [ ] Dependency list (Documentation/DEPENDENCIES.md)
- [ ] Test results, marked honestly as unit-tested or live-tested
- [ ] Troubleshooting guide
- [ ] Upload instructions for MCServerHost (Installation/)
- [ ] Safe restart procedure
