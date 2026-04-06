# ChronoVault - AGENTS.md

ChronoVault is a Minecraft 1.21.1 NeoForge mod (Java 21) providing git-like backup for worlds. Uses BLAKE3 hashing, Zstd compression, and content-addressable storage.

## Build / Test Commands

Use `./gradlew` (Linux/macOS) or `gradlew.bat` (Windows).

**Windows Git Bash + ctx7**: Use `powershell -Command "npx ctx7@latest ..."` for ctx7 CLI (path resolution issue).

| Command | Description |
|---|---|
| `gradlew build` | Compile, test, and build mod JAR |
| `gradlew test` | Run all JUnit 5 tests |
| `gradlew test --tests "*ClassName"` | Run single test class |
| `gradlew test --tests "*ClassName.testMethod"` | Run single test method |
| `gradlew runClient` / `gradlew runServer` | Launch Minecraft with mod |
| `gradlew runGameTestServer` | Run GameTests then exit |
| `gradlew runWriterDemo` | Verify Anvil round-trip writing |
| `gradlew runObjectPoolDemo` | Run ObjectPool demo |
| `gradlew compareFiles` | Compare original and round-trip files |
| `gradlew runBlake3Verify` | Verify BLAKE3 hash values |

## Architecture Notes

### Restore Creates NEW World
`RestoreExecutor.restoreToNewWorld()` always creates a **new** world directory. Original world is never modified. Naming: `<sourceWorldName>-restored-<snapshotId>`, with `-2`, `-3`... on conflict.

### Per-World Backup Isolation
Backups are stored at `backups/<worldName>/objects/` and `backups/<worldName>/snapshots/`. Use `BackupPathResolver.resolve(backupRoot, worldName)` to compute paths. World names are sanitized by `sanitizeWorldName()`.

### Anvil Files in Multiple Directories
Minecraft stores Anvil files in three places per dimension: `region/`, `entities/`, and `poi/`. All three are scanned and backed up independently.

## Package Structure

```
io.github.catt1eyaa/
  ChronoVault.java              # @Mod entry point
  ChronoVaultClient.java        # Client entry point
  Config.java                   # NeoForge mod config
  chronovault/
    backup/                     # WorldScanner, BackupExecutor, AsyncBackupService
    region/                     # AnvilReader, AnvilWriter
    snapshot/                   # Manifest, ManifestSerializer
    storage/                    # ObjectPool, BackupPathResolver
    command/                     # ChronoVaultCommands
    restore/                     # RestoreExecutor, RestoreResult
    client/                     # RestoreSnapshotsScreen
    cli/                         # ChronoVaultCLI (integrated into mod JAR)
    util/                        # HashUtil, CompressionUtil
```

## Key Conventions

- **Comments**: Written in Chinese
- **Filesystem**: Use `Path` not `File`
- **Error handling**: `IllegalArgumentException` for bad args, `IOException` for I/O failures
- **Thread safety**: Use atomic moves (`Files.move` with `ATOMIC_MOVE`) for concurrent safety
- **Mixins**: Client-only mixin for EditWorldScreen; add new mixins to `chrono_vault.mixins.json`
- **Tests**: Live in `src/test/java/` mirroring main package; `@TempDir` for temp directories

## Dependencies (jarJar + dev runtime)

```groovy
jarJar(implementation('org.bouncycastle:bcprov-jdk18on:1.78.1'))   // BLAKE3
jarJar(implementation('com.github.luben:zstd-jni:1.5.6-3'))        // Zstd
additionalRuntimeClasspath 'org.bouncycastle:bcprov-jdk18on:1.78.1'
additionalRuntimeClasspath 'com.github.luben:zstd-jni:1.5.6-3'
```

## Git Commit 规范

- **格式**: `type: description`（英文冒号），如 `i18n: add zh_cn.json`、`fix: resolve NPE in backup`
- **颗粒度**: 小步提交，每完成一个独立任务就commit
- **时机**: 
  - 完成一个功能或bug修复后
  - 写完一个测试类后
  - 添加独立的小改动后
- **避免**: 一次提交大量不相关文件（用户会要求只提交部分）
- **提交前检查**: `git status` 查看变更内容，确保只包含相关文件
