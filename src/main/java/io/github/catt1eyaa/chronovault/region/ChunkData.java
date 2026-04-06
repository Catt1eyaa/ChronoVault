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
 * Represents the raw data of a chunk extracted from a region file.
 * <p>
 * In Minecraft's Anvil format, each chunk's data consists of:
 * <ul>
 *   <li>4 bytes: Data length (big-endian, includes compression type byte)</li>
 *   <li>1 byte: Compression type identifier</li>
 *   <li>N bytes: Compressed NBT data</li>
 * </ul>
 * This record stores the chunk coordinates, compression type, and the raw
 * compressed data (excluding the 4-byte length prefix and compression type byte).
 *
 * @param x              The chunk's x coordinate within the region (0-31)
 * @param z              The chunk's z coordinate within the region (0-31)
 * @param compressionType The compression type: 1=GZip, 2=Zlib, 3=Uncompressed, 4=LZ4
 * @param rawData        The compressed NBT data (not including length and type bytes)
 */
public record ChunkData(
    int x,
    int z,
    byte compressionType,
    byte[] rawData
) {
    /**
     * Compression type constants.
     */
    public static final byte COMPRESSION_GZIP = 1;
    public static final byte COMPRESSION_ZLIB = 2;
    public static final byte COMPRESSION_UNCOMPRESSED = 3;
    public static final byte COMPRESSION_LZ4 = 4;

    /**
     * Returns the human-readable name of the compression type.
     *
     * @return The compression type name (e.g., "Zlib", "GZip", "LZ4", "Uncompressed", or "Unknown")
     */
    public String getCompressionName() {
        return switch (compressionType) {
            case COMPRESSION_GZIP -> "GZip";
            case COMPRESSION_ZLIB -> "Zlib";
            case COMPRESSION_UNCOMPRESSED -> "Uncompressed";
            case COMPRESSION_LZ4 -> "LZ4";
            default -> "Unknown (" + compressionType + ")";
        };
    }

    /**
     * Returns the size of the raw compressed data in bytes.
     *
     * @return The length of the rawData array
     */
    public int getDataSize() {
        return rawData.length;
    }

    @Override
    public String toString() {
        return String.format("ChunkData[x=%d, z=%d, compression=%s, size=%d bytes]",
                x, z, getCompressionName(), getDataSize());
    }
}
