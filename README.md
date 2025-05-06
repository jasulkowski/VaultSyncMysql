# VaultSyncMySQL

<div align="center">
  <img src="https://iili.io/3joly1n.png" alt="VaultSyncMySQL Logo" width="200"/>
  <br>
  <strong>Simple MySQL synchronization for Vault eco players </strong>
</div>

## Features

- ✅ Events synchronization of player eco balance
- 🔌 Compatible with any economy plugin that supports Vault API
- ⚡ Lightweight and optimized for performance
- ⚙️ Configurable update intervals and database settings

## Requirements

- Bukkit/Spigot server (1.8.x - 1.20.x)
- [Vault](https://www.spigotmc.org/resources/vault.34315/) plugin
- MySQL database
- Any economy plugin that supports Vault

## Installation

1. Download the latest release from [Spigot](https://www.spigotmc.org/resources/vaultsyncmysql.124649/)
2. Place the JAR file in your server's `plugins` folder
3. Start or restart your server
4. Configure the plugin in the `config.yml` file
5. Restart your server again

## Configuration

```yaml
# VaultSyncMySQL Configuration

# Database settings
database:
  host: localhost
  port: 3306
  name: minecraft
  username: root
  password: password
  table: player_money

# Synchronization settings
# Choose synchronization method:
# - "time" - synchronize at regular intervals
# - "events" - synchronize on player join/quit events
sync-method: "time"

# Synchronization interval in minutes (only used if sync-method is "time")
sync-interval-minutes: 10

# Force full synchronization of all players (online and offline) on plugin start
force-full-sync: false

# Immediately sync new players when they join for the first time
sync-new-players-immediately: true

# Enable debug mode (more verbose logging)
debug-mode: false



```
# Support
If you encounter any issues or have suggestions, please open an issue on [GitHub](https://github.com/jasulkowski/VaultSyncMysql/issues).


