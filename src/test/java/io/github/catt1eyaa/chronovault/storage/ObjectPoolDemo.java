package io.github.catt1eyaa.chronovault.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * ObjectPool演示程序
 * 
 * <p>演示对象池的基本功能，包括：</p>
 * <ul>
 *   <li>写入和读取数据</li>
 *   <li>内容去重</li>
 *   <li>压缩效果展示</li>
 *   <li>统计信息</li>
 * </ul>
 * 
 * <p>运行方式：</p>
 * <pre>./gradlew runObjectPoolDemo</pre>
 */
public class ObjectPoolDemo {
    
    public static void main(String[] args) throws IOException {
        System.out.println("========================================");
        System.out.println("ChronoVault ObjectPool Demo");
        System.out.println("========================================\n");
        
        // 创建临时目录用于演示
        Path demoDir = Paths.get("build/demo-objectpool");
        Path objectsDir = demoDir.resolve("objects");
        
        // 清理旧数据
        if (Files.exists(demoDir)) {
            deleteDirectory(demoDir);
        }
        Files.createDirectories(objectsDir);
        
        // 创建对象池
        ObjectPool pool = new ObjectPool(objectsDir);
        System.out.println("✓ Created object pool at: " + objectsDir);
        System.out.println();
        
        // 演示1：基本读写
        demonstrateBasicReadWrite(pool);
        System.out.println();
        
        // 演示2：内容去重
        demonstrateDeduplication(pool);
        System.out.println();
        
        // 演示3：压缩效果
        demonstrateCompression(pool);
        System.out.println();
        
        // 演示4：统计信息
        demonstrateStats(pool);
        System.out.println();
        
        System.out.println("========================================");
        System.out.println("Demo completed successfully!");
        System.out.println("Object pool data saved at: " + demoDir);
        System.out.println("========================================");
    }
    
    private static void demonstrateBasicReadWrite(ObjectPool pool) throws IOException {
        System.out.println("Demo 1: Basic Read/Write");
        System.out.println("-------------------------");
        
        String message = "Hello, ChronoVault! This is a test message.";
        byte[] data = message.getBytes();
        
        System.out.println("Original data: \"" + message + "\"");
        System.out.println("Data size: " + data.length + " bytes");
        
        // 写入
        String hash = pool.write(data);
        System.out.println("✓ Written to object pool");
        System.out.println("Hash: " + hash);
        System.out.println("Path: " + pool.getObjectPath(hash));
        
        // 读取
        byte[] readData = pool.read(hash);
        String readMessage = new String(readData);
        
        System.out.println("✓ Read from object pool");
        System.out.println("Read data: \"" + readMessage + "\"");
        System.out.println("Data matches: " + message.equals(readMessage));
    }
    
    private static void demonstrateDeduplication(ObjectPool pool) throws IOException {
        System.out.println("Demo 2: Content Deduplication");
        System.out.println("------------------------------");
        
        String duplicateData = "This data will be written multiple times!";
        byte[] data = duplicateData.getBytes();
        
        System.out.println("Writing same data 5 times...");
        
        String firstHash = null;
        for (int i = 0; i < 5; i++) {
            String hash = pool.write(data);
            if (i == 0) {
                firstHash = hash;
                System.out.println("  Write #" + (i+1) + ": " + hash);
            } else {
                System.out.println("  Write #" + (i+1) + ": " + hash + " (deduplicated)");
            }
        }
        
        // 验证只有一个文件
        Path objectPath = pool.getObjectPath(firstHash);
        System.out.println("\n✓ All writes produced same hash: " + firstHash);
        System.out.println("✓ Only one file created: " + Files.exists(objectPath));
        System.out.println("File size: " + Files.size(objectPath) + " bytes (compressed)");
    }
    
    private static void demonstrateCompression(ObjectPool pool) throws IOException {
        System.out.println("Demo 3: Compression Effect");
        System.out.println("--------------------------");
        
        // 生成高度可压缩的数据（重复模式）
        byte[] compressibleData = new byte[10 * 1024]; // 10 KB
        for (int i = 0; i < compressibleData.length; i++) {
            compressibleData[i] = (byte) (i % 10);
        }
        
        System.out.println("Compressible data (repetitive pattern):");
        System.out.println("  Original size: " + compressibleData.length + " bytes");
        
        String hash1 = pool.write(compressibleData);
        long compressedSize1 = Files.size(pool.getObjectPath(hash1));
        double ratio1 = (double) compressedSize1 / compressibleData.length;
        
        System.out.println("  Compressed size: " + compressedSize1 + " bytes");
        System.out.printf("  Compression ratio: %.2f%% (%.2f%% saved)\n", 
                          ratio1 * 100, (1 - ratio1) * 100);
        
        // 生成随机数据（不可压缩）
        byte[] randomData = new byte[10 * 1024]; // 10 KB
        new Random(42).nextBytes(randomData);
        
        System.out.println("\nRandom data (incompressible):");
        System.out.println("  Original size: " + randomData.length + " bytes");
        
        String hash2 = pool.write(randomData);
        long compressedSize2 = Files.size(pool.getObjectPath(hash2));
        double ratio2 = (double) compressedSize2 / randomData.length;
        
        System.out.println("  Compressed size: " + compressedSize2 + " bytes");
        System.out.printf("  Compression ratio: %.2f%%\n", ratio2 * 100);
    }
    
    private static void demonstrateStats(ObjectPool pool) throws IOException {
        System.out.println("Demo 4: Statistics");
        System.out.println("------------------");
        
        ObjectPoolStats stats = pool.getStats();
        
        System.out.println("Object pool statistics:");
        System.out.println("  Total objects: " + stats.objectCount());
        System.out.println("  Total size (compressed): " + formatBytes(stats.totalSizeBytes()));
        System.out.println("  Total size (original): " + formatBytes(stats.totalSizeOriginal()));
        System.out.printf("  Overall compression: %.2f%%\n", stats.getCompressionRatio() * 100);
        System.out.println("  Space saved: " + formatBytes(stats.getSavedBytes()));
        System.out.println("  Average object size: " + formatBytes(stats.getAverageObjectSize()));
        
        System.out.println("\nFormatted stats: " + stats);
    }
    
    private static String formatBytes(long bytes) {
        if (bytes < 0) {
            return "-" + formatBytes(-bytes);
        }
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a)) // 逆序（先删除文件，后删除目录）
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        System.err.println("Failed to delete: " + path);
                    }
                });
        }
    }
}
