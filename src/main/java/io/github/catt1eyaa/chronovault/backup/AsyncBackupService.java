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

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步备份服务
 *
 * <p>基于ForkJoinPool执行备份任务，提供进度回调、取消和优雅关闭能力。</p>
 */
public class AsyncBackupService implements AutoCloseable {

    private final BackupExecutor backupExecutor;
    private final ForkJoinPool executor;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * 使用默认并行度创建服务
     *
     * @param backupDir 备份目录
     * @param compressionLevel 压缩级别
     */
    public AsyncBackupService(Path backupDir, int compressionLevel) {
        this(backupDir, compressionLevel, Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * 创建异步备份服务
     *
     * @param backupDir 备份目录
     * @param compressionLevel 压缩级别
     * @param parallelism 并行度
     */
    public AsyncBackupService(Path backupDir, int compressionLevel, int parallelism) {
        Objects.requireNonNull(backupDir, "backupDir cannot be null");
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be positive");
        }

        this.backupExecutor = new BackupExecutor(backupDir, compressionLevel);
        this.executor = new ForkJoinPool(parallelism);
    }

    /**
     * 使用默认压缩级别创建服务
     */
    public AsyncBackupService(Path backupDir) {
        this(backupDir, 3);
    }

    /**
     * 异步执行备份
     *
     * @param worldDir 世界目录
     * @param gameVersion 游戏版本
     * @param description 备份描述
     * @param progressListener 进度监听
     * @return 可取消的 Future
     */
    public CompletableFuture<BackupResult> backupAsync(
            Path worldDir,
            String gameVersion,
            String description,
            BackupProgressListener progressListener
    ) {
        if (shutdown.get()) {
            throw new IllegalStateException("AsyncBackupService is already shut down");
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);

        CompletableFuture<BackupResult> future = CompletableFuture.supplyAsync(
                () -> backupExecutor.execute(worldDir, gameVersion, description, progressListener, cancelled::get),
                executor
        );

        future.whenComplete((result, throwable) -> {
            if (future.isCancelled()) {
                cancelled.set(true);
            }
        });

        return new CancellableBackupFuture(future, cancelled);
    }

    /**
     * 异步执行备份（无进度回调）
     */
    public CompletableFuture<BackupResult> backupAsync(Path worldDir, String gameVersion, String description) {
        return backupAsync(worldDir, gameVersion, description, null);
    }

    /**
     * 是否已关闭
     */
    public boolean isShutdown() {
        return shutdown.get();
    }

    /**
     * 优雅关闭
     */
    @Override
    public void close() {
        if (shutdown.compareAndSet(false, true)) {
            executor.shutdown();
        }
    }

    /**
     * 支持传播取消信号的 Future 包装器
     */
    private static final class CancellableBackupFuture extends CompletableFuture<BackupResult> {
        private final CompletableFuture<BackupResult> delegate;
        private final AtomicBoolean cancelled;

        private CancellableBackupFuture(CompletableFuture<BackupResult> delegate, AtomicBoolean cancelled) {
            this.delegate = delegate;
            this.cancelled = cancelled;
            this.delegate.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    completeExceptionally(throwable);
                } else {
                    complete(result);
                }
            });
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled.set(true);
            boolean cancelledDelegate = delegate.cancel(mayInterruptIfRunning);
            boolean cancelledSelf = super.cancel(mayInterruptIfRunning);
            return cancelledDelegate || cancelledSelf;
        }
    }
}
