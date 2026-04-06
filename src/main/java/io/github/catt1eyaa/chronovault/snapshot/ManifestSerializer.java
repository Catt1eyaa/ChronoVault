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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manifest序列化器 - 负责将快照清单持久化为JSON格式
 * 
 * <p>JSON格式特性：
 * <ul>
 *   <li>Pretty-printing: 便于人类阅读和版本控制diff</li>
 *   <li>保留null值: 用于表示未生成的chunk</li>
 *   <li>UTF-8编码: 支持国际化描述</li>
 *   <li>原子写入: 避免写入过程中的数据损坏</li>
 * </ul>
 */
public class ManifestSerializer {
    
    private static final Gson GSON = createGson();
    
    /**
     * 创建配置好的Gson实例
     * 线程安全，可重复使用
     */
    private static Gson createGson() {
        return new GsonBuilder()
            .setPrettyPrinting()           // 格式化输出，便于阅读
            .disableHtmlEscaping()         // 不转义特殊字符
            .serializeNulls()              // 包含null值（用于空chunk）
            .create();
    }
    
    /**
     * 将Manifest序列化为JSON字符串
     * @param manifest 快照清单
     * @return 格式化的JSON字符串
     * @throws NullPointerException 如果manifest为null
     */
    public static String toJson(Manifest manifest) {
        if (manifest == null) {
            throw new NullPointerException("Manifest cannot be null");
        }
        return GSON.toJson(manifest);
    }
    
    /**
     * 从JSON字符串反序列化Manifest
     * @param json JSON字符串
     * @return Manifest对象
     * @throws JsonSyntaxException 如果JSON格式错误
     * @throws NullPointerException 如果json为null
     */
    public static Manifest fromJson(String json) {
        if (json == null) {
            throw new NullPointerException("JSON string cannot be null");
        }
        return GSON.fromJson(json, Manifest.class);
    }
    
    /**
     * 保存Manifest到文件（原子写入）
     * 
     * <p>实现原子写入：先写入临时文件，成功后再重命名。
     * 这样即使写入过程中崩溃，也不会破坏原有文件。
     * 
     * @param manifest 快照清单
     * @param filePath 文件路径（通常是 backups/snapshots/&lt;snapshot_id&gt;.json）
     * @throws IOException 如果写入失败
     * @throws NullPointerException 如果参数为null
     */
    public static void save(Manifest manifest, Path filePath) throws IOException {
        if (manifest == null) {
            throw new NullPointerException("Manifest cannot be null");
        }
        if (filePath == null) {
            throw new NullPointerException("File path cannot be null");
        }
        
        // 确保父目录存在
        Path parentDir = filePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
        
        // 原子写入：先写临时文件
        Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
        
        try {
            // 序列化并写入临时文件
            String json = toJson(manifest);
            Files.writeString(tempFile, json, StandardCharsets.UTF_8);
            
            // 原子替换
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // 清理临时文件
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
                // 忽略清理失败
            }
            throw e;
        }
    }
    
    /**
     * 从文件加载Manifest
     * @param filePath 文件路径
     * @return Manifest对象
     * @throws IOException 如果读取失败或文件不存在
     * @throws JsonSyntaxException 如果JSON格式错误
     * @throws NullPointerException 如果filePath为null
     */
    public static Manifest load(Path filePath) throws IOException {
        if (filePath == null) {
            throw new NullPointerException("File path cannot be null");
        }
        
        String json = Files.readString(filePath, StandardCharsets.UTF_8);
        return fromJson(json);
    }
    
    /**
     * 列出指定目录下所有快照文件
     * @param snapshotsDir 快照目录（通常是 backups/snapshots/）
     * @return 快照ID列表（从文件名提取，按修改时间排序）
     * @throws IOException 如果目录不存在或无法读取
     * @throws NullPointerException 如果snapshotsDir为null
     */
    public static List<String> listSnapshots(Path snapshotsDir) throws IOException {
        if (snapshotsDir == null) {
            throw new NullPointerException("Snapshots directory cannot be null");
        }
        
        // 如果目录不存在，返回空列表
        if (!Files.exists(snapshotsDir)) {
            return List.of();
        }
        
        try (Stream<Path> files = Files.list(snapshotsDir)) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> {
                    String filename = path.getFileName().toString();
                    // 去掉 .json 后缀
                    return filename.substring(0, filename.length() - 5);
                })
                .sorted()
                .collect(Collectors.toList());
        }
    }
    
    /**
     * 加载指定目录下所有快照的Manifest
     * @param snapshotsDir 快照目录
     * @return snapshotId -&gt; Manifest 的映射
     * @throws IOException 如果读取失败
     * @throws NullPointerException 如果snapshotsDir为null
     */
    public static Map<String, Manifest> loadAllSnapshots(Path snapshotsDir) throws IOException {
        if (snapshotsDir == null) {
            throw new NullPointerException("Snapshots directory cannot be null");
        }
        
        List<String> snapshotIds = listSnapshots(snapshotsDir);
        
        return snapshotIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> {
                    try {
                        return load(snapshotsDir.resolve(id + ".json"));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load snapshot: " + id, e);
                    }
                }
            ));
    }
}
