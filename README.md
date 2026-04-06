# ChronoVault

A Minecraft 1.21.1 NeoForge mod providing git-like backup for worlds.

## Development

This project was developed using [OpenCode](https://github.com/anomalyco/opencode) with:
- **SpecDD** (Specification-Driven Development) — for feature design and planning
- **Superpowers** (obra/superpowers) — for [TDD](https://github.com/obra/superpowers)-based code implementation

## Features

- **Content-addressable storage**: BLAKE3 hashing for deduplication
- **Fast compression**: Zstd compression for efficient storage
- **Async backups**: Non-blocking backup operations
- **Per-world isolation**: Separate backup directories per world
- **In-game commands**: `/chronovault list` and `/chronovault info`
- **GUI restore**: Restore snapshots from within the game

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.222+
- Java 21

## Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Usage

### Commands

- `/chronovault backup` — Create a backup of the current world
- `/chronovault list` — List all snapshots
- `/chronovault info <id>` — Show snapshot details

### Configuration

The mod can be configured in `config/chrono_vault-common.toml`:
- `backupPath` — Where backups are stored
- `autoBackupEnabled` — Enable automatic backups
- `autoBackupIntervalMinutes` — Interval between auto backups
- `compressionLevel` — Zstd compression level (1-22)
- `maxSnapshots` — Maximum number of snapshots to keep

## License

Copyright (C) 2026 Cattleya

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

See [LICENSE.txt](LICENSE.txt) for details.
