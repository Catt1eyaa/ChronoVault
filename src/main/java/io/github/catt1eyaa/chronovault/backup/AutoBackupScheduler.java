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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
 *
 * <p>使用 {@link BackupCoordinator} 执行实际备份，确保与手动备份协调。</p>
 */
public class AutoBackupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBackupScheduler.class);

    private final BackupCoordinator coordinator;
    private final int intervalMinutes;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 创建自动备份调度器
     *
     * @param coordinator     备份协调器
     * @param intervalMinutes 备份间隔（分钟）
     */
    public AutoBackupScheduler(BackupCoordinator coordinator, int intervalMinutes) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator cannot be null");
        if (intervalMinutes < 1) {
            throw new IllegalArgumentException("intervalMinutes must be >= 1");
        }
        this.intervalMinutes = intervalMinutes;
    }

    /**
     * 开始自动备份调度
     */
    public void start() {
        if (BackupCoordinator.isBackupDisabled()) {
            LOGGER.info("Auto backup disabled via JVM argument, not starting scheduler");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            LOGGER.warn("AutoBackupScheduler is already running");
            return;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            LOGGER.warn("AutoBackupScheduler scheduler already initialized");
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ChronoVault-AutoBackup-Scheduler");
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
            LOGGER.warn("AutoBackupScheduler is not running");
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

            // 使用 BackupCoordinator 执行同步备份
            // BackupCoordinator 会处理：
            // - 备份锁（防止与手动备份冲突）
            // - WorldStateManager（save-all + save-off/on）
            // - 磁盘空间检查
            // - 快照清理
            BackupResult result = coordinator.backupSync("Auto backup", null);

            if (result.success()) {
                LOGGER.info(() -> String.format(
                        "Auto backup completed: %s (files: %d, regions: %d, chunks: %d, time: %dms)",
                        result.snapshotId(),
                        result.stats().totalFiles(),
                        result.stats().totalRegions(),
                        result.stats().totalChunks(),
                        result.stats().durationMs()
                ));
            } else {
                LOGGER.error("Auto backup failed: {}", String.join("; ", result.errors()));
            }
        } catch (Exception e) {
            LOGGER.error("Auto backup encountered an error", e);
        }
    }
}
