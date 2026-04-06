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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for Minecraft's Anvil region file format (.mca files).
 * <p>
 * The Anvil format stores chunks in region files, where each region contains
 * up to 1024 chunks (32x32 grid). The file structure consists of:
 * <ul>
 *   <li><b>Location Table (bytes 0-4095)</b>: 1024 entries, 4 bytes each.
 *       First 3 bytes (big-endian) = sector offset, 4th byte = sector count.</li>
 *   <li><b>Timestamp Table (bytes 4096-8191)</b>: 1024 entries, 4 bytes each.
 *       Each entry is a big-endian Unix timestamp.</li>
 *   <li><b>Chunk Data (bytes 8192+)</b>: Compressed chunk data in sectors.</li>
 * </ul>
 *
 * @see <a href="https://minecraft.wiki/w/Region_file_format">Region File Format</a>
 */
public class AnvilReader {

    /**
     * Size of each sector in bytes (4KB).
     */
    private static final int SECTOR_SIZE = 4096;

    /**
     * Number of chunks per region file (32x32 grid).
     */
    private static final int CHUNKS_PER_REGION = 1024;

    /**
     * Size of the header (location table + timestamp table).
     */
    private static final int HEADER_SIZE = SECTOR_SIZE * 2;
    private static final String ZERO_BYTE_REGION_FORMAT = "anvil-zero-byte";

