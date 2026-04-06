package io.github.catt1eyaa.chronovault.storage;

import io.github.catt1eyaa.chronovault.util.HashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ObjectPool的综合测试套件
 * 
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>基本读写测试</li>
 *   <li>去重测试</li>
 *   <li>大数据测试</li>
 *   <li>真实.mca文件测试</li>
 *   <li>目录结构测试</li>
 *   <li>统计测试</li>
 *   <li>删除测试</li>
 *   <li>并发测试</li>
 *   <li>错误处理测试</li>
 * </ul>
 */
class ObjectPoolTest {
    
    @TempDir
    Path tempDir;
    
    private ObjectPool objectPool;
    private Path objectsDir;
    
    @BeforeEach
    void setUp() {
        // 创建测试用的对象池目录
        objectsDir = tempDir.resolve("objects");
        objectPool = new ObjectPool(objectsDir);
    }
    
    @AfterEach
    void tearDown() {
        // 测试清理（@TempDir会自动清理）
    }
    
    // ==================== 基本读写测试 ====================
    
    @Test
    void testWriteAndRead() throws IOException {
        byte[] data = "Hello, ObjectPool!".getBytes();
        
        // 写入数据
        String hash = objectPool.write(data);
        
        // 验证哈希格式
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
        
        // 读取数据
        byte[] readData = objectPool.read(hash);
        
        // 验证数据一致
        assertArrayEquals(data, readData);
    }
    
    @Test
    void testWriteEmptyData() throws IOException {
        byte[] emptyData = new byte[0];
        
        String hash = objectPool.write(emptyData);
        byte[] readData = objectPool.read(hash);
        
        assertArrayEquals(emptyData, readData);
    }
    
    @Test
    void testHashCorrectness() throws IOException {
        byte[] data = "Test data".getBytes();
        
        // 写入并获取哈希
        String poolHash = objectPool.write(data);
        
        // 直接计算哈希
        String directHash = HashUtil.hashToHex(data);
        
        // 两个哈希应该一致
        assertEquals(directHash, poolHash);
    }
    
    // ==================== 去重测试 ====================
    
