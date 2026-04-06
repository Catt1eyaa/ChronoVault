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

package io.github.catt1eyaa.chronovault.backup;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Scanner for Minecraft world directories.
 * Traverses the world directory and categorizes files into regular files and region files.
 */
public class WorldScanner {
    
    /**
     * Scan a world directory and categorize all files
     * 
     * @param worldDir World directory (e.g., saves/MyWorld/)
     * @return WorldFiles object containing categorized files
     * @throws IOException If scanning fails
     */
    public static WorldFiles scan(Path worldDir) throws IOException {
        if (!Files.exists(worldDir)) {
            throw new IOException("World directory does not exist: " + worldDir);
        }
        
        if (!Files.isDirectory(worldDir)) {
            throw new IOException("Path is not a directory: " + worldDir);
        }
        
        Map<String, Path> regularFiles = new HashMap<>();
        Map<String, Map<String, Path>> regionFiles = new HashMap<>();
        
        // Walk the directory tree
        try (var stream = Files.walk(worldDir)) {
            stream.filter(Files::isRegularFile)  // Only process regular files
                  .filter(path -> {
                      try {
                          // Skip symbolic links to avoid infinite loops
                          return !Files.isSymbolicLink(path);
                      } catch (Exception e) {
                          return false;
                      }
                  })
                  .forEach(path -> {
                      try {
                          String fileName = path.getFileName().toString();
                          
                          // Skip files that shouldn't be backed up
                          if (!shouldBackup(fileName)) {
                              return;
                          }

                          // Get relative path from world directory
                          Path relativePath = worldDir.relativize(path);
                          String relativePathStr = relativePath.toString().replace('\\', '/');
                          
                          // Categorize as region file or regular file
                          if (isManagedAnvilFile(worldDir, path)) {
                              String dimension = extractDimensionName(worldDir, path);
                              regionFiles.computeIfAbsent(dimension, k -> new HashMap<>())
                                        .put(relativePathStr, path);
                          } else {
                              regularFiles.put(relativePathStr, path);
                          }
                      } catch (Exception e) {
                          // Log warning but continue scanning
                          System.err.println("Warning: Failed to process file " + path + ": " + e.getMessage());
                      }
                  });
        }
        
        return new WorldFiles(regularFiles, regionFiles);
    }
    
    /**
     * Check if a file should be backed up
     * 
     * @param fileName File name to check
     * @return true if the file should be backed up
     */
    private static boolean shouldBackup(String fileName) {
        // Exclude session lock
        if ("session.lock".equals(fileName)) {
            return false;
        }
        
        // Exclude temporary files
        if (fileName.endsWith(".tmp") || fileName.endsWith(".temp")) {
            return false;
        }
        
        // Exclude region file temporary versions
        if (fileName.endsWith(".mca.tmp")) {
            return false;
        }
        
        // Exclude old versions (except level.dat_old)
        if (fileName.endsWith("_old") && !fileName.equals("level.dat_old")) {
            return false;
        }
        
        // Exclude hidden files
        if (fileName.startsWith(".")) {
            return false;
        }
        
        // Include everything else
        return true;
    }
    
    /**
     * Check if a file is a region file (.mca)
     * 
     * @param fileName File name to check
     * @return true if the file is a .mca file
     */
    private static boolean isManagedAnvilFile(Path worldDir, Path filePath) {
        String fileName = filePath.getFileName().toString();
        if (!fileName.endsWith(".mca")) {
            return false;
        }

        Path relativePath = worldDir.relativize(filePath);
        if (relativePath.getNameCount() < 2) {
            return false;
        }

        String parentDir = relativePath.getName(relativePath.getNameCount() - 2).toString();
        return "region".equals(parentDir) || "entities".equals(parentDir) || "poi".equals(parentDir);
    }

    /**
     * Extract dimension name from a file path
     * 
     * Mapping rules:
     * - world/region/r.*.mca -> overworld
     * - world/entities/r.*.mca -> overworld
     * - world/DIM-1/region/r.*.mca -> nether
     * - world/DIM-1/entities/r.*.mca -> nether
     * - world/DIM1/region/r.*.mca -> end
     * - world/DIM1/entities/r.*.mca -> end
     * - world/dimensions/{namespace}/{path}/region/r.*.mca -> {namespace}:{path}
     * 
     * @param worldDir World root directory
     * @param filePath File path
     * @return Dimension name
     */
    private static String extractDimensionName(Path worldDir, Path filePath) {
        Path relativePath = worldDir.relativize(filePath);
        
        // Convert to string with forward slashes for consistent parsing
        String pathStr = relativePath.toString().replace('\\', '/');
        String[] parts = pathStr.split("/");
        
        if (parts.length < 2) {
            return "overworld";  // Default
        }
        
        // Check first component
        String firstDir = parts[0];
        
        switch (firstDir) {
            case "region":
            case "entities":
                // Root level region/entities -> overworld
                return "overworld";
                
            case "DIM-1":
                // Nether dimension
                return "nether";
                
            case "DIM1":
                // End dimension
                return "end";
                
            case "dimensions":
                // Custom dimension: dimensions/{namespace}/{path}/...
                if (parts.length >= 3) {
                    String namespace = parts[1];
                    String path = parts[2];
                    return namespace + ":" + path;
                }
                return "unknown";
                
            default:
                // Unknown structure
                return "overworld";
        }
    }
}
