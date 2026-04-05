package io.github.catt1eyaa.chronovault.backup;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import io.github.catt1eyaa.chronovault.storage.BackupPathResolver;
import io.github.catt1eyaa.chronovault.util.CompressionUtil;

/**
 * 自动备份调度器
 *
 * <p>基于 ScheduledExecutorService 实现定时自动备份。</p>
 *
 * <p>生命周期：</p>
 * <ul>
 *   <li>服务器启动时调用 {@link #start()} 开始调度</li>
 *   <li>服务器关闭时调用 {@link #stop()} 停止调度</li>
 * </ul>
 */
public class AutoBackupScheduler {

    private static final Logger LOGGER = Logger.getLogger(AutoBackupScheduler.class.getName());

    private final MinecraftServer server;
    private final Path backupRoot;
    private final int intervalMinutes;
    private final int compressionLevel;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 创建自动备份调度器
     *
     * @param server Minecraft 服务器实例
     * @param backupRoot 备份根目录
     * @param intervalMinutes 备份间隔（分钟）
     * @param compressionLevel 压缩级别
     */
    public AutoBackupScheduler(MinecraftServer server, Path backupRoot, int intervalMinutes, int compressionLevel) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        this.backupRoot = Objects.requireNonNull(backupRoot, "backupRoot cannot be null");
        if (intervalMinutes < 1) {
            throw new IllegalArgumentException("intervalMinutes must be >= 1");
        }
        if (!CompressionUtil.isValidLevel(compressionLevel)) {
            throw new IllegalArgumentException("Invalid compression level: " + compressionLevel);
        }
        this.intervalMinutes = intervalMinutes;
        this.compressionLevel = compressionLevel;
    }

    /**
     * 开始自动备份调度
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            LOGGER.warning("AutoBackupScheduler is already running");
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            LOGGER.warning("AutoBackupScheduler scheduler already initialized");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChronoVault-AutoBackup");
            t.setDaemon(true);
            return t;
        });

        LOGGER.info(() -> String.format("Scheduling automatic backups every %d minutes", intervalMinutes));

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::executeAutoBackup,
                intervalMinutes,
                intervalMinutes,
                TimeUnit.MINUTES
        );
    }

    /**
     * 停止自动备份调度
     */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            LOGGER.warning("AutoBackupScheduler is not running");
            return;
        }

        if (scheduledTask != null) {
            scheduledTask.cancel(true);
            scheduledTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            scheduler = null;
        }

        LOGGER.info("Auto backup scheduler stopped");
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        return running.get();
    }

    private void executeAutoBackup() {
        try {
            LOGGER.info("Starting automatic backup...");

            server.execute(() -> {
                try {
                    server.saveEverything(true, false, true);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Auto backup pre-save failed", e);
                }
            });

            Path worldDir = server.getWorldPath(LevelResource.ROOT);
            Path worldBackupDir = resolveWorldBackupDir(backupRoot, worldDir);
            BackupExecutor backupExecutor = new BackupExecutor(worldBackupDir, compressionLevel);

            BackupResult result = backupExecutor.execute(
                    worldDir,
                    server.getServerVersion(),
                    "Auto backup"
            );

            if (result.success()) {
                LOGGER.info(() -> "Auto backup completed successfully: " + result.snapshotId());
            } else {
                LOGGER.severe("Auto backup failed: " + String.join("; ", result.errors()));
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Auto backup encountered an error", e);
        }
    }

    static Path resolveWorldBackupDir(Path backupRoot, Path worldDir) {
        Objects.requireNonNull(backupRoot, "backupRoot cannot be null");
        Objects.requireNonNull(worldDir, "worldDir cannot be null");

        Path fileName = worldDir.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("worldDir must have a file name: " + worldDir);
        }

        return BackupPathResolver.resolve(backupRoot, fileName.toString());
    }
}
