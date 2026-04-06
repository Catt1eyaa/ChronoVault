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

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * ChronoVault 配置系统
 *
 * <p>管理模组的所有可配置选项，包括备份路径、自动备份设置和压缩参数。</p>
 */
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 备份目录路径（相对于 Minecraft 运行目录）
     */
    public static final ModConfigSpec.ConfigValue<String> BACKUP_PATH = BUILDER
            .comment("Path to the backup directory (relative to the Minecraft server directory)")
            .define("backupPath", "backups");

    /**
     * 是否启用自动备份
     */
    public static final ModConfigSpec.BooleanValue AUTO_BACKUP_ENABLED = BUILDER
            .comment("Enable automatic periodic backups")
            .define("autoBackupEnabled", true);

    /**
     * 自动备份间隔（分钟）
     */
    public static final ModConfigSpec.IntValue AUTO_BACKUP_INTERVAL_MINUTES = BUILDER
            .comment("Interval between automatic backups in minutes")
            .defineInRange("autoBackupIntervalMinutes", 30, 5, 1440);

    /**
     * 最大快照数（0 表示无限）
     */
    public static final ModConfigSpec.IntValue MAX_SNAPSHOTS = BUILDER
            .comment("Maximum number of snapshots to keep (0 = unlimited, oldest will be deleted when exceeded)")
            .defineInRange("maxSnapshots", 0, 0, Integer.MAX_VALUE);

    /**
     * Zstd 压缩级别（1-22）
     */
    public static final ModConfigSpec.IntValue COMPRESSION_LEVEL = BUILDER
            .comment("Zstd compression level (1-22, higher = better compression but slower)")
            .defineInRange("compressionLevel", 3, 1, 22);

    /**
     * 命令权限等级（0-4）
     * 0 = 所有玩家可用（单人游戏推荐）
     * 2 = OP 以上的 GAMEMASTER
     * 4 = 仅 OP
     */
    public static final ModConfigSpec.IntValue COMMAND_PERMISSION_LEVEL = BUILDER
            .comment("Permission level required to use /chronovault commands (0-4)",
                    "0 = all players (recommended for single player)",
                    "2 = gamemaster+",
                    "4 = op only")
            .defineInRange("commandPermissionLevel", 2, 0, 4);

    /**
     * 备份前是否强制执行 save-all
     */
    public static final ModConfigSpec.BooleanValue SAVE_ALL_BEFORE_BACKUP = BUILDER
            .comment("Should a save-all be forced before backup?",
                    "Recommended to keep enabled for data consistency")
            .define("saveAllBeforeBackup", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    /**
     * 获取备份路径
     */
    public static String getBackupPath() {
        return BACKUP_PATH.get();
    }

    /**
     * 是否启用自动备份
     */
    public static boolean isAutoBackupEnabled() {
        return AUTO_BACKUP_ENABLED.get();
    }

    /**
     * 获取自动备份间隔（分钟）
     */
    public static int getAutoBackupIntervalMinutes() {
        return AUTO_BACKUP_INTERVAL_MINUTES.get();
    }

    /**
     * 获取最大快照数
     */
    public static int getMaxSnapshots() {
        return MAX_SNAPSHOTS.get();
    }

    /**
     * 获取压缩级别
     */
    public static int getCompressionLevel() {
        return COMPRESSION_LEVEL.get();
    }

    /**
     * 获取命令权限等级
     */
    public static int getCommandPermissionLevel() {
        return COMMAND_PERMISSION_LEVEL.get();
    }

    /**
     * 备份前是否执行 save-all
     */
    public static boolean isSaveAllBeforeBackup() {
        return SAVE_ALL_BEFORE_BACKUP.get();
    }
}
