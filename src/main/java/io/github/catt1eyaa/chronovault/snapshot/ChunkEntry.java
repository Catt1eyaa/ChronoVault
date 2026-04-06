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
 * 表示快照中单个chunk的条目
 * @param x chunk的x坐标 (0-31)
 * @param z chunk的z坐标 (0-31)
 * @param hash 对象池中chunk数据的BLAKE3哈希（64字符），null表示chunk未生成
 */
public record ChunkEntry(
    int x,
    int z,
    String hash  // nullable
) {
    // 构造函数验证
    public ChunkEntry {
        if (x < 0 || x > 31) {
            throw new IllegalArgumentException("Chunk x must be 0-31, got: " + x);
        }
        if (z < 0 || z > 31) {
            throw new IllegalArgumentException("Chunk z must be 0-31, got: " + z);
        }
        // hash可以为null（未生成的chunk）
        if (hash != null && !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid hash format: " + hash);
        }
    }
    
    /**
     * chunk是否为空（未生成）
     */
    public boolean isEmpty() {
        return hash == null;
    }
}
