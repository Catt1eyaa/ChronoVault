# ChronoVault Region Format Module

This module provides complete support for reading and writing Minecraft's Anvil region file format (.mca files). It is designed for content-addressable storage (CAS) based backup systems.

## Overview

The Anvil format stores chunks in region files, where each region contains up to 1024 chunks arranged in a 32x32 grid. This module allows you to:

- Read individual chunks or entire region files
- Extract chunk data for content-addressable storage
- Reconstruct region files from stored chunks (for restore operations)
- Validate and verify region file integrity

## Architecture

### Core Classes

#### `ChunkLocation.java`
Represents the location metadata for a chunk within a region file:
- Chunk coordinates (x, z) within the region (0-31)
- Sector offset and count (4KB sectors)
- Last modification timestamp

#### `ChunkData.java`
Represents the actual chunk data:
- Chunk coordinates (x, z)
- Compression type (GZip, Zlib, Uncompressed, or LZ4)
- Raw compressed NBT data

#### `AnvilReader.java`
Reads Anvil region files:
- `readHeader()` - Reads location/timestamp tables
- `readChunk()` - Reads a specific chunk's data
- `readAllChunks()` - Reads all non-empty chunks
- `countChunks()` - Counts chunks in a region

#### `AnvilWriter.java`
Writes Anvil region files:
- `writeRegion()` - Writes chunks to a .mca file
- Supports partial region reconstruction
- Handles sector allocation and alignment
- Preserves or generates timestamps

## File Format

### Anvil Region Format Structure

```
┌─────────────────────────────────────┐
│ Location Table (0-4095 bytes)       │  1024 × 4-byte entries
│ - 3 bytes: sector offset (big-endian)
│ - 1 byte:  sector count             │
├─────────────────────────────────────┤
│ Timestamp Table (4096-8191 bytes)   │  1024 × 4-byte Unix timestamps
├─────────────────────────────────────┤
│ Chunk Data (8192+ bytes)            │  Sector-aligned (4KB) chunks
│ For each chunk:                     │
│   - 4 bytes: data length            │
│   - 1 byte:  compression type       │
│   - N bytes: compressed NBT         │
│   - Padding to 4KB boundary         │
└─────────────────────────────────────┘
```

### Compression Types

| Type | Value | Description |
|------|-------|-------------|
| GZip | 1 | GZip compression (RFC 1952) |
| Zlib | 2 | Zlib compression (RFC 1950) - most common |
| Uncompressed | 3 | No compression |
| LZ4 | 4 | LZ4 compression (modern format) |

## Usage Examples

### Reading Chunks from a Region

```java
// Read all chunks
Path regionFile = Paths.get("world/region/r.0.0.mca");
List<ChunkData> chunks = AnvilReader.readAllChunks(regionFile);

// Read just the header
List<ChunkLocation> locations = AnvilReader.readHeader(regionFile);

// Read a specific chunk
ChunkLocation location = locations.stream()
    .filter(loc -> loc.x() == 5 && loc.z() == 10)
    .findFirst()
    .orElse(null);

if (location != null && !location.isEmpty()) {
    ChunkData chunk = AnvilReader.readChunk(regionFile, location);
    System.out.println(chunk);
}

// Count chunks
int count = AnvilReader.countChunks(regionFile);
```

### Writing Chunks to a Region

```java
// Write all chunks to a new region file
List<ChunkData> chunks = /* loaded chunks */;
Path outputFile = Paths.get("backup/region/r.0.0.mca");
AnvilWriter.writeRegion(outputFile, chunks);

// Write with custom timestamps
Map<String, Integer> timestamps = new HashMap<>();
timestamps.put("5,10", 1609459200); // 2021-01-01 for chunk (5,10)
AnvilWriter.writeRegion(outputFile, chunks, timestamps);

// Partial region reconstruction
List<ChunkData> partialChunks = chunks.subList(0, 100);
AnvilWriter.writeRegion(outputFile, partialChunks);
```

### Round-Trip Verification

```java
// Read original
List<ChunkData> original = AnvilReader.readAllChunks(originalFile);

// Write to new file
AnvilWriter.writeRegion(newFile, original);

// Verify
List<ChunkData> readBack = AnvilReader.readAllChunks(newFile);
assert original.equals(readBack);
```

## Testing

