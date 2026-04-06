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

import java.util.List;

/**
 * 表示快照中一个region文件(.mca)的条目
 * @param filename region文件名（如"r.0.0.mca"）
 * @param format 格式（通常是"anvil"）
 * @param chunks 该region中所有chunk的列表（最多1024个）
 */
public record RegionEntry(
    String filename,
    String format,
    List<ChunkEntry> chunks
) {
    public RegionEntry {
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        if (format == null) {
            format = "anvil";  // 默认值
        }
        if (chunks == null) {
            chunks = List.of();  // 空列表
        }
        if (chunks.size() > 1024) {
            throw new IllegalArgumentException("Region cannot have more than 1024 chunks");
        }
    }
    
    /**
     * 获取非空chunk数量
     */
    public int getNonEmptyChunkCount() {
        return (int) chunks.stream().filter(c -> !c.isEmpty()).count();
    }
}