    @Test
    void testDeduplication() throws IOException {
        byte[] data = "Duplicate data".getBytes();
        
        // 写入第一次
        String hash1 = objectPool.write(data);
        assertTrue(objectPool.exists(hash1));
        
        // 获取对象文件路径
        Path objectPath = objectPool.getObjectPath(hash1);
        long firstWriteTime = Files.getLastModifiedTime(objectPath).toMillis();
        
        // 等待1毫秒以确保时间戳不同
        try {
            Thread.sleep(2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 写入第二次（相同数据）
        String hash2 = objectPool.write(data);
        
        // 验证哈希相同
        assertEquals(hash1, hash2);
        
        // 验证文件只有一个
        long secondWriteTime = Files.getLastModifiedTime(objectPath).toMillis();
        
        // 如果是真正的去重，文件修改时间应该没有变化（没有重写）
        assertEquals(firstWriteTime, secondWriteTime);
    }
    
    @Test
    void testExistsMethod() throws IOException {
        byte[] data = "Exists test".getBytes();
        String hash = HashUtil.hashToHex(data);
        
        // 写入前不存在
        assertFalse(objectPool.exists(hash));
        
        // 写入
        objectPool.write(data);
        
        // 写入后存在
        assertTrue(objectPool.exists(hash));
    }
    
    // ==================== 大数据测试 ====================
    
    @Test
    void testLargeData100KB() throws IOException {
        // 生成100KB随机数据
        byte[] data = new byte[100 * 1024];
        new Random(42).nextBytes(data);
        
        String hash = objectPool.write(data);
        byte[] readData = objectPool.read(hash);
        
        assertArrayEquals(data, readData);
        
        // 验证压缩工作（压缩后的文件应该小于原始大小）
        Path objectPath = objectPool.getObjectPath(hash);
        long compressedSize = Files.size(objectPath);
        
        // 随机数据通常不可压缩，但至少不应该显著膨胀
        assertTrue(compressedSize <= data.length * 1.1, 
                   "Compressed size too large: " + compressedSize);
    }
    
    @Test
    void testLargeData1MB() throws IOException {
        // 生成1MB随机数据
        byte[] data = new byte[1024 * 1024];
        new Random(123).nextBytes(data);
        
        String hash = objectPool.write(data);
        byte[] readData = objectPool.read(hash);
        
        assertArrayEquals(data, readData);
    }
    
    @Test
    void testCompressibleData() throws IOException {
        // 生成高度可压缩的数据（重复数据）
        byte[] data = new byte[10 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 10);
        }
        
        String hash = objectPool.write(data);
        byte[] readData = objectPool.read(hash);
        
        assertArrayEquals(data, readData);
        
        // 验证压缩效果
        Path objectPath = objectPool.getObjectPath(hash);
        long compressedSize = Files.size(objectPath);
        
        // 可压缩数据应该显著减小
        assertTrue(compressedSize < data.length * 0.5,
                   "Expected better compression for repetitive data. " +
                   "Original: " + data.length + ", Compressed: " + compressedSize);
    }
    
    // ==================== 真实数据测试 ====================
    
    @Test
    void testRealMcaFile() throws IOException {
        // 查找真实的.mca文件
        Path mcaFile = findMcaFile();
        
        if (mcaFile == null) {
            System.out.println("Skipping testRealMcaFile: No .mca file found");
            return;
        }
        
        System.out.println("Testing with .mca file: " + mcaFile);
        
        // 读取.mca文件内容
        byte[] mcaData = Files.readAllBytes(mcaFile);
        System.out.println("MCA file size: " + mcaData.length + " bytes");
        
        // 写入对象池
        String hash = objectPool.write(mcaData);
        System.out.println("MCA file hash: " + hash);
        
        // 读取并验证
        byte[] readData = objectPool.read(hash);
        assertArrayEquals(mcaData, readData);
        
        // 输出压缩统计
        Path objectPath = objectPool.getObjectPath(hash);
        long compressedSize = Files.size(objectPath);
        double ratio = (double) compressedSize / mcaData.length;
        
        System.out.printf("Compression: %d -> %d bytes (%.2f%%)%n",
                          mcaData.length, compressedSize, ratio * 100);
    }
    
    @Test
    void testMultipleMcaChunks() throws IOException {
        Path mcaFile = findMcaFile();
        
        if (mcaFile == null) {
            System.out.println("Skipping testMultipleMcaChunks: No .mca file found");
            return;
        }
        
        byte[] mcaData = Files.readAllBytes(mcaFile);
        
        // 将.mca文件分成多个"chunk"（每个4KB）
        int chunkSize = 4096;
        List<String> hashes = new ArrayList<>();
        
        for (int offset = 0; offset < mcaData.length; offset += chunkSize) {
            int length = Math.min(chunkSize, mcaData.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(mcaData, offset, chunk, 0, length);
            
            String hash = objectPool.write(chunk);
            hashes.add(hash);
        }
        
        System.out.println("Wrote " + hashes.size() + " chunks");
        
        // 验证所有chunk都可以读取
        for (int i = 0; i < hashes.size(); i++) {
            String hash = hashes.get(i);
            byte[] readChunk = objectPool.read(hash);
            
            int offset = i * chunkSize;
            int length = Math.min(chunkSize, mcaData.length - offset);
            
            assertEquals(length, readChunk.length);
        }
    }
    
    // ==================== 目录结构测试 ====================
    
    @Test
    void testDirectoryStructure() throws IOException {
        byte[] data = "Directory structure test".getBytes();
        String hash = objectPool.write(data);
        
        // 验证2级目录结构
        String prefix = hash.substring(0, 2);
        String suffix = hash.substring(2);
        
        Path expectedPath = objectsDir.resolve(prefix).resolve(suffix);
        Path actualPath = objectPool.getObjectPath(hash);
        
        assertEquals(expectedPath, actualPath);
        assertTrue(Files.exists(actualPath));
        
        // 验证父目录存在
        assertTrue(Files.isDirectory(actualPath.getParent()));
        assertEquals(prefix, actualPath.getParent().getFileName().toString());
    }
    
    @Test
    void testMultipleObjectsInDifferentDirectories() throws IOException {
        // 创建多个对象，确保它们分布在不同目录中
        List<String> hashes = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            byte[] data = ("Object " + i).getBytes();
            String hash = objectPool.write(data);
            hashes.add(hash);
        }
        
        // 收集所有使用的目录前缀
        List<String> prefixes = hashes.stream()
            .map(hash -> hash.substring(0, 2))
            .distinct()
            .toList();
        
        // 验证目录创建
        for (String prefix : prefixes) {
            Path prefixDir = objectsDir.resolve(prefix);
            assertTrue(Files.isDirectory(prefixDir));
        }
    }
    
    // ==================== 统计测试 ====================
    
    @Test
    void testStatsEmpty() throws IOException {
        ObjectPoolStats stats = objectPool.getStats();
        
        assertEquals(0, stats.objectCount());
        assertEquals(0, stats.totalSizeBytes());
        assertEquals(0, stats.totalSizeOriginal());
    }
    
    @Test
    void testStatsSingleObject() throws IOException {
        byte[] data = "Single object".getBytes();
        objectPool.write(data);
        
        ObjectPoolStats stats = objectPool.getStats();
        
        assertEquals(1, stats.objectCount());
        assertTrue(stats.totalSizeBytes() > 0);
        assertEquals(data.length, stats.totalSizeOriginal());
    }
    
    @Test
    void testStatsMultipleObjects() throws IOException {
        int objectCount = 5;
        long totalOriginalSize = 0;
        
        for (int i = 0; i < objectCount; i++) {
            byte[] data = ("Object " + i + " with some data").getBytes();
            totalOriginalSize += data.length;
            objectPool.write(data);
        }
        
        ObjectPoolStats stats = objectPool.getStats();
        
        assertEquals(objectCount, stats.objectCount());
        assertTrue(stats.totalSizeBytes() > 0);
        assertEquals(totalOriginalSize, stats.totalSizeOriginal());
        
        // 验证压缩率计算
        // 注意：对于小数据，压缩可能会使数据变大（压缩头开销）
        double ratio = stats.getCompressionRatio();
        assertTrue(ratio > 0, "Compression ratio should be positive, got: " + ratio);
        
        System.out.println("Stats: " + stats);
    }
    
    @Test
    void testStatsFormatting() {
        ObjectPoolStats stats = new ObjectPoolStats(10, 5000, 10000);
        
        assertEquals(10, stats.objectCount());
        assertEquals(5000, stats.totalSizeBytes());
        assertEquals(10000, stats.totalSizeOriginal());
        assertEquals(0.5, stats.getCompressionRatio(), 0.001);
        assertEquals(5000, stats.getSavedBytes());
        assertEquals(500, stats.getAverageObjectSize());
        
        String formatted = stats.toString();
        assertTrue(formatted.contains("objects=10"));
        assertTrue(formatted.contains("50.00%"));
    }
    
    // ==================== 删除测试 ====================
    
    @Test
    void testDeleteObject() throws IOException {
        byte[] data = "Delete test".getBytes();
        String hash = objectPool.write(data);
        
        // 验证对象存在
        assertTrue(objectPool.exists(hash));
        
        // 删除对象
        boolean deleted = objectPool.delete(hash);
        assertTrue(deleted);
        
        // 验证对象不存在
        assertFalse(objectPool.exists(hash));
        
        // 尝试读取应该失败
        assertThrows(IOException.class, () -> objectPool.read(hash));
    }
    
    @Test
    void testDeleteNonExistentObject() throws IOException {
        String fakeHash = "a".repeat(64);
        
        boolean deleted = objectPool.delete(fakeHash);
        assertFalse(deleted);
    }
    
    @Test
    void testListAllObjects() throws IOException {
        // 写入多个对象
        List<String> expectedHashes = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            byte[] data = ("List test " + i).getBytes();
            String hash = objectPool.write(data);
            expectedHashes.add(hash);
        }
        
        // 列出所有对象
        List<String> actualHashes = objectPool.listAllObjects();
        
        assertEquals(expectedHashes.size(), actualHashes.size());
        
        // 验证所有哈希都被列出（顺序可能不同）
        Collections.sort(expectedHashes);
        Collections.sort(actualHashes);
        assertEquals(expectedHashes, actualHashes);
    }
    
