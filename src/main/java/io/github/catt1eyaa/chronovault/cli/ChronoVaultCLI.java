package io.github.catt1eyaa.chronovault.cli;

import io.github.catt1eyaa.chronovault.restore.RestoreExecutor;
import io.github.catt1eyaa.chronovault.restore.RestoreResult;
import io.github.catt1eyaa.chronovault.snapshot.Manifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ChronoVault 独立命令行工具 - 用于服务器环境恢复备份。
 *
 * <p>使用方法：
 * <pre>
 * java -jar chronovault-restore.jar list &lt;backup_dir&gt;
 * java -jar chronovault-restore.jar restore &lt;backup_dir&gt; &lt;snapshot_id&gt; &lt;world_dir&gt;
 * </pre>
 */
public class ChronoVaultCLI {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsageAndExit();
        }

        String command = args[0];
        try {
            switch (command) {
                case "list" -> executeList(args);
                case "restore" -> executeRestore(args);
                case "help", "--help", "-h" -> printUsageAndExit();
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsageAndExit();
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("I/O Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(2);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            System.exit(3);
        }
    }

    private static void executeList(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: list <backup_dir>");
            System.exit(1);
        }

        Path backupDir = validatePath(args[1], "backup directory");
        RestoreExecutor executor = new RestoreExecutor(backupDir);

        List<String> snapshots = executor.listSnapshots();
        if (snapshots.isEmpty()) {
            System.out.println("No snapshots found in " + backupDir);
            return;
        }

        System.out.println("Available snapshots in " + backupDir + ":");
        System.out.println();

        for (String snapshotId : snapshots) {
            try {
                Manifest manifest = executor.loadManifest(snapshotId);
                String timestamp = formatTimestamp(manifest.timestamp());
                String description = manifest.description() != null && !manifest.description().isBlank()
                    ? manifest.description()
                    : "(no description)";
                String gameVersion = manifest.gameVersion() != null ? manifest.gameVersion() : "unknown";

                System.out.printf("  ID: %s%n", snapshotId);
                System.out.printf("    Time: %s%n", timestamp);
                System.out.printf("    Game: %s%n", gameVersion);
                System.out.printf("    Desc: %s%n", description);
                System.out.println();
            } catch (IOException e) {
                System.err.printf("  Warning: Failed to load snapshot %s: %s%n", snapshotId, e.getMessage());
            }
        }

        System.out.printf("Total: %d snapshot(s)%n", snapshots.size());
    }

    private static void executeRestore(String[] args) throws IOException {
        if (args.length < 5) {
            System.err.println("Usage: restore <backup_dir> <snapshot_id> <saves_dir> <world_name>");
            System.exit(1);
        }

        Path backupDir = validatePath(args[1], "backup directory");
        String snapshotId = args[2];
        Path savesDir = parsePath(args[3], "saves directory");
        String worldName = args[4];

        RestoreExecutor executor = new RestoreExecutor(backupDir);

        // 验证快照存在
        Manifest manifest = executor.loadManifest(snapshotId);
        System.out.println("Snapshot Info:");
        System.out.printf("  ID: %s%n", manifest.snapshotId());
        System.out.printf("  Time: %s%n", formatTimestamp(manifest.timestamp()));
        System.out.printf("  Game: %s%n", manifest.gameVersion());
        System.out.printf("  Desc: %s%n", manifest.description() != null ? manifest.description() : "(no description)");
        System.out.println();

        System.out.println("Starting restore to new world...");
        long startTime = System.currentTimeMillis();

        RestoreResult result = executor.restoreToNewWorld(snapshotId, savesDir, worldName);

        long duration = System.currentTimeMillis() - startTime;

        System.out.println();
        System.out.println("Restore completed successfully!");
        System.out.printf("  Files restored: %d%n", result.restoredFiles());
        System.out.printf("  Regions restored: %d%n", result.restoredRegions());
        System.out.printf("  Chunks restored: %d%n", result.restoredChunks());
        System.out.printf("  Duration: %.2f seconds%n", duration / 1000.0);
        System.out.println();
        System.out.println("New world created: " + result.targetWorldName());
        System.out.println("World path: " + result.targetWorldPath());
    }

    private static Path parsePath(String pathStr, String description) {
        try {
            return Paths.get(pathStr).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid " + description + ": " + pathStr, e);
        }
    }

    private static Path validatePath(String pathStr, String description) throws IOException {
        Path path = parsePath(pathStr, description);
        if (!Files.exists(path)) {
            throw new IOException(capitalize(description) + " does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new IOException(capitalize(description) + " is not a directory: " + path);
        }
        return path;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    private static String formatTimestamp(long epochSeconds) {
        return TIMESTAMP_FORMATTER.format(Instant.ofEpochSecond(epochSeconds));
    }

    private static void printUsageAndExit() {
        System.out.println("ChronoVault Restore Tool");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar chronovault-restore.jar list <backup_dir>");
        System.out.println("  java -jar chronovault-restore.jar restore <backup_dir> <snapshot_id> <world_dir>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  list      List all available snapshots in backup directory");
        System.out.println("  restore   Restore a snapshot to world directory");
        System.out.println("  help      Show this help message");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  backup_dir    Path to ChronoVault backup directory (contains objects/ and snapshots/)");
        System.out.println("  snapshot_id   Snapshot ID to restore (from list command)");
        System.out.println("  world_dir     Target Minecraft world directory (will be overwritten)");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar chronovault-restore.jar list /server/backups");
        System.out.println("  java -jar chronovault-restore.jar restore /server/backups 20260405_120000 /server/world");
        System.exit(0);
    }
}
