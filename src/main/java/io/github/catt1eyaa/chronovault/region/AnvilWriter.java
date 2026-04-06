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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Writer for Minecraft's Anvil region file format (.mca files).
 * <p>
 * This class can reassemble chunk data into valid .mca files, which is essential
 * for restoring worlds from backups. The Anvil format consists of:
 * <ul>
 *   <li><b>Location Table (bytes 0-4095)</b>: 1024 entries, 4 bytes each.
 *       First 3 bytes (big-endian) = sector offset, 4th byte = sector count.</li>
 *   <li><b>Timestamp Table (bytes 4096-8191)</b>: 1024 entries, 4 bytes each.
 *       Each entry is a big-endian Unix timestamp.</li>
 *   <li><b>Chunk Data (bytes 8192+)</b>: Compressed chunk data aligned to 4KB sectors.</li>
 * </ul>
 *
 * @see AnvilReader
 * @see <a href="https://minecraft.wiki/w/Region_file_format">Region File Format</a>
 */
public class AnvilWriter {

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

    /**
     * Writes a list of chunks to a .mca region file.
     * <p>
     * This method creates a valid Anvil format file with the provided chunks.
     * Chunks can be fewer than 1024, but their coordinates must be within the
     * 0-31 range. Missing chunks will have their location table entries set to 0.
     * Timestamps will be set to the current Unix time.
     *
     * @param mcaFile The path to the output .mca file (will be created or overwritten)
     * @param chunks  The list of chunk data to write (must have coordinates 0-31)
     * @throws IOException              If the file cannot be written
     * @throws IllegalArgumentException If any chunk has invalid coordinates or compression type
     */
    public static void writeRegion(Path mcaFile, List<ChunkData> chunks) throws IOException {
        writeRegion(mcaFile, chunks, null);
    }

