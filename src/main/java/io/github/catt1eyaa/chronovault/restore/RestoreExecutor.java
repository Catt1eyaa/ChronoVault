package io.github.catt1eyaa.chronovault.restore;

import io.github.catt1eyaa.chronovault.region.AnvilWriter;
import io.github.catt1eyaa.chronovault.region.AnvilReader;
import io.github.catt1eyaa.chronovault.region.ChunkData;
import io.github.catt1eyaa.chronovault.snapshot.ChunkEntry;
import io.github.catt1eyaa.chronovault.snapshot.Manifest;
import io.github.catt1eyaa.chronovault.snapshot.ManifestSerializer;
import io.github.catt1eyaa.chronovault.snapshot.RegionEntry;
import io.github.catt1eyaa.chronovault.storage.ObjectPool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 恢复执行器 - 根据Manifest将快照内容恢复到新世界目录。
 *
 * <p>恢复策略：始终创建新世界目录，不覆盖任何现有世界。</p>
 */
public class RestoreExecutor {

    private static final int MAX_NAME_CONFLICT_RETRIES = 1000;

    private final Path backupDir;
    private final ObjectPool objectPool;

    /**
     * 创建恢复执行器。
     *
     * @param backupDir 备份根目录（包含objects/与snapshots/）
     */
    public RestoreExecutor(Path backupDir) {
        Objects.requireNonNull(backupDir, "backupDir cannot be null");
        this.backupDir = backupDir;
        this.objectPool = new ObjectPool(backupDir.resolve("objects"));
    }

    /**
     * 列出所有可用快照ID。
     *
     * @return 快照ID列表
     * @throws IOException 读取快照目录失败时抛出
     */
    public List<String> listSnapshots() throws IOException {
        return ManifestSerializer.listSnapshots(backupDir.resolve("snapshots"));
    }

    /**
     * 加载指定快照的Manifest。
     *
     * @param snapshotId 快照ID
     * @return Manifest对象
     * @throws IOException 读取失败时抛出
     */
    public Manifest loadManifest(String snapshotId) throws IOException {
        if (snapshotId == null || snapshotId.isBlank()) {
            throw new IllegalArgumentException("Snapshot ID cannot be null or blank");
        }
        return ManifestSerializer.load(backupDir.resolve("snapshots").resolve(snapshotId + ".json"));
    }

    /**
     * 恢复快照到新世界目录。
     *
     * <p>新世界命名规则：{baseWorldName}-restored-{snapshotId}，冲突时追加 -2, -3, ...</p>
     *
     * @param snapshotId 快照ID
     * @param savesDir 存档根目录（如 saves/）
     * @param baseWorldName 基础世界文件夹名（用于生成新世界名）
     * @return 恢复结果（含新世界路径）
     * @throws IOException 恢复失败时抛出
     */
    public RestoreResult restoreToNewWorld(String snapshotId, Path savesDir, String baseWorldName) throws IOException {
        Objects.requireNonNull(savesDir, "savesDir cannot be null");
        if (baseWorldName == null || baseWorldName.isBlank()) {
            throw new IllegalArgumentException("baseWorldName cannot be null or blank");
        }

        Manifest manifest = loadManifest(snapshotId);

        String targetWorldName = generateUniqueWorldName(savesDir, baseWorldName, snapshotId);
        Path targetWorldPath = savesDir.resolve(targetWorldName);
        Files.createDirectories(targetWorldPath);

        int restoredFiles = restoreRegularFiles(manifest, targetWorldPath);
        RestoreRegionStats regionStats = restoreRegions(manifest, targetWorldPath);

        return new RestoreResult(
            manifest.snapshotId(),
            restoredFiles,
            regionStats.restoredRegions(),
            regionStats.restoredChunks(),
            targetWorldName,
            targetWorldPath
        );
    }

    /**
     * 生成唯一的世界目录名。
     *
     * <p>基础名称格式：{baseWorldName}-restored-{snapshotId}</p>
     * <p>冲突时追加：-2, -3, ... 直到找到不存在的名称</p>
     *
     * @param savesDir 存档根目录
     * @param baseWorldName 基础世界名
     * @param snapshotId 快照ID
     * @return 唯一的世界目录名
     * @throws IOException 超过最大重试次数时抛出
     */
    private String generateUniqueWorldName(Path savesDir, String baseWorldName, String snapshotId) throws IOException {
        String baseName = baseWorldName + "-restored-" + snapshotId;

        if (!Files.exists(savesDir.resolve(baseName))) {
            return baseName;
        }

        for (int suffix = 2; suffix <= MAX_NAME_CONFLICT_RETRIES; suffix++) {
            String candidateName = baseName + "-" + suffix;
            if (!Files.exists(savesDir.resolve(candidateName))) {
                return candidateName;
            }
        }

        throw new IOException("无法找到唯一世界名，已尝试 " + MAX_NAME_CONFLICT_RETRIES + " 次");
    }