    /**
     * Reads the header of an Anvil region file (.mca) and returns location
     * information for all 1024 chunks.
     * <p>
     * This method returns entries for all chunk positions, including empty
     * chunks (where offset = 0). This provides a complete representation of
     * the region's chunk layout.
     *
     * @param mcaFile Path to the .mca region file
     * @return A list of {@link ChunkLocation} objects for all 1024 chunks,
     *         ordered by their index (z * 32 + x)
     * @throws IOException If the file cannot be read, does not exist, or is
     *                     corrupted (too small to contain a valid header)
     */
    public static List<ChunkLocation> readHeader(Path mcaFile) throws IOException {
        List<ChunkLocation> locations = new ArrayList<>(CHUNKS_PER_REGION);

        try (FileChannel channel = FileChannel.open(mcaFile, StandardOpenOption.READ)) {
            // Validate file size - must at least contain the header
            long fileSize = channel.size();
            if (fileSize == 0L) {
                return createEmptyChunkLocations();
            }
            if (fileSize < HEADER_SIZE) {
                throw new IOException("Invalid region file: file size (" + fileSize +
                        " bytes) is smaller than required header size (" + HEADER_SIZE + " bytes)");
            }

            // Allocate buffer for both tables
            ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE);
            buffer.order(ByteOrder.BIG_ENDIAN);

            // Read the entire header
            int bytesRead = 0;
            while (bytesRead < HEADER_SIZE) {
                int read = channel.read(buffer);
                if (read == -1) {
                    throw new IOException("Unexpected end of file while reading header");
                }
                bytesRead += read;
            }

            buffer.flip();

            // Parse all 1024 chunk entries
            for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                // Calculate chunk coordinates from index
                int x = index & 31;        // index % 32
                int z = index >> 5;         // index / 32

                // Read location entry (4 bytes at position index * 4)
                int locationEntry = buffer.getInt(index * 4);

                // Extract offset (upper 24 bits) and sector count (lower 8 bits)
                int offset = (locationEntry >> 8) & 0xFFFFFF;
                int sectorCount = locationEntry & 0xFF;

                // Read timestamp (4 bytes at position SECTOR_SIZE + index * 4)
                int timestamp = buffer.getInt(SECTOR_SIZE + index * 4);

                locations.add(new ChunkLocation(x, z, offset, sectorCount, timestamp));
            }
        }

        return locations;
    }

    public static boolean isZeroByteRegion(Path mcaFile) throws IOException {
        try (FileChannel channel = FileChannel.open(mcaFile, StandardOpenOption.READ)) {
            return channel.size() == 0L;
        }
    }

    public static String zeroByteRegionFormat() {
        return ZERO_BYTE_REGION_FORMAT;
    }

    /**
     * Reads only the non-empty chunks from a region file header.
     * <p>
     * This is a convenience method that filters out empty chunk locations,
     * returning only chunks that actually contain data.
     *
     * @param mcaFile Path to the .mca region file
     * @return A list of non-empty {@link ChunkLocation} objects
     * @throws IOException If the file cannot be read
     */
    public static List<ChunkLocation> readNonEmptyChunks(Path mcaFile) throws IOException {
        return readHeader(mcaFile).stream()
                .filter(loc -> !loc.isEmpty())
                .toList();
    }

    /**
     * Counts the number of existing (non-empty) chunks in a region file.
     *
     * @param mcaFile Path to the .mca region file
     * @return The number of chunks that contain data
     * @throws IOException If the file cannot be read
     */
    public static int countChunks(Path mcaFile) throws IOException {
        return (int) readHeader(mcaFile).stream()
                .filter(loc -> !loc.isEmpty())
                .count();
    }

    /**
     * Reads the data of a specific chunk from a region file.
     * <p>
     * The chunk data structure in the file is:
     * <ul>
     *   <li>4 bytes: Data length (big-endian, includes compression type byte)</li>
     *   <li>1 byte: Compression type (1=GZip, 2=Zlib, 3=Uncompressed, 4=LZ4)</li>
     *   <li>N bytes: Compressed NBT data</li>
     * </ul>
     *
     * @param mcaFile  Path to the .mca region file
     * @param location The chunk location information obtained from readHeader()
     * @return A {@link ChunkData} object containing the chunk's data, or null if the chunk is empty
     * @throws IOException If the file cannot be read or the chunk data is corrupted
     */
    public static ChunkData readChunk(Path mcaFile, ChunkLocation location) throws IOException {
        // Return null for empty chunks
        if (location.isEmpty()) {
            return null;
        }

        try (FileChannel channel = FileChannel.open(mcaFile, StandardOpenOption.READ)) {
            // Position the channel at the chunk's data location
            long byteOffset = location.byteOffset();
            channel.position(byteOffset);

            // Read the 4-byte length header and 1-byte compression type
            ByteBuffer headerBuffer = ByteBuffer.allocate(5);
            headerBuffer.order(ByteOrder.BIG_ENDIAN);

            int bytesRead = 0;
            while (bytesRead < 5) {
                int read = channel.read(headerBuffer);
                if (read == -1) {
                    throw new IOException("Unexpected end of file while reading chunk header at offset " + byteOffset);
                }
                bytesRead += read;
            }

            headerBuffer.flip();

            // Parse the length (includes the compression type byte)
            int dataLength = headerBuffer.getInt();
            byte compressionType = headerBuffer.get();

            // Validate the length
            if (dataLength < 1) {
                throw new IOException("Invalid chunk data length: " + dataLength + " at offset " + byteOffset);
            }

            // The actual compressed data length is (dataLength - 1) because dataLength includes the compression type byte
            int compressedDataLength = dataLength - 1;

            // Validate that the compressed data length is reasonable
            if (compressedDataLength < 0) {
                throw new IOException("Invalid compressed data length: " + compressedDataLength);
            }

            // Read the compressed data
            ByteBuffer dataBuffer = ByteBuffer.allocate(compressedDataLength);
            bytesRead = 0;
            while (bytesRead < compressedDataLength) {
                int read = channel.read(dataBuffer);
                if (read == -1) {
                    throw new IOException("Unexpected end of file while reading chunk data at offset " + (byteOffset + 5));
                }
                bytesRead += read;
            }

            dataBuffer.flip();
            byte[] rawData = new byte[compressedDataLength];
            dataBuffer.get(rawData);

            return new ChunkData(location.x(), location.z(), compressionType, rawData);
        }
    }

    /**
     * Reads all non-empty chunks from a region file.
     * <p>
     * This method first reads the header to find all non-empty chunk locations,
     * then reads the data for each of those chunks.
     *
     * @param mcaFile Path to the .mca region file
     * @return A list of {@link ChunkData} objects for all non-empty chunks
     * @throws IOException If the file cannot be read
     */
    public static List<ChunkData> readAllChunks(Path mcaFile) throws IOException {
        List<ChunkLocation> nonEmptyLocations = readNonEmptyChunks(mcaFile);
        List<ChunkData> chunks = new ArrayList<>(nonEmptyLocations.size());

        for (ChunkLocation location : nonEmptyLocations) {
            ChunkData chunkData = readChunk(mcaFile, location);
            if (chunkData != null) {
                chunks.add(chunkData);
            }
        }

        return chunks;
    }

    private static List<ChunkLocation> createEmptyChunkLocations() {
        List<ChunkLocation> locations = new ArrayList<>(CHUNKS_PER_REGION);
        for (int index = 0; index < CHUNKS_PER_REGION; index++) {
            int x = index & 31;
            int z = index >> 5;
            locations.add(new ChunkLocation(x, z, 0, 0, 0));
        }
        return locations;
    }
}
