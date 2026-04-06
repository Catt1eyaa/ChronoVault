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

package io.github.catt1eyaa.chronovault.snapshot;

/**
 * 快照统计信息
 */
public record ManifestStats(
    int fileCount,        // 普通文件数量
    int regionCount,      // region文件数量
    int totalChunks,      // 总chunk槽位数
    int nonEmptyChunks,   // 非空chunk数量
    int uniqueObjects     // 唯一对象数量
) {}
