package net.shasankp000.Database;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public class VectorExtensionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorExtensionHelper.class);

    // === SQLITE-VEC download URLs ===
    private static final String WINDOWS_VEC_URL        = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-windows-x86_64.tar.gz";
    private static final String LINUX_X86_VEC_URL      = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-linux-x86_64.tar.gz";
    private static final String LINUX_ARM64_VEC_URL    = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-linux-aarch64.tar.gz";
    private static final String MACOS_X86_VEC_URL      = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-macos-x86_64.tar.gz";
    private static final String MACOS_ARM64_VEC_URL    = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.6/sqlite-vec-0.1.6-loadable-macos-aarch64.tar.gz";

    private static final String VECTOR_FILENAME_WINDOWS = "vec0.dll";
    private static final String VECTOR_FILENAME_LINUX   = "vec0.so";
    private static final String VECTOR_FILENAME_MACOS   = "vec0.dylib";

    // =========================================================================
    // Architecture helpers
    // =========================================================================

    /**
     * Returns true if the JVM is running on an ARM64 / Apple-Silicon CPU.
     * Checks os.arch for: aarch64, arm64, armv8.
     */
    private static boolean isArm64() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
        return arch.contains("aarch64") || arch.contains("arm64") || arch.contains("armv8");
    }

    // =========================================================================
    // Cleanup
    // =========================================================================

    private static void cleanupOldVecFiles(Path dir, String correctName) {
        try {
            String[] oldNames = {"vector0.dll", "vector0.so", "vector0.dylib"};
            for (String oldName : oldNames) {
                if (!oldName.equals(correctName)) {
                    Path oldFile = dir.resolve(oldName);
                    if (Files.exists(oldFile)) {
                        Files.delete(oldFile);
                        LOGGER.info("🧹 Cleaned up old incorrectly-named file: {}", oldFile);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("⚠️ Failed to clean up old files: {}", e.getMessage());
        }
    }

    // =========================================================================
    // SQLITE-VEC
    // =========================================================================

    public static Path ensureSqliteVecPresent() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        boolean arm64  = isArm64();

        String downloadUrl;
        String targetFileName;

        if (osName.contains("win")) {
            // Windows ARM64 builds are not yet published by asg017; x86_64 runs
            // under emulation on Windows on ARM so use it as a fallback.
            downloadUrl    = WINDOWS_VEC_URL;
            targetFileName = VECTOR_FILENAME_WINDOWS;
        } else if (osName.contains("nux") || osName.contains("nix")) {
            downloadUrl    = arm64 ? LINUX_ARM64_VEC_URL : LINUX_X86_VEC_URL;
            targetFileName = VECTOR_FILENAME_LINUX;
        } else if (osName.contains("mac")) {
            downloadUrl    = arm64 ? MACOS_ARM64_VEC_URL : MACOS_X86_VEC_URL;
            targetFileName = VECTOR_FILENAME_MACOS;
        } else {
            throw new UnsupportedOperationException("Unsupported OS for SQLite-Vec: " + osName);
        }

        LOGGER.info("💻 Detected OS='{}' arch='{}' arm64={}",
                osName, System.getProperty("os.arch"), arm64);

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path vecDir    = configDir.resolve("sqlite_vector/sqlite-vec");
        if (!Files.exists(vecDir)) Files.createDirectories(vecDir);

        Path outputPath = vecDir.resolve(targetFileName);

        cleanupOldVecFiles(vecDir, targetFileName);

        if (Files.exists(outputPath)) {
            LOGGER.info("✅ sqlite-vec already present at: {}", outputPath);
            return outputPath;
        }

        LOGGER.info("⬇️ Downloading sqlite-vec from {}", downloadUrl);
        Path gzPath  = vecDir.resolve("sqlite-vec.tar.gz");
        Path tarPath = vecDir.resolve("sqlite-vec.tar");

        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, gzPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(gzPath));
             OutputStream out = Files.newOutputStream(tarPath)) {
            gzipIn.transferTo(out);
        }
        try (InputStream tarIn = Files.newInputStream(tarPath)) {
            boolean found = safeExtractTar(tarIn, targetFileName, outputPath);
            if (!found) throw new IOException("❌ sqlite-vec extraction failed!");
        }

        LOGGER.info("✅ sqlite-vec ready at: {}", outputPath);
        return outputPath;
    }

    // =========================================================================
    // Extension loaders
    // =========================================================================

    public static void loadSqliteVecExtension(Connection conn, Path vecPath) throws SQLException, IOException {
        String path = vecPath.toAbsolutePath().toString()
                .replaceAll("\\.(dll|so|dylib)$", "")
                .replace("\\", "\\\\");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT load_extension('" + path + "', 'sqlite3_vec_init');");
            LOGGER.info("✅ Loaded sqlite-vec extension");
            ResultSet rs = stmt.executeQuery("SELECT vec_version();");
            if (rs.next()) LOGGER.info("✅ sqlite-vec version: {}", rs.getString(1));
        }
    }

    // =========================================================================
    // TAR extraction
    // =========================================================================

    private static long parseTarSize(byte[] header, int offset) {
        if ((header[offset] & 0x80) != 0) {
            long val = 0;
            for (int i = 1; i < 12; i++) val = (val << 8) | (header[offset + i] & 0xFF);
            return val;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + 12; i++) {
            char c = (char) (header[i] & 0xFF);
            if (c >= '0' && c <= '7') sb.append(c);
        }
        return sb.length() == 0 ? 0 : Long.parseLong(sb.toString(), 8);
    }

    private static boolean safeExtractTar(InputStream tarInputStream, String targetFileName, Path outputPath) throws IOException {
        byte[] header = new byte[512];
        boolean found = false;

        while (true) {
            int read = tarInputStream.read(header);
            if (read < 512) break;

            String name = new String(header, 0, 100).trim();
            if (name.isEmpty()) break;

            long size    = parseTarSize(header, 124);
            boolean isMatch = name.equals(targetFileName) || name.endsWith("/" + targetFileName);

            LOGGER.info("🔍 TAR entry: {} ({} bytes)", name, size);

            if (isMatch) {
                LOGGER.info("✅ Found '{}' in archive, extracting to: {}", targetFileName, outputPath);
                try (OutputStream out = Files.newOutputStream(outputPath)) {
                    byte[] buf = new byte[4096];
                    long remaining = size;
                    while (remaining > 0) {
                        int len = tarInputStream.read(buf, 0, (int) Math.min(buf.length, remaining));
                        if (len == -1) break;
                        out.write(buf, 0, len);
                        remaining -= len;
                    }
                }
                LOGGER.info("✅ Successfully extracted: {}", outputPath);
                found = true;
            }

            long skip = size + (512 - (size % 512)) % 512;
            if (!isMatch) {
                while (skip > 0) {
                    long skipped = tarInputStream.skip(skip);
                    if (skipped <= 0) break;
                    skip -= skipped;
                }
            }
        }
        return found;
    }
}