    @Test
    void testListAllObjectsEmpty() throws IOException {
        List<String> hashes = objectPool.listAllObjects();
        assertTrue(hashes.isEmpty());
    }
    
    // ==================== 并发测试 ====================
    
    @Test
    void testConcurrentWriteDifferentObjects() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // 多线程并发写入不同对象
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    byte[] data = ("Concurrent object " + index).getBytes();
                    String hash = objectPool.write(data);
                    
                    // 验证可以读取
                    byte[] readData = objectPool.read(hash);
                    if (java.util.Arrays.equals(data, readData)) {
                        successCount.incrementAndGet();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(threadCount, successCount.get());
    }
    
    @Test
    void testConcurrentWriteSameObject() throws InterruptedException, IOException {
        int threadCount = 10;
        byte[] sharedData = "Shared data".getBytes();
        String expectedHash = HashUtil.hashToHex(sharedData);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        // 多线程并发写入相同对象
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String hash = objectPool.write(sharedData);
                    
                    // 验证哈希一致
                    if (hash.equals(expectedHash)) {
                        successCount.incrementAndGet();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // 所有线程都应该成功
        assertEquals(threadCount, successCount.get());
        
        // 验证对象只有一个
        assertTrue(objectPool.exists(expectedHash));
        
        // 验证文件内容正确
        byte[] readData = objectPool.read(expectedHash);
        assertArrayEquals(sharedData, readData);
    }
    
    // ==================== 错误处理测试 ====================
    
    @Test
    void testReadNonExistentObject() {
        String fakeHash = "a".repeat(64);
        
        assertThrows(IOException.class, () -> objectPool.read(fakeHash));
    }
    
    @Test
    void testInvalidHashFormat() {
        // 哈希太短
        assertThrows(IllegalArgumentException.class, 
                     () -> objectPool.read("abc"));
        
        // 哈希包含非法字符
        assertThrows(IllegalArgumentException.class,
                     () -> objectPool.read("g".repeat(64)));
        
        // 哈希包含大写字母
        assertThrows(IllegalArgumentException.class,
                     () -> objectPool.read("A".repeat(64)));
        
        // null哈希
        assertThrows(IllegalArgumentException.class,
                     () -> objectPool.read(null));
    }
    
    @Test
    void testWriteNullData() {
        assertThrows(IllegalArgumentException.class,
                     () -> objectPool.write(null));
    }
    
    @Test
    void testInvalidCompressionLevel() {
        assertThrows(IllegalArgumentException.class,
                     () -> new ObjectPool(objectsDir, 0));
        
        assertThrows(IllegalArgumentException.class,
                     () -> new ObjectPool(objectsDir, 100));
    }
    
    @Test
    void testNullObjectsDir() {
        assertThrows(IllegalArgumentException.class,
                     () -> new ObjectPool(null));
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 查找第一个可用的.mca文件
     */
    private Path findMcaFile() {
        try {
            // 常见的.mca文件位置
            String[] searchPaths = {
                "run/saves/New World/region",
                "run/saves/New World/entities",
                "run/saves/New World/poi"
            };
            
            for (String searchPath : searchPaths) {
                Path regionDir = Paths.get(searchPath);
                if (Files.exists(regionDir) && Files.isDirectory(regionDir)) {
                    try (var stream = Files.list(regionDir)) {
                        var mcaFile = stream
                            .filter(path -> path.toString().endsWith(".mca"))
                            .filter(path -> {
                                try {
                                    return Files.size(path) > 0;
                                } catch (IOException e) {
                                    return false;
                                }
                            })
                            .findFirst();
                        
                        if (mcaFile.isPresent()) {
                            return mcaFile.get();
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error searching for .mca files: " + e.getMessage());
        }
        
        return null;
    }
}
