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

package io.github.catt1eyaa.chronovault.restore;

import java.nio.file.Path;

/**
 * 恢复操作结果。
 *
 * @param snapshotId 恢复的快照 ID
 * @param restoredFiles 恢复的普通文件数量
 * @param restoredRegions 恢复的 region 文件数量
 * @param restoredChunks 恢复的 chunk 数量
 * @param targetWorldName 新创建的世界文件夹名
 * @param targetWorldPath 新创建的世界完整路径
 */
public record RestoreResult(
    String snapshotId,
    int restoredFiles,
    int restoredRegions,
    int restoredChunks,
    String targetWorldName,
    Path targetWorldPath
) {
    /**
     * 紧凑构造器 - 参数验证。
     */
    public RestoreResult {
        if (snapshotId == null || snapshotId.isEmpty()) {
            throw new IllegalArgumentException("snapshotId 不能为 null 或空字符串");
        }
        if (restoredFiles < 0) {
            throw new IllegalArgumentException("restoredFiles 不能为负数: " + restoredFiles);
        }
        if (restoredRegions < 0) {
            throw new IllegalArgumentException("restoredRegions 不能为负数: " + restoredRegions);
        }
        if (restoredChunks < 0) {
            throw new IllegalArgumentException("restoredChunks 不能为负数: " + restoredChunks);
        }
        if (targetWorldName == null || targetWorldName.isEmpty()) {
            throw new IllegalArgumentException("targetWorldName 不能为 null 或空字符串");
        }
        if (targetWorldPath == null) {
            throw new IllegalArgumentException("targetWorldPath 不能为 null");
        }
    }
}
