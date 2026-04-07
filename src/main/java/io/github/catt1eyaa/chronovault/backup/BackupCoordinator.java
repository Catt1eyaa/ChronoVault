/*
 * Copyright (C) 2026 Cattleya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.catt1eyaa.chronovault.backup;

import io.github.catt1eyaa.Config;
import io.github.catt1eyaa.chronovault.storage.BackupPathResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * 备份协调器 - 统一管理所有备份入口
 *
 * <p>功能：</p>
 * <ul>
 *   <li>提供统一的备份接口（手动/自动备份共用）</li>
 *   <li>全局备份锁防止并发备份</li>
 *   <li>会话锁验证（storageAccess.checkLock()）</li>
 *   <li>可配置的 save-all 行为</li>
 *   <li>实现快照清理策略</li>
 *   <li>磁盘空间预检查</li>
 * </ul>
 *
 * <p>线程安全：此类是线程安全的，可以从多个线程调用。</p>
 *
 * <p>参考 SimpleBackups 的 BackupThread/CompressionBase 设计。</p>
 */
public class BackupCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupCoordinator.class);

    /**
     * 磁盘空间缓冲区大小（128MB）
     * 参考 SimpleBackups 的 BACKUP_BUFFER_SIZE
     */
    private static final long DISK_SPACE_BUFFER = 128L * 1024 * 1024;

    /**
     * JVM 参数：禁用备份
     * 参考 SimpleBackups 的 disableBackups 参数
     */
    private static final String DISABLE_BACKUPS_PROPERTY = "chronovault.disableBackups";

    private final MinecraftServer server;
    private final LevelStorageSource.LevelStorageAccess storageAccess;
    private final Path backupRoot;
    private final int compressionLevel;
    private final int maxSnapshots;

    private final AtomicBoolean backupInProgress = new AtomicBoolean(false);
    private final ExecutorService executor;

    /**
     * 创建备份协调器
     *
     * @param server           Minecraft 服务器实例
     * @param storageAccess    世界存储访问器（用于 checkLock）
     * @param backupRoot       备份根目录
     * @param compressionLevel Zstd 压缩级别（1-22）
     * @param maxSnapshots     最大快照数（0 表示无限）
     */
    public BackupCoordinator(MinecraftServer server, LevelStorageSource.LevelStorageAccess storageAccess,
                             Path backupRoot, int compressionLevel, int maxSnapshots) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        this.storageAccess = Objects.requireNonNull(storageAccess, "storageAccess cannot be null");
        this.backupRoot = Objects.requireNonNull(backupRoot, "backupRoot cannot be null");
        this.compressionLevel = compressionLevel;
        this.maxSnapshots = maxSnapshots;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChronoVault-Backup");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 检查是否通过 JVM 参数禁用了备份
     *
     * @return true 如果备份被禁用
     */
    public static boolean isBackupDisabled() {
        return "true".equalsIgnoreCase(System.getProperty(DISABLE_BACKUPS_PROPERTY));
    }

    /**
     * 检查是否有备份正在进行
     *
     * @return true 如果有备份正在进行
     */
    public boolean isBackupInProgress() {
        return backupInProgress.get();
    }

    /**
     * 执行同步备份
     *
     * <p>此方法会阻塞直到备份完成。适用于自动备份。</p>
     *
     * @param description      备份描述（可为 null）
     * @param progressListener 进度回调（可为 null）
     * @return 备份结果
     */
    public BackupResult backupSync(String description, BackupProgressListener progressListener) {
        if (isBackupDisabled()) {
            LOGGER.info("Backups are disabled via JVM argument");
            return BackupResult.failure(
                    new BackupResult.BackupStats(0, 0, 0, 0, 0, 0, 0),
                    List.of("Backups are disabled via -D" + DISABLE_BACKUPS_PROPERTY + "=true")
            );
        }

        if (!backupInProgress.compareAndSet(false, true)) {
            LOGGER.warn("Backup already in progress, skipping");
            return BackupResult.failure(
                    new BackupResult.BackupStats(0, 0, 0, 0, 0, 0, 0),
                    List.of("Another backup is already in progress")
            );
        }

        try {
            return executeBackup(description, progressListener);
        } finally {
            backupInProgress.set(false);
        }
    }

    /**
     * 执行异步备份
     *
     * <p>备份在后台线程执行，立即返回 CompletableFuture。</p>
     *
     * @param description      备份描述（可为 null）
     * @param progressListener 进度回调（可为 null）
     * @return 备份结果的 Future
     */
    public CompletableFuture<BackupResult> backupAsync(String description, BackupProgressListener progressListener) {
        if (isBackupDisabled()) {
            return CompletableFuture.completedFuture(BackupResult.failure(
                    new BackupResult.BackupStats(0, 0, 0, 0, 0, 0, 0),
                    List.of("Backups are disabled via -D" + DISABLE_BACKUPS_PROPERTY + "=true")
            ));
        }

        if (!backupInProgress.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(BackupResult.failure(
                    new BackupResult.BackupStats(0, 0, 0, 0, 0, 0, 0),
                    List.of("Another backup is already in progress")
            ));
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeBackup(description, progressListener);
            } finally {
                backupInProgress.set(false);
            }
        }, executor);
    }

    /**
     * 执行备份的核心逻辑
     * 参考 SimpleBackups 的 CompressionBase.makeBackup()
     */
    private BackupResult executeBackup(String description, BackupProgressListener progressListener) {
        try {
            // 1. 验证会话锁（参考 SimpleBackups）
            // 确保当前会话拥有世界的锁，防止多实例同时访问
            storageAccess.checkLock();

            // 2. 磁盘空间预检查
            Path worldBackupDir = getWorldBackupDir();
            if (!checkDiskSpace(worldBackupDir)) {
                return BackupResult.failure(
                        new BackupResult.BackupStats(0, 0, 0, 0, 0, 0, 0),
                        List.of("Insufficient disk space (less than " + (DISK_SPACE_BUFFER / 1024 / 1024) + "MB available)")
                );
            }

            // 3. 可配置的 save-all（参考 SimpleBackups 的 CommonConfig.saveAll()）
            if (Config.isSaveAllBeforeBackup()) {
                server.executeBlocking(() -> server.saveEverything(true, false, true));
            }

            // 4. 执行备份
            Path worldDir = getWorldDir();
            BackupExecutor backupExecutor = new BackupExecutor(worldBackupDir, compressionLevel);
            BackupResult result = backupExecutor.execute(
                    worldDir,
                    server.getServerVersion(),
                    description != null ? description : "",
                    progressListener,
                    () -> false
            );

            // 5. 如果备份成功，执行快照清理
            if (result.success() && maxSnapshots > 0) {
                cleanupOldSnapshots(worldBackupDir);
            }

            return result;

        } catch (Exception e) {
            LOGGER.error("Backup failed with exception", e);
            return BackupResult.failure(
                    new BackupResult.BackupStats(0, 0, 0, 0, 0, 0, 0),
                    List.of("Backup failed: " + e.getMessage())
            );
        }
    }

    /**
     * 关闭协调器，释放资源
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取当前世界目录
     */
    private Path getWorldDir() {
        Path levelDatPath = server.getWorldPath(LevelResource.LEVEL_DATA_FILE);
        Path worldDir = levelDatPath.getParent();
        if (worldDir == null || worldDir.getFileName() == null) {
            throw new IllegalStateException("Unable to resolve world directory");
        }
        return worldDir;
    }

    /**
     * 获取当前世界的备份目录
     */
    private Path getWorldBackupDir() {
        Path worldDir = getWorldDir();
        String worldName = worldDir.getFileName().toString();
        return BackupPathResolver.resolve(backupRoot, worldName);
    }

    /**
     * 检查磁盘空间是否充足
     *
     * @param backupDir 备份目录
     * @return true 如果磁盘空间充足
     */
    private boolean checkDiskSpace(Path backupDir) {
        try {
            // 确保目录存在
            Files.createDirectories(backupDir);

            FileStore fileStore = Files.getFileStore(backupDir);
            long usableSpace = fileStore.getUsableSpace();

            if (usableSpace < DISK_SPACE_BUFFER) {
                LOGGER.warn("Insufficient disk space: {:.2f} MB available, {:.2f} MB required",
                        usableSpace / 1024.0 / 1024.0,
                        DISK_SPACE_BUFFER / 1024.0 / 1024.0);
                return false;
            }

            return true;
        } catch (IOException e) {
            LOGGER.warn("Failed to check disk space", e);
            // 如果无法检查，继续执行备份
            return true;
        }
    }

    /**
     * 清理旧快照，保留最新的 maxSnapshots 个
     *
     * @param worldBackupDir 世界备份目录
     */
    private void cleanupOldSnapshots(Path worldBackupDir) {
        Path snapshotsDir = worldBackupDir.resolve("snapshots");
        if (!Files.exists(snapshotsDir)) {
            return;
        }

        try (Stream<Path> files = Files.list(snapshotsDir)) {
            List<Path> snapshots = files
                    .filter(p -> p.toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed()) // 按文件名降序（最新的在前）
                    .toList();

            if (snapshots.size() <= maxSnapshots) {
                return;
            }

            // 删除超出限制的旧快照
            List<Path> toDelete = snapshots.subList(maxSnapshots, snapshots.size());
            for (Path snapshot : toDelete) {
                try {
                    Files.delete(snapshot);
                    LOGGER.info(() -> "Deleted old snapshot: " + snapshot.getFileName());
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete old snapshot: {}", snapshot, e);
                }
            }

            LOGGER.info(() -> String.format("Cleaned up %d old snapshots", toDelete.size()));

        } catch (IOException e) {
            LOGGER.warn("Failed to cleanup old snapshots", e);
        }
    }
}
