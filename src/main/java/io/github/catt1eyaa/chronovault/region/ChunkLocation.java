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

package io.github.catt1eyaa.chronovault.region;

/**
 * Represents the location and metadata of a chunk within a region file.
 * <p>
 * In Minecraft's Anvil format, each region file (.mca) contains up to 1024 chunks
 * arranged in a 32x32 grid. This record stores the position and storage information
 * for a single chunk within that grid.
 *
 * @param x           The chunk's x coordinate within the region (0-31)
 * @param z           The chunk's z coordinate within the region (0-31)
 * @param offset      The sector offset where chunk data begins (each sector is 4KB).
 *                    An offset of 0 indicates the chunk does not exist.
 * @param sectorCount The number of 4KB sectors the chunk data occupies
 * @param timestamp   The Unix timestamp of the last modification
 */
public record ChunkLocation(
    int x,
    int z,
    int offset,
    int sectorCount,
    int timestamp
) {
    /**
     * The size of each sector in bytes (4KB).
     */
    public static final int SECTOR_SIZE = 4096;

    /**
     * Checks if this chunk location represents an empty/non-existent chunk.
     * <p>
     * A chunk is considered empty when its offset is 0, meaning no data
     * has been written for this chunk position in the region file.
     *
     * @return {@code true} if the chunk does not exist, {@code false} otherwise
     */
    public boolean isEmpty() {
        return offset == 0;
    }

    /**
     * Calculates the byte offset where the chunk data begins in the file.
     *
     * @return The byte offset from the start of the file
     */
    public long byteOffset() {
        return (long) offset * SECTOR_SIZE;
    }

    /**
     * Calculates the index of this chunk in the region file's header tables.
     * <p>
     * The index is calculated as: {@code (z & 31) * 32 + (x & 31)}
     *
     * @return The index (0-1023) in the location/timestamp tables
     */
    public int index() {
        return (z & 31) * 32 + (x & 31);
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return String.format("ChunkLocation[x=%d, z=%d, empty]", x, z);
        }
        return String.format("ChunkLocation[x=%d, z=%d, offset=%d, sectors=%d, timestamp=%d]",
                x, z, offset, sectorCount, timestamp);
    }
}
