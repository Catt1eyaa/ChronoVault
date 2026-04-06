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

package io.github.catt1eyaa.chronovault.command;

import io.github.catt1eyaa.chronovault.snapshot.Manifest;
import io.github.catt1eyaa.chronovault.snapshot.ManifestSerializer;
import io.github.catt1eyaa.chronovault.snapshot.ManifestStats;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 快照信息命令实现
 *
 * <p>显示指定快照的详细信息，包括时间戳、游戏版本、描述、文件数和 chunk 数。</p>
 */
public class InfoCommand {

    /**
     * 执行信息命令
     *
     * @param source 命令源
     * @param backupDir 备份目录
     * @param snapshotId 快照 ID
     * @return 命令结果代码
     */
    public static int execute(CommandSourceStack source, Path backupDir, String snapshotId) {
        Path snapshotsDir = backupDir.resolve("snapshots");
        Path manifestPath = snapshotsDir.resolve(snapshotId + ".json");

        Manifest manifest;
        try {
            manifest = ManifestSerializer.load(manifestPath);
        } catch (IOException e) {
            source.sendFailure(Component.literal("无法加载快照 '" + snapshotId + "': " + e.getMessage()));
            return 0;
        }

        ManifestStats stats = manifest.getStats();
        String timestamp = formatTimestamp(manifest.timestamp());

        source.sendSuccess(() -> Component.literal("=== 快照信息 ==="), false);
        source.sendSuccess(() -> Component.literal("  ID: " + manifest.snapshotId()), false);
        source.sendSuccess(() -> Component.literal("  时间: " + timestamp), false);
        source.sendSuccess(() -> Component.literal("  版本: " + manifest.gameVersion()), false);
        source.sendSuccess(() -> Component.literal("  描述: " + (manifest.description().isEmpty() ? "(无)" : manifest.description())), false);
        source.sendSuccess(() -> Component.literal("  文件数: " + stats.fileCount()), false);
        source.sendSuccess(() -> Component.literal("  Region 数: " + stats.regionCount()), false);
        source.sendSuccess(() -> Component.literal("  Chunk 数: " + stats.totalChunks()), false);
        source.sendSuccess(() -> Component.literal("  非空 Chunk: " + stats.nonEmptyChunks()), false);
        source.sendSuccess(() -> Component.literal("  唯一对象数: " + stats.uniqueObjects()), false);

        return 1;
    }

    private static String formatTimestamp(long epochSecond) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochSecond(epochSecond));
    }
}
