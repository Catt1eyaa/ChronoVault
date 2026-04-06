package io.github.catt1eyaa.chronovault.region;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AnvilWriter class.
 * <p>
 * These tests verify that the writer can correctly create valid .mca files
 * that can be read back by AnvilReader, including:
 * <ul>
 *   <li>Round-trip consistency (write and read back produces identical data)</li>
 *   <li>Partial chunk writing (writing only some chunks from a region)</li>
 *   <li>File format validity (headers, timestamps, sector alignment)</li>
 * </ul>
 */
class AnvilWriterTest {

    @TempDir
    Path tempDir;

    private Path testRegionFile;
    private List<ChunkData> testChunks;

    @BeforeEach
    void setUp() throws IOException {
        // Try to use the actual test region file if it exists
        testRegionFile = Paths.get("run/saves/New World/region/r.0.0.mca");
        
        if (Files.exists(testRegionFile)) {
            System.out.println("Using test region file: " + testRegionFile);
            testChunks = AnvilReader.readAllChunks(testRegionFile);
            System.out.println("Loaded " + testChunks.size() + " chunks from test file");
        } else {
            System.out.println("Test region file not found, will use synthetic data for some tests");
            testChunks = null;
        }
    }

    @Test
    void testRoundTripConsistency() throws IOException {
        if (testChunks == null || testChunks.isEmpty()) {
            System.out.println("Skipping round-trip test: no test data available");
            return;
        }

        // Write all chunks to a new file
        Path outputFile = tempDir.resolve("roundtrip.mca");
        AnvilWriter.writeRegion(outputFile, testChunks);

        // Verify file was created
        assertTrue(Files.exists(outputFile), "Output file should exist");

        // Read back the chunks
        List<ChunkData> readBackChunks = AnvilReader.readAllChunks(outputFile);

        // Verify same number of chunks
        assertEquals(testChunks.size(), readBackChunks.size(), 
                "Should have same number of chunks after round-trip");

        // Sort both lists for comparison (order might differ)
        testChunks.sort(Comparator.comparingInt((ChunkData c) -> c.z()).thenComparingInt(ChunkData::x));
        readBackChunks.sort(Comparator.comparingInt((ChunkData c) -> c.z()).thenComparingInt(ChunkData::x));

        // Compare each chunk
        for (int i = 0; i < testChunks.size(); i++) {
            ChunkData original = testChunks.get(i);
            ChunkData readBack = readBackChunks.get(i);

            assertEquals(original.x(), readBack.x(), "Chunk x coordinate should match");
            assertEquals(original.z(), readBack.z(), "Chunk z coordinate should match");
            assertEquals(original.compressionType(), readBack.compressionType(), 
                    "Compression type should match for chunk (" + original.x() + "," + original.z() + ")");
            assertArrayEquals(original.rawData(), readBack.rawData(), 
                    "Raw data should match for chunk (" + original.x() + "," + original.z() + ")");
        }

        System.out.println("✓ Round-trip test passed: All " + testChunks.size() + " chunks match");
    }

    @Test
    void testPartialChunkWriting() throws IOException {
        if (testChunks == null || testChunks.size() < 10) {
            System.out.println("Skipping partial chunk test: insufficient test data");
            return;
        }

        // Take only the first 10 chunks
        List<ChunkData> partialChunks = testChunks.subList(0, 10);

        // Write partial chunks to a new file
        Path outputFile = tempDir.resolve("partial.mca");
        AnvilWriter.writeRegion(outputFile, partialChunks);

        // Verify file was created
        assertTrue(Files.exists(outputFile), "Output file should exist");

        // Read back all chunks
        List<ChunkData> readBackChunks = AnvilReader.readAllChunks(outputFile);

        // Should have exactly 10 chunks
        assertEquals(10, readBackChunks.size(), "Should have exactly 10 chunks");

        // Verify all chunks match
        for (ChunkData original : partialChunks) {
            ChunkData readBack = readBackChunks.stream()
                    .filter(c -> c.x() == original.x() && c.z() == original.z())
                    .findFirst()
                    .orElse(null);

            assertNotNull(readBack, "Should find chunk (" + original.x() + "," + original.z() + ")");
            assertEquals(original.compressionType(), readBack.compressionType());
            assertArrayEquals(original.rawData(), readBack.rawData());
        }

        System.out.println("✓ Partial chunk test passed: 10 chunks written and verified");
    }