    private int restoreRegularFiles(Manifest manifest, Path worldDir) throws IOException {
        int count = 0;
        for (Map.Entry<String, String> entry : manifest.files().entrySet()) {
            String relativePath = entry.getKey();
            String hash = entry.getValue();

            byte[] data = objectPool.read(hash);
            Path targetFile = worldDir.resolve(relativePath);
            Path parent = targetFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempFile = targetFile.resolveSibling(targetFile.getFileName() + ".restore.tmp");
            Files.write(tempFile, data);
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            count++;
        }
        return count;
    }

    private RestoreRegionStats restoreRegions(Manifest manifest, Path worldDir) throws IOException {
        int restoredRegions = 0;
        int restoredChunks = 0;

        for (Map.Entry<String, Map<String, RegionEntry>> dimensionEntry : manifest.regions().entrySet()) {
            String dimension = dimensionEntry.getKey();
            Path dimensionRoot = resolveDimensionRoot(worldDir, dimension);

            for (RegionEntry regionEntry : dimensionEntry.getValue().values()) {
                List<ChunkData> chunks = new ArrayList<>();
                for (ChunkEntry chunkEntry : regionEntry.chunks()) {
                    if (chunkEntry.hash() == null) {
                        continue;
                    }
                    byte[] rawData = objectPool.read(chunkEntry.hash());
                    byte compressionType = detectCompressionType(rawData);
                    chunks.add(new ChunkData(chunkEntry.x(), chunkEntry.z(), compressionType, rawData));
                    restoredChunks++;
                }

                Path regionPath = resolveRegionFilePath(dimensionRoot, regionEntry.filename());
                Files.createDirectories(regionPath.getParent());
                if (AnvilReader.zeroByteRegionFormat().equals(regionEntry.format())) {
                    Files.write(regionPath, new byte[0]);
                } else {
                    AnvilWriter.writeRegion(regionPath, chunks);
                }
                restoredRegions++;
            }
        }

        return new RestoreRegionStats(restoredRegions, restoredChunks);
    }

    private Path resolveDimensionRoot(Path worldDir, String dimension) {
        return switch (dimension) {
            case "overworld" -> worldDir;
            case "nether" -> worldDir.resolve("DIM-1");
            case "end" -> worldDir.resolve("DIM1");
            default -> {
                int split = dimension.indexOf(':');
                if (split > 0 && split < dimension.length() - 1) {
                    String namespace = dimension.substring(0, split);
                    String path = dimension.substring(split + 1);
                    yield worldDir.resolve("dimensions").resolve(namespace).resolve(path);
                }
                yield worldDir;
            }
        };
    }

    private Path resolveRegionFilePath(Path dimensionRoot, String regionFilename) {
        String filename = Path.of(regionFilename).getFileName().toString();
        String normalized = regionFilename.replace('\\', '/');
        String folderName = resolveRegionSubdirectory(normalized);
        return dimensionRoot.resolve(folderName).resolve(filename);
    }

    private String resolveRegionSubdirectory(String normalizedPath) {
        if (normalizedPath.contains("/region/") || normalizedPath.startsWith("region/")) {
            return "region";
        }
        if (normalizedPath.contains("/entities/") || normalizedPath.startsWith("entities/")) {
            return "entities";
        }
        if (normalizedPath.contains("/poi/") || normalizedPath.startsWith("poi/")) {
            return "poi";
        }
        throw new IllegalArgumentException("Unsupported anvil file path in manifest: " + normalizedPath);
    }

    private byte detectCompressionType(byte[] rawData) {
        if (rawData.length >= 2) {
            int b0 = rawData[0] & 0xFF;
            int b1 = rawData[1] & 0xFF;

            if (b0 == 0x1F && b1 == 0x8B) {
                return ChunkData.COMPRESSION_GZIP;
            }

            // RFC1950 zlib 头: CMF低4位必须是8，且 (CMF*256+FLG)%31==0
            int cmf = b0;
            int flg = b1;
            if ((cmf & 0x0F) == 8 && ((cmf << 8) + flg) % 31 == 0) {
                return ChunkData.COMPRESSION_ZLIB;
            }
        }

        if (rawData.length >= 4) {
            if ((rawData[0] & 0xFF) == 0x04
                && (rawData[1] & 0xFF) == 0x22
                && (rawData[2] & 0xFF) == 0x4D
                && (rawData[3] & 0xFF) == 0x18) {
                return ChunkData.COMPRESSION_LZ4;
            }
        }

        return ChunkData.COMPRESSION_UNCOMPRESSED;
    }

    private record RestoreRegionStats(int restoredRegions, int restoredChunks) {
    }
}