### Running Tests

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "*AnvilWriterTest"

# Run specific test method
./gradlew test --tests "*AnvilWriterTest.testRoundTripConsistency"
```

### Demo Programs

```bash
# Round-trip verification demo
./gradlew runWriterDemo

# Byte-level file comparison
./gradlew compareFiles
```

### Test Coverage

The test suite includes:

1. **Round-trip consistency tests**
   - Verify that reading and writing preserves all chunk data
   - Uses real-world region data when available, with synthetic/conditional fallback in CI or clean environments

2. **Partial chunk writing**
   - Test writing subsets of chunks
   - Verify missing chunks are properly handled

3. **File format validation**
   - Header structure correctness
   - Sector alignment verification
   - Timestamp handling

4. **Error handling**
   - Invalid coordinates (out of 0-31 range)
   - Invalid compression types
   - Empty chunk lists

## Design Decisions

### Sector Allocation Strategy

The writer uses **compact sequential allocation**:
- Chunks are sorted by (z, x) coordinates
- First chunk starts at sector 2 (after headers)
- No fragmentation - all chunks are consecutive
- Padding to 4KB boundaries with zeros

This differs from the original Minecraft implementation which may have:
- Fragmentation from deleted chunks
- Non-sequential allocation
- Gaps in the sector map

Both approaches are valid and produce functionally equivalent files.

### Timestamp Handling

By default, the writer uses the current Unix timestamp for all chunks unless custom timestamps are provided. This ensures:
- Valid Minecraft-compatible files
- Reasonable "last modified" times
- Flexibility for backup restoration scenarios

### Error Handling

The implementation validates:
- Chunk coordinates are in range [0, 31]
- Compression types are valid (1-4)
- File sizes and sector boundaries
- Data length consistency

Invalid inputs throw `IllegalArgumentException` with descriptive messages.

## Performance Characteristics

### Memory Usage

- Header reading: ~8KB per region file
- Chunk data: Variable, typically 1-100KB per chunk compressed
- Full region: ~1-5MB typical for populated regions

### I/O Patterns

- **Reading**: Sequential reads of headers, random access for chunks
- **Writing**: Sequential write with buffered I/O
- **Optimization**: Use `FileChannel` for efficient I/O

## Integration with CAS System

This module is designed to integrate with content-addressable storage:

1. **Extraction**: Use `AnvilReader` to extract chunks
2. **Storage**: Store each `ChunkData` by content hash
3. **Deduplication**: Identical chunks share the same hash
4. **Restoration**: Use `AnvilWriter` to reconstruct regions from stored chunks

## Validation and Verification

### Automated Tests
- Region format tests currently include `AnvilWriterTest` and `AnvilReaderTest`
- Coverage includes round-trip consistency, partial writes, format validity, timestamps, invalid inputs, and zero-byte region handling
- Some scenarios use a local Minecraft world file if present; exact chunk counts are environment-dependent

### Manual Verification
You can verify generated files work in Minecraft:

```bash
# Generate round-trip file
./gradlew runWriterDemo

# Copy to test world
cp "run/saves/New World/region/r.0.0.roundtrip.mca" \
   "run/saves/Test World/region/r.0.0.mca"

# Load in Minecraft and verify chunks load correctly
```

## Known Limitations

1. **Byte-identical reproduction**: The writer produces functionally equivalent but not byte-identical files due to different sector allocation strategies. This is expected and acceptable.

2. **Minecraft version support**: Tested with Minecraft 1.21.1. The Anvil format has been stable since Minecraft 1.2, so it should work with most versions.

3. **File size**: Written files may be smaller than originals if the original had fragmentation.

## Future Enhancements

Potential improvements for future versions:

- [ ] Support for oversized chunk data (separate .mcc files)
- [ ] Parallel chunk processing for large regions
- [ ] Streaming API for very large worlds
- [ ] Chunk NBT parsing and validation
- [ ] Region file compaction tool
- [ ] Benchmark suite for performance testing

## References

- [Minecraft Wiki - Region File Format](https://minecraft.wiki/w/Region_file_format)
- [NBT Format Specification](https://minecraft.wiki/w/NBT_format)
- [Anvil Format Discussion](https://minecraft.wiki/w/Anvil_file_format)

## License

This code is part of the ChronoVault mod for Minecraft. See the main project LICENSE file for details.