    @Test
    void testFileFormatValidity() throws IOException {
        if (testChunks == null || testChunks.isEmpty()) {
            System.out.println("Skipping file format test: no test data available");
            return;
        }

        // Write chunks to a new file
        Path outputFile = tempDir.resolve("format_test.mca");
        AnvilWriter.writeRegion(outputFile, testChunks);

        // Verify file size is reasonable
        long fileSize = Files.size(outputFile);
        assertTrue(fileSize >= 8192, "File should be at least 8KB (header size)");
        assertTrue(fileSize % 4096 == 0, "File size should be aligned to 4KB sectors");

        // Verify header can be read
        List<ChunkLocation> locations = AnvilReader.readHeader(outputFile);
        assertEquals(1024, locations.size(), "Should have 1024 location entries");

        // Count non-empty chunks
        long nonEmptyCount = locations.stream().filter(loc -> !loc.isEmpty()).count();
        assertEquals(testChunks.size(), nonEmptyCount, 
                "Number of non-empty locations should match number of chunks written");

        // Verify all written chunks have valid location entries
        for (ChunkData chunk : testChunks) {
            ChunkLocation location = locations.stream()
                    .filter(loc -> loc.x() == chunk.x() && loc.z() == chunk.z())
                    .findFirst()
                    .orElse(null);

            assertNotNull(location, "Should have location for chunk (" + chunk.x() + "," + chunk.z() + ")");
            assertFalse(location.isEmpty(), "Location should not be empty");
            assertTrue(location.offset() >= 2, "Offset should be >= 2 (after header)");
            assertTrue(location.sectorCount() > 0, "Sector count should be positive");
        }

        System.out.println("✓ File format test passed: Valid Anvil format with " + testChunks.size() + " chunks");
    }

