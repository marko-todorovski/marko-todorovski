package com.example.aidiagramgenerator.service;

import com.example.aidiagramgenerator.exception.RepositoryValidationException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Scans a ZIP archive (from an upload or a downloaded GitHub archive) for structural metadata
 * only. Nothing is ever written to disk and nothing is ever executed - entries are read
 * in-memory, one at a time, straight from the archive stream.
 *
 * <p>Safety measures applied while streaming:</p>
 * <ul>
 *     <li>Zip-slip protection: any entry whose normalized path escapes the archive root is rejected.</li>
 *     <li>Symbolic links are never followed or extracted - they are skipped entirely.</li>
 *     <li>Directory depth is capped.</li>
 *     <li>Total entry count is capped.</li>
 *     <li>Total uncompressed bytes read is capped (zip-bomb protection).</li>
 *     <li>Ignored directories (.git, node_modules, target, build, dist, bin, obj, vendor) are skipped.</li>
 * </ul>
 */
@Service
public class ZipExtractionService {

    private static final Set<String> IGNORED_DIR_NAMES = Set.of(
            ".git", "node_modules", "target", "build", "dist", "bin", "obj", "vendor");

    private static final Set<String> MARKER_FILE_NAMES = Set.of(
            "package.json", "pom.xml", "build.gradle", "build.gradle.kts",
            "requirements.txt", "pipfile", "go.mod", "gemfile", "composer.json");

    private static final int MAX_TOP_LEVEL_FOLDERS = 500;
    private static final long MAX_MARKER_FILE_BYTES = 1_000_000L;

    private final int maxFiles;
    private final int maxDepth;
    private final long maxArchiveBytes;

    public ZipExtractionService(
            @Value("${app.repository.max-files:50000}") int maxFiles,
            @Value("${app.repository.max-depth:40}") int maxDepth,
            @Value("${app.repository.max-archive-bytes:262144000}") long maxArchiveBytes) {
        this.maxFiles = maxFiles;
        this.maxDepth = maxDepth;
        this.maxArchiveBytes = maxArchiveBytes;
    }

    public record ScanResult(
            int fileCount,
            int folderCount,
            Set<String> topLevelFolders,
            Map<String, Integer> extensionCounts,
            Map<String, String> markerFiles) {
    }

    public ScanResult scanStream(InputStream zipStream) {
        int fileCount = 0;
        Set<String> folders = new LinkedHashSet<>();
        Set<String> topLevelFolders = new TreeSet<>();
        Map<String, Integer> extensionCounts = new LinkedHashMap<>();
        Map<String, String> markerFiles = new LinkedHashMap<>();
        long totalUncompressedBytes = 0;
        String rootPrefix = null;

        try (ZipArchiveInputStream zipIn = new ZipArchiveInputStream(zipStream)) {
            ZipArchiveEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                String rawName = entry.getName().replace('\\', '/');

                if (entry.isUnixSymlink()) {
                    // Never follow or extract symlinks - skip entirely without inspecting the target.
                    continue;
                }

                String normalized = normalizeAndValidate(rawName);
                if (normalized == null) {
                    continue;
                }

                if (rootPrefix == null && !entry.isDirectory()) {
                    rootPrefix = detectRootPrefix(normalized);
                }
                String logicalPath = stripRootPrefix(normalized, rootPrefix);
                if (logicalPath.isEmpty()) {
                    continue;
                }

                String[] segments = logicalPath.split("/");
                if (segments.length > maxDepth) {
                    throw new RepositoryValidationException(
                            "Archive exceeds the maximum allowed directory depth of " + maxDepth);
                }
                if (containsIgnoredSegment(segments)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    registerFolder(logicalPath, folders, topLevelFolders);
                    continue;
                }

                fileCount++;
                if (fileCount > maxFiles) {
                    throw new RepositoryValidationException(
                            "Archive exceeds the maximum allowed file count of " + maxFiles);
                }
                if (segments.length > 1) {
                    registerFolder(logicalPath.substring(0, logicalPath.length() - segments[segments.length - 1].length() - 1),
                            folders, topLevelFolders);
                }

                String fileName = segments[segments.length - 1];
                trackExtension(fileName, extensionCounts);

                boolean isMarker = segments.length <= 2 && MARKER_FILE_NAMES.contains(fileName.toLowerCase())
                        || fileName.toLowerCase().endsWith(".csproj");
                long entrySize = entry.getSize();
                if (isMarker && (entrySize < 0 || entrySize <= MAX_MARKER_FILE_BYTES)) {
                    byte[] content = readBounded(zipIn, MAX_MARKER_FILE_BYTES);
                    totalUncompressedBytes += content.length;
                    markerFiles.put(fileName.toLowerCase(), new String(content, StandardCharsets.UTF_8));
                } else {
                    totalUncompressedBytes += skipAndCount(zipIn);
                }
                if (totalUncompressedBytes > maxArchiveBytes) {
                    throw new RepositoryValidationException(
                            "Archive uncompressed size exceeds the maximum allowed size of " + maxArchiveBytes + " bytes");
                }
            }
        } catch (IOException e) {
            throw new RepositoryValidationException("Archive could not be read: " + e.getMessage());
        }

        return new ScanResult(fileCount, folders.size(), topLevelFolders, extensionCounts, markerFiles);
    }

    /**
     * Rejects zip-slip attempts (absolute paths, {@code ..} traversal segments) and returns
     * the forward-slash-normalized entry path, or {@code null} if the entry is empty/root.
     */
    private String normalizeAndValidate(String rawName) {
        String name = rawName;
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return null;
        }
        for (String segment : name.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                throw new RepositoryValidationException("Archive contains an unsafe path: " + rawName);
            }
        }
        return name;
    }

    private static String detectRootPrefix(String firstFilePath) {
        int slash = firstFilePath.indexOf('/');
        return slash < 0 ? null : firstFilePath.substring(0, slash + 1);
    }

    private static String stripRootPrefix(String path, String rootPrefix) {
        if (rootPrefix != null && path.startsWith(rootPrefix)) {
            return path.substring(rootPrefix.length());
        }
        return path;
    }

    private static boolean containsIgnoredSegment(String[] segments) {
        for (String segment : segments) {
            if (IGNORED_DIR_NAMES.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private static void registerFolder(String folderPath, Set<String> folders, Set<String> topLevelFolders) {
        if (folderPath.isEmpty()) {
            return;
        }
        folders.add(folderPath);
        if (topLevelFolders.size() < MAX_TOP_LEVEL_FOLDERS) {
            int slash = folderPath.indexOf('/');
            topLevelFolders.add(slash < 0 ? folderPath : folderPath.substring(0, slash));
        }
    }

    private static void trackExtension(String fileName, Map<String, Integer> extensionCounts) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return;
        }
        String extension = fileName.substring(dot + 1).toLowerCase();
        extensionCounts.merge(extension, 1, Integer::sum);
    }

    private static byte[] readBounded(InputStream in, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                break;
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private static long skipAndCount(InputStream in) throws IOException {
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
        }
        return total;
    }
}
