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

import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 世界状态管理器
 *
 * <p>在备份期间控制世界保存状态，避免写入与备份并发导致的数据不一致。</p>
 *
 * <p>标准流程：</p>
 * <ol>
 *   <li>{@code prepareForBackup()}：执行 save-all flush 后禁用自动保存</li>
 *   <li>执行备份逻辑</li>
 *   <li>{@code finishBackup()}：重新启用自动保存</li>
 * </ol>
 *
 * <p>线程安全：此类的方法会在主服务器线程上阻塞执行世界保存操作。</p>
 */
public class WorldStateManager {

    private static final Logger LOGGER = Logger.getLogger(WorldStateManager.class.getName());

    private final MinecraftServer server;
    private final AtomicBoolean prepared;

    /**
     * 创建世界状态管理器
     *
     * @param server Minecraft 服务器实例
     */
    public WorldStateManager(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server cannot be null");
        this.prepared = new AtomicBoolean(false);
    }

    /**
     * 进入备份前状态：先 flush 所有数据到磁盘，再关闭自动保存。
     *
     * <p>此方法会阻塞直到世界保存完成。</p>
     *
     * @throws IllegalStateException 如果已经处于备份状态
     */
    public void prepareForBackup() {
        if (prepared.get()) {
            LOGGER.warning("Already in backup state, skipping prepare");
            return;
        }

        LOGGER.fine("Preparing for backup: saving world and disabling auto-save");

        try {
            server.executeBlocking(() -> {
                // 先刷新所有数据到磁盘
                server.saveEverything(true, false, true);
                // 然后禁用自动保存
                server.getAllLevels().forEach(level -> level.noSave = true);
            });
            prepared.set(true);
            LOGGER.fine("Backup preparation complete");
        } catch (Exception e) {
            // 如果保存失败，尝试恢复自动保存
            LOGGER.log(Level.SEVERE, "Failed to prepare for backup", e);
            try {
                server.executeBlocking(() ->
                    server.getAllLevels().forEach(level -> level.noSave = false)
                );
            } catch (Exception rollbackError) {
                LOGGER.log(Level.SEVERE, "Failed to rollback auto-save state", rollbackError);
            }
            throw new RuntimeException("Failed to prepare for backup", e);
        }
    }

    /**
     * 结束备份状态：重新开启自动保存。
     *
     * <p>此方法应在备份完成后（无论成功或失败）调用。</p>
     */
    public void finishBackup() {
        if (!prepared.get()) {
            LOGGER.warning("Not in backup state, skipping finish");
            return;
        }

        LOGGER.fine("Finishing backup: re-enabling auto-save");

        try {
            server.executeBlocking(() ->
                server.getAllLevels().forEach(level -> level.noSave = false)
            );
            prepared.set(false);
            LOGGER.fine("Backup finished, auto-save re-enabled");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to re-enable auto-save", e);
            // 即使失败也标记为未准备状态，避免死锁
            prepared.set(false);
            throw new RuntimeException("Failed to finish backup", e);
        }
    }

    /**
     * 当前是否处于备份保护状态（已禁用自动保存）。
     *
     * @return true 如果处于备份状态
     */
    public boolean isPrepared() {
        return prepared.get();
    }
}
