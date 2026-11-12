# BTIZA-Core

Shared foundation for every BTIZA SMP plugin. Install this first. Other BTIZA plugins depend on it.

## Features

- **Shared database.** One SQLite file, `plugins/BTIZA-Core/btiza.db`, in WAL mode with `synchronous=FULL`, so committed economy changes survive a crash or power loss. All queries run on one background thread.
- **Economy engine.** Atomic `deposit`, `withdraw`, `transfer`, and admin `set`. Each runs in one transaction, checks limits, and writes an audit row. Failed operations roll back. Player commands (`/balance`, `/pay`, `/baltop`, `/eco`) and Vault integration belong to BTIZA-Economy.
- **Player records.** Created on first join with the configured starting balance. Name and last-seen time are updated on every join and quit.
- **Money formatting.** `1,000` shows as `1K`, through `1T`. Input parsing accepts `1.5k`, `$2,000`, and similar. Fractional dollars are rejected.
- **Configurable messages.** `messages.yml` uses MiniMessage. Placeholder values from players are escaped, so a name containing formatting tags cannot change the message. Missing keys fall back to the bundled defaults.
- **GUI framework.** A 54-slot menu base, and a paged variant with 28 content slots, previous and next buttons, a page indicator, back, and close. Every click is cancelled, so items cannot be taken. Each open creates a new menu instance, so no state leaks between players.
- **Protection hook.** `ProtectionService.canBuild(player, location)` uses WorldGuard when it is installed and enabled in config. If a WorldGuard check throws, the action is denied and the first error is logged with its stack trace. Without WorldGuard, the hook allows the action and the calling plugin must enforce its own rules.
- **Owner identity.** `owner.uuid` in config. Core exposes `isOwner(uuid)` for the Owner rank in later plugins.

Not in Core, by design: player commands, Vault, the ranking system, and the leaderboard. The ranking system is excluded at your request.

## Installation

1. Build with Java 21 and Maven, or download the JAR from GitHub Actions.
2. Put `BTIZA-Core-1.0.0.jar` in the server's `plugins/` folder. Do not place it in any other folder.
3. Start the server once. It creates `plugins/BTIZA-Core/config.yml` and `messages.yml`.
4. Stop the server, set `owner.uuid` in `config.yml`, then start it again.

On first start, Paper downloads the SQLite driver (`org.xerial:sqlite-jdbc`) through its library loader. The server needs outbound internet access for this step.

## Configuration

`config.yml` contains currency symbol and decimals, starting balance, maximum balance, maximum single transaction, owner name and UUID, and the WorldGuard toggle. Each option is commented in the file.

`messages.yml` contains the economy and protection messages. Edit the text freely. Keep the `{placeholder}` names unchanged.

Restart the server after editing `config.yml`. Protection provider changes always need a restart. Economy limits and messages can also be reloaded through `BtizaCore.reloadSettings()`, which BTIZA-Tools will expose as `/btiza reload`.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `btiza.bypass.protection` | op | Skips WorldGuard build checks in BTIZA features. |

BTIZA-Core has no commands, so it has no command permissions. Command permissions are added with BTIZA-Economy and BTIZA-Tools.

## Using the API from another plugin

```java
EconomyService economy = Bukkit.getServicesManager().load(EconomyService.class);
economy.deposit(player.getUniqueId(), 500, "quest reward").thenAccept(result -> {
    // Runs on the main thread.
    if (result.success()) {
        player.sendMessage("Reward paid.");
    }
});
```

Declare `BTIZA-Core` as a dependency in your `plugin.yml`. `balance()` completes with an empty optional for unknown accounts and completes exceptionally on database errors, so an error is never read as a zero balance.

## Verification status

| Area | Status |
|---|---|
| Number formatting and parsing | Tested: 38 checks pass across formatting, parsing, settings validation, UUID handling, and message rendering. |
| SQL statements | Tested: the statements taken from the Java source were run against SQLite 3.45.1. Inserts, updates, leaderboard ordering, rollback, and schema creation all behaved as expected. |
| Java type-checking of all 19 main sources | Compiled with `javac -Xlint:all` against stub declarations of the Paper, Adventure, and WorldGuard APIs. This confirms the code is internally consistent. It does not confirm the real Paper signatures. |
| JUnit suite (`src/test`) | Written, not executed here. Runs in GitHub Actions with `mvn verify`. |
| Maven build and JAR | Not built here. Maven and network access were unavailable. |
| Live server behavior | Not tested. Requires Paper 1.21.11, the SQLite driver download, and a player session. |
