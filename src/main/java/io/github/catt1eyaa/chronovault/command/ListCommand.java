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

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 快照列表命令实现
 *
 * <p>列出所有已保存的快照，显示快照 ID、时间戳和描述。</p>
 */
public class ListCommand {

    /**
     * 执行列表命令
     *
     * @param source 命令源
     * @param backupDir 备份目录
     * @return 命令结果代码
     */
    public static int execute(CommandSourceStack source, Path backupDir) {
        Path snapshotsDir = backupDir.resolve("snapshots");

        if (!Files.exists(snapshotsDir)) {
            source.sendSuccess(() -> Component.translatable("chrono_vault.command.list.no_snapshots"), false);
            return 0;
        }

        Map<String, Manifest> snapshots;
        try {
            snapshots = ManifestSerializer.loadAllSnapshots(snapshotsDir);
        } catch (IOException e) {
            source.sendFailure(Component.translatable("chrono_vault.command.list.load_failed", e.getMessage()));
            return 0;
        }

        if (snapshots.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("chrono_vault.command.list.no_snapshots"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("chrono_vault.command.list.header"), false);
        source.sendSuccess(() -> Component.translatable("chrono_vault.command.list.count", snapshots.size()), false);

        for (Manifest manifest : snapshots.values()) {
            String description = manifest.description().isEmpty() 
                    ? Component.translatable("chrono_vault.command.list.no_description").getString() 
                    : manifest.description();
            String line = String.format("  %s - %s", manifest.snapshotId(), description);
            source.sendSuccess(() -> Component.literal(line), false);
        }

        return snapshots.size();
    }
}
