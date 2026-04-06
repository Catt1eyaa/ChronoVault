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

package io.github.catt1eyaa;

import io.github.catt1eyaa.chronovault.backup.AutoBackupScheduler;
import io.github.catt1eyaa.chronovault.backup.BackupCoordinator;
import io.github.catt1eyaa.chronovault.command.ChronoVaultCommands;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.nio.file.Path;

@Mod(ChronoVault.MOD_ID)
public class ChronoVault {
    public static final String MOD_ID = "chrono_vault";
    public static final Logger LOGGER = LogUtils.getLogger();

    private ChronoVaultCommands commands;
    private BackupCoordinator coordinator;
    private AutoBackupScheduler autoBackupScheduler;

    public ChronoVault(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        commands = new ChronoVaultCommands();

        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // 命令注册（此时命令对象已存在，可以正常注册）
        commands.register(event.getDispatcher());
        LOGGER.info("ChronoVault commands registered");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        Path backupRoot = Path.of(Config.getBackupPath());
        int compressionLevel = Config.getCompressionLevel();
        int maxSnapshots = Config.getMaxSnapshots();

        // 创建统一的备份协调器
        // 使用 server.storageSource 获取 LevelStorageAccess（参考 SimpleBackups）
        coordinator = new BackupCoordinator(
                event.getServer(),
                event.getServer().storageSource,
                backupRoot,
                compressionLevel,
                maxSnapshots
        );

        // 设置命令处理器的协调器
        commands.setCoordinator(coordinator, backupRoot);
        LOGGER.info("ChronoVault backup coordinator initialized");

        // 启动自动备份调度器
        if (Config.isAutoBackupEnabled()) {
            autoBackupScheduler = new AutoBackupScheduler(
                    coordinator,
                    Config.getAutoBackupIntervalMinutes()
            );
            autoBackupScheduler.start();
            LOGGER.info("ChronoVault auto backup scheduler started (interval: {} minutes)",
                    Config.getAutoBackupIntervalMinutes());
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        // 停止自动备份调度器
        if (autoBackupScheduler != null) {
            autoBackupScheduler.stop();
            autoBackupScheduler = null;
        }

        // 关闭命令处理器
        if (commands != null) {
            commands.shutdown();
        }

        // 关闭备份协调器
        if (coordinator != null) {
            coordinator.shutdown();
            coordinator = null;
        }

        LOGGER.info("ChronoVault shutdown complete");
    }
}