    @Test
    void testWriteFullyHandlesPartialWrites(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("partial-write.bin");
        byte[] expected = new byte[8192 + 123];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i & 0xFF);
        }

        try (FileChannel base = FileChannel.open(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
             FileChannel partial = new PartialWriteFileChannel(base, 257)
        ) {
            ByteBuffer buffer = ByteBuffer.wrap(expected);
            AnvilWriter.writeFully(partial, buffer);
            assertFalse(buffer.hasRemaining(), "Buffer should be fully written");
        }

        byte[] actual = Files.readAllBytes(file);
        assertArrayEquals(expected, actual, "writeFully should write all bytes even with partial channel writes");
    }

    @Test
    void testTimestampHandling() throws IOException {
        if (testChunks == null || testChunks.isEmpty()) {
            System.out.println("Skipping timestamp test: no test data available");
            return;
        }

        // Create custom timestamps for chunks
        Map<String, Integer> customTimestamps = new HashMap<>();
        int baseTimestamp = 1609459200; // 2021-01-01 00:00:00 UTC
        
        for (int i = 0; i < Math.min(5, testChunks.size()); i++) {
            ChunkData chunk = testChunks.get(i);
            customTimestamps.put(chunk.x() + "," + chunk.z(), baseTimestamp + i * 3600);
        }

        // Write with custom timestamps
        Path outputFile = tempDir.resolve("timestamp_test.mca");
        AnvilWriter.writeRegion(outputFile, testChunks.subList(0, Math.min(5, testChunks.size())), customTimestamps);

        // Read header and verify timestamps
        List<ChunkLocation> locations = AnvilReader.readHeader(outputFile);

        for (Map.Entry<String, Integer> entry : customTimestamps.entrySet()) {
            String[] coords = entry.getKey().split(",");
            int x = Integer.parseInt(coords[0]);
            int z = Integer.parseInt(coords[1]);
            int expectedTimestamp = entry.getValue();

            ChunkLocation location = locations.stream()
                    .filter(loc -> loc.x() == x && loc.z() == z)
                    .findFirst()
                    .orElse(null);

            assertNotNull(location, "Should have location for chunk (" + x + "," + z + ")");
            assertEquals(expectedTimestamp, location.timestamp(), 
                    "Timestamp should match for chunk (" + x + "," + z + ")");
        }

        System.out.println("✓ Timestamp test passed: Custom timestamps preserved correctly");
    }

    @Test
    void testInvalidCoordinates() {
        // Test x coordinate out of range
        ChunkData invalidX = new ChunkData(32, 15, ChunkData.COMPRESSION_ZLIB, new byte[100]);
        assertThrows(IllegalArgumentException.class, 
                () -> AnvilWriter.writeRegion(tempDir.resolve("invalid.mca"), List.of(invalidX)),
                "Should throw exception for x=32");

        // Test negative x coordinate
        ChunkData negativeX = new ChunkData(-1, 15, ChunkData.COMPRESSION_ZLIB, new byte[100]);
        assertThrows(IllegalArgumentException.class,
                () -> AnvilWriter.writeRegion(tempDir.resolve("invalid.mca"), List.of(negativeX)),
                "Should throw exception for x=-1");

        // Test z coordinate out of range
        ChunkData invalidZ = new ChunkData(15, 32, ChunkData.COMPRESSION_ZLIB, new byte[100]);
        assertThrows(IllegalArgumentException.class,
                () -> AnvilWriter.writeRegion(tempDir.resolve("invalid.mca"), List.of(invalidZ)),
                "Should throw exception for z=32");

        System.out.println("✓ Invalid coordinates test passed: Exceptions thrown as expected");
    }

    @Test
    void testInvalidCompressionType() {
        // Test invalid compression type (0)
        ChunkData invalid0 = new ChunkData(5, 5, (byte) 0, new byte[100]);
        assertThrows(IllegalArgumentException.class,
                () -> AnvilWriter.writeRegion(tempDir.resolve("invalid.mca"), List.of(invalid0)),
                "Should throw exception for compression type 0");

        // Test invalid compression type (5)
        ChunkData invalid5 = new ChunkData(5, 5, (byte) 5, new byte[100]);
        assertThrows(IllegalArgumentException.class,
                () -> AnvilWriter.writeRegion(tempDir.resolve("invalid.mca"), List.of(invalid5)),
                "Should throw exception for compression type 5");

        System.out.println("✓ Invalid compression type test passed: Exceptions thrown as expected");
    }

    @Test
    void testEmptyChunkList() throws IOException {
        // Writing an empty list should create a valid (but empty) region file
        Path outputFile = tempDir.resolve("empty.mca");
        AnvilWriter.writeRegion(outputFile, Collections.emptyList());

        assertTrue(Files.exists(outputFile), "File should be created");
        
        // File should be at least the header size
        long fileSize = Files.size(outputFile);
        assertEquals(8192, fileSize, "Empty region file should be exactly 8KB (headers only)");

        // Read back and verify no chunks
        List<ChunkData> chunks = AnvilReader.readAllChunks(outputFile);
        assertEquals(0, chunks.size(), "Should have no chunks");

        System.out.println("✓ Empty chunk list test passed: Valid empty region file created");
    }

    @Test
    void testSingleChunk() throws IOException {
        // Create a single test chunk with synthetic data
        byte[] testData = new byte[1000];
        new Random(42).nextBytes(testData); // Deterministic random data
        
        ChunkData singleChunk = new ChunkData(15, 20, ChunkData.COMPRESSION_ZLIB, testData);

        // Write single chunk
        Path outputFile = tempDir.resolve("single.mca");
        AnvilWriter.writeRegion(outputFile, List.of(singleChunk));

        // Read back
        List<ChunkData> chunks = AnvilReader.readAllChunks(outputFile);
        assertEquals(1, chunks.size(), "Should have exactly one chunk");

        ChunkData readBack = chunks.get(0);
        assertEquals(15, readBack.x());
        assertEquals(20, readBack.z());
        assertEquals(ChunkData.COMPRESSION_ZLIB, readBack.compressionType());
        assertArrayEquals(testData, readBack.rawData());

        System.out.println("✓ Single chunk test passed: Single chunk written and verified");
    }

    private static final class PartialWriteFileChannel extends FileChannel {
        private final FileChannel delegate;
        private final int maxBytesPerWrite;

        private PartialWriteFileChannel(FileChannel delegate, int maxBytesPerWrite) {
            this.delegate = delegate;
            this.maxBytesPerWrite = maxBytesPerWrite;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            return delegate.read(dst);
        }

        @Override
        public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
            return delegate.read(dsts, offset, length);
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            if (!src.hasRemaining()) {
                return 0;
            }

            int toWrite = Math.min(src.remaining(), maxBytesPerWrite);
            ByteBuffer slice = src.slice();
            slice.limit(toWrite);
            int written = delegate.write(slice);
            src.position(src.position() + written);
            return written;
        }

        @Override
        public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
            return delegate.write(srcs, offset, length);
        }

        @Override
        public long position() throws IOException {
            return delegate.position();
        }

        @Override
        public FileChannel position(long newPosition) throws IOException {
            delegate.position(newPosition);
            return this;
        }

        @Override
        public long size() throws IOException {
            return delegate.size();
        }

        @Override
        public FileChannel truncate(long size) throws IOException {
            delegate.truncate(size);
            return this;
        }

        @Override
        public void force(boolean metaData) throws IOException {
            delegate.force(metaData);
        }

        @Override
        public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            return delegate.transferTo(position, count, target);
        }

        @Override
        public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
            return delegate.transferFrom(src, position, count);
        }

        @Override
        public int read(ByteBuffer dst, long position) throws IOException {
            return delegate.read(dst, position);
        }

        @Override
        public int write(ByteBuffer src, long position) throws IOException {
            return delegate.write(src, position);
        }

        @Override
        public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
            return delegate.map(mode, position, size);
        }

        @Override
        public FileLock lock(long position, long size, boolean shared) throws IOException {
            return delegate.lock(position, size, shared);
        }

        @Override
        public FileLock tryLock(long position, long size, boolean shared) throws IOException {
            return delegate.tryLock(position, size, shared);
        }

        @Override
        protected void implCloseChannel() throws IOException {
            delegate.close();
        }
    }
}