    /**
     * Writes a list of chunks to a .mca region file with custom timestamps.
     * <p>
     * This method creates a valid Anvil format file with the provided chunks and timestamps.
     * Chunks can be fewer than 1024, but their coordinates must be within the 0-31 range.
     * Missing chunks will have their location table entries set to 0.
     *
     * @param mcaFile    The path to the output .mca file (will be created or overwritten)
     * @param chunks     The list of chunk data to write (must have coordinates 0-31)
     * @param timestamps Optional map of chunk coordinates to Unix timestamps.
     *                   Key format: "x,z" (e.g., "5,12"). If null or missing for a chunk,
     *                   the current time will be used.
     * @throws IOException              If the file cannot be written
     * @throws IllegalArgumentException If any chunk has invalid coordinates or compression type
     */
    public static void writeRegion(Path mcaFile, List<ChunkData> chunks, Map<String, Integer> timestamps) throws IOException {
        // Validate input chunks
        validateChunks(chunks);

        // Create parent directories if they don't exist
        if (mcaFile.getParent() != null) {
            Files.createDirectories(mcaFile.getParent());
        }

        // Build the sector allocation map
        SectorAllocation allocation = allocateSectors(chunks);

        // Write the file
        try (FileChannel channel = FileChannel.open(mcaFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            // Step 1: Reserve space for headers (8KB)
            ByteBuffer headerBuffer = ByteBuffer.allocate(HEADER_SIZE);
            headerBuffer.order(ByteOrder.BIG_ENDIAN);
            
            // Fill with zeros initially
            while (headerBuffer.hasRemaining()) {
                headerBuffer.put((byte) 0);
            }
            headerBuffer.flip();
            writeFully(channel, headerBuffer);

            // Step 2: Write chunk data in sector order
            int currentTimestamp = (int) (System.currentTimeMillis() / 1000);
            
            for (ChunkData chunk : allocation.orderedChunks) {
                SectorInfo info = allocation.sectorMap.get(getChunkKey(chunk.x(), chunk.z()));
                
                // Calculate total size: 4 bytes (length) + 1 byte (compression) + data
                int totalDataSize = 4 + 1 + chunk.rawData().length;
                
                // Create buffer for this chunk's data
                ByteBuffer chunkBuffer = ByteBuffer.allocate(totalDataSize);
                chunkBuffer.order(ByteOrder.BIG_ENDIAN);
                
                // Write length (includes compression type byte)
                chunkBuffer.putInt(1 + chunk.rawData().length);
                
                // Write compression type
                chunkBuffer.put(chunk.compressionType());
                
                // Write compressed data
                chunkBuffer.put(chunk.rawData());
                
                chunkBuffer.flip();
                writeFully(channel, chunkBuffer);
                
                // Pad to sector boundary
                int padding = info.sectorCount * SECTOR_SIZE - totalDataSize;
                if (padding > 0) {
                    ByteBuffer paddingBuffer = ByteBuffer.allocate(padding);
                    while (paddingBuffer.hasRemaining()) {
                        paddingBuffer.put((byte) 0);
                    }
                    paddingBuffer.flip();
                    writeFully(channel, paddingBuffer);
                }
            }

            // Step 3: Write location and timestamp tables
            channel.position(0);
            
            ByteBuffer locationTable = ByteBuffer.allocate(SECTOR_SIZE);
            ByteBuffer timestampTable = ByteBuffer.allocate(SECTOR_SIZE);
            locationTable.order(ByteOrder.BIG_ENDIAN);
            timestampTable.order(ByteOrder.BIG_ENDIAN);

            // Initialize all entries to 0
            for (int i = 0; i < CHUNKS_PER_REGION; i++) {
                locationTable.putInt(0);
                timestampTable.putInt(0);
            }

            // Fill in entries for existing chunks
            for (ChunkData chunk : chunks) {
                int index = getChunkIndex(chunk.x(), chunk.z());
                SectorInfo info = allocation.sectorMap.get(getChunkKey(chunk.x(), chunk.z()));
                
                // Write location entry: offset (3 bytes) + sector count (1 byte)
                int locationEntry = (info.offset << 8) | (info.sectorCount & 0xFF);
                locationTable.putInt(index * 4, locationEntry);
                
                // Write timestamp
                int timestamp;
                if (timestamps != null && timestamps.containsKey(getChunkKey(chunk.x(), chunk.z()))) {
                    timestamp = timestamps.get(getChunkKey(chunk.x(), chunk.z()));
                } else {
                    timestamp = currentTimestamp;
                }
                timestampTable.putInt(index * 4, timestamp);
            }

            locationTable.flip();
            timestampTable.flip();
            
            writeFully(channel, locationTable);
            writeFully(channel, timestampTable);
        }
    }

    static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            int written = channel.write(buffer);
            if (written < 0) {
                throw new IOException("Unexpected end of channel while writing data");
            }
        }
    }

    /**
     * Validates that all chunks have valid coordinates and compression types.
     *
     * @param chunks The list of chunks to validate
     * @throws IllegalArgumentException If any chunk is invalid
     */
    private static void validateChunks(List<ChunkData> chunks) {
        for (ChunkData chunk : chunks) {
            // Validate coordinates
            if (chunk.x() < 0 || chunk.x() > 31) {
                throw new IllegalArgumentException("Chunk x coordinate out of range: " + chunk.x() + " (must be 0-31)");
            }
            if (chunk.z() < 0 || chunk.z() > 31) {
                throw new IllegalArgumentException("Chunk z coordinate out of range: " + chunk.z() + " (must be 0-31)");
            }

            // Validate compression type
            byte compression = chunk.compressionType();
            if (compression < 1 || compression > 4) {
                throw new IllegalArgumentException(
                        "Invalid compression type: " + compression + " for chunk (" + chunk.x() + "," + chunk.z() + ")"
                );
            }
        }
    }

    /**
     * Allocates sectors for chunks and determines their positions in the file.
     * <p>
     * Chunks are allocated in order of their coordinates (sorted by z, then x)
     * starting from sector 2 (sectors 0-1 are reserved for headers).
     *
     * @param chunks The list of chunks to allocate
     * @return A SectorAllocation object containing the allocation information
     */
    private static SectorAllocation allocateSectors(List<ChunkData> chunks) {
        Map<String, SectorInfo> sectorMap = new HashMap<>();
        
        // Sort chunks by coordinate (z-major, then x) for consistent ordering
        List<ChunkData> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort(Comparator.comparingInt((ChunkData c) -> c.z())
                .thenComparingInt(ChunkData::x));

        int currentSector = 2; // Start after the header (sectors 0-1)

        for (ChunkData chunk : sortedChunks) {
            // Calculate size needed: 4 bytes (length) + 1 byte (compression) + data
            int dataSize = 4 + 1 + chunk.rawData().length;
            
            // Calculate number of sectors needed (round up)
            int sectorsNeeded = (dataSize + SECTOR_SIZE - 1) / SECTOR_SIZE;

            sectorMap.put(
                    getChunkKey(chunk.x(), chunk.z()),
                    new SectorInfo(currentSector, sectorsNeeded)
            );

            currentSector += sectorsNeeded;
        }

        return new SectorAllocation(sectorMap, sortedChunks);
    }

    /**
     * Calculates the index of a chunk in the location/timestamp tables.
     * Index formula: (z & 31) * 32 + (x & 31)
     *
     * @param x The chunk's x coordinate
     * @param z The chunk's z coordinate
     * @return The index (0-1023)
     */
    private static int getChunkIndex(int x, int z) {
        return (z & 31) * 32 + (x & 31);
    }

    /**
     * Generates a string key for a chunk coordinate pair.
     *
     * @param x The chunk's x coordinate
     * @param z The chunk's z coordinate
     * @return A string in the format "x,z"
     */
    private static String getChunkKey(int x, int z) {
        return x + "," + z;
    }

    /**
     * Holds information about sector allocation for a chunk.
     */
    private record SectorInfo(int offset, int sectorCount) {
    }

    /**
     * Holds the complete sector allocation information for a region file.
     */
    private record SectorAllocation(
            Map<String, SectorInfo> sectorMap,
            List<ChunkData> orderedChunks
    ) {
    }
}
