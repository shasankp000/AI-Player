package net.shasankp000.Database;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.Function;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

    // === SQLITE-VSS download URLs ===
    private static final String VSS_LINUX_X86_URL      = "https://github.com/asg017/sqlite-vss/releases/download/v0.1.2/sqlite-vss-v0.1.2-loadable-linux-x86_64.tar.gz";
    private static final String VSS_LINUX_ARM64_URL    = "https://github.com/asg017/sqlite-vss/releases/download/v0.1.2/sqlite-vss-v0.1.2-loadable-linux-aarch64.tar.gz";
    private static final String VSS_MACOS_X86_URL      = "https://github.com/asg017/sqlite-vss/releases/download/v0.1.2/sqlite-vss-v0.1.2-loadable-macos-x86_64.tar.gz";
    private static final String VSS_MACOS_ARM64_URL    = "https://github.com/asg017/sqlite-vss/releases/download/v0.1.2/sqlite-vss-v0.1.2-loadable-macos-aarch64.tar.gz";

    private static final String VSS_FILENAME_LINUX = "vss0.so";
    private static final String VSS_FILENAME_MACOS = "vss0.dylib";

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
    // SQLITE-VSS
    // =========================================================================

    public static Path ensureSqliteVssPresent() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        boolean arm64  = isArm64();

        String downloadUrl;
        String targetFileName;

        if (osName.contains("nux") || osName.contains("nix")) {
            downloadUrl    = arm64 ? VSS_LINUX_ARM64_URL : VSS_LINUX_X86_URL;
            targetFileName = VSS_FILENAME_LINUX;
        } else if (osName.contains("mac")) {
            downloadUrl    = arm64 ? VSS_MACOS_ARM64_URL : VSS_MACOS_X86_URL;
            targetFileName = VSS_FILENAME_MACOS;
        } else {
            throw new UnsupportedOperationException("sqlite-vss is not supported on this OS: " + osName);
        }

        LOGGER.info("💻 Detected OS='{}' arch='{}' arm64={}",
                osName, System.getProperty("os.arch"), arm64);

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path vssDir    = configDir.resolve("sqlite_vector/sqlite-vss");
        if (!Files.exists(vssDir)) Files.createDirectories(vssDir);

        Path outputPath = vssDir.resolve(targetFileName);

        // Clean up stale files
        try {
            String[] unwantedFiles = {"vector0.so", "vector0.dylib"};
            for (String unwanted : unwantedFiles) {
                Path f = vssDir.resolve(unwanted);
                if (Files.exists(f)) { Files.delete(f); LOGGER.info("🧹 Cleaned up: {}", f); }
            }
        } catch (IOException e) {
            LOGGER.warn("⚠️ Failed to clean up unwanted files: {}", e.getMessage());
        }

        String vector0FileName = osName.contains("nux") || osName.contains("nix") ? "vector0.so" : "vector0.dylib";
        Path   vector0OutputPath = vssDir.resolve(vector0FileName);

        if (Files.exists(outputPath) && Files.exists(vector0OutputPath)) {
            LOGGER.info("✅ sqlite-vss already present at: {}", outputPath);
            return outputPath;
        }

        LOGGER.info("⬇️ Downloading sqlite-vss from {}", downloadUrl);
        Path gzPath  = vssDir.resolve("sqlite-vss.tar.gz");
        Path tarPath = vssDir.resolve("sqlite-vss.tar");

        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, gzPath, StandardCopyOption.REPLACE_EXISTING);
        }
        try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(gzPath));
             OutputStream out = Files.newOutputStream(tarPath)) {
            gzipIn.transferTo(out);
        }
        try (InputStream tarIn = Files.newInputStream(tarPath)) {
            boolean found = safeExtractTar(tarIn, targetFileName, outputPath);
            if (!found) throw new IOException("❌ sqlite-vss extraction failed!");
        }
        try (InputStream tarIn = Files.newInputStream(tarPath)) {
            boolean found = safeExtractTar(tarIn, vector0FileName, vector0OutputPath);
            if (!found) LOGGER.warn("⚠️ vector0 not found in archive — vss0 may fail to load");
            else LOGGER.info("✅ vector0 extracted to: {}", vector0OutputPath);
        }

        LOGGER.info("✅ sqlite-vss ready at: {}", outputPath);
        return outputPath;
    }

    // =========================================================================
    // Extension loaders
    // =========================================================================

    public static void loadSqliteVector0Extension(Connection conn, Path vssDir) throws SQLException {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        String vector0FileName = osName.contains("nux") || osName.contains("nix") ? "vector0.so" : "vector0.dylib";
        Path vector0Path = vssDir.resolve(vector0FileName);

        if (!Files.exists(vector0Path)) {
            LOGGER.warn("⚠️ vector0 not found at {}, skipping", vector0Path);
            return;
        }

        String path = vector0Path.toAbsolutePath().toString().replaceAll("\\.(dll|so|dylib)$", "");
        path = path.replace("\\", "\\\\");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT load_extension('" + path + "', 'sqlite3_vector_init');");
            LOGGER.info("✅ Loaded vector0 extension");
        }
    }

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

    public static void loadSqliteVssExtension(Connection conn, Path vssPath) throws SQLException, IOException {
        String path = vssPath.toAbsolutePath().toString()
                .replaceAll("\\.(dll|so|dylib)$", "")
                .replace("\\", "\\\\");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT load_extension('" + path + "', 'sqlite3_vss_init');");
            LOGGER.info("✅ Loaded sqlite-vss extension");
            ResultSet rs = stmt.executeQuery("SELECT vss_version();");
            if (rs.next()) LOGGER.info("✅ sqlite-vss version: {}", rs.getString(1));
        }
    }

    // =========================================================================
    // Fallback: cosine_distance UDF (Windows only)
    // =========================================================================

    public static void registerCosineDistanceIfNeeded(Connection conn) throws SQLException {
        // Registered on every platform: the cosine_distance UDF is implemented in
        // pure Java and does not require the native sqlite-vec / sqlite-vss extension.
        try {
            Function.create(conn, "cosine_distance", new Function() {
                @Override
                protected void xFunc() throws SQLException {
                    if (args() != 2) throw new SQLException("cosine_distance() requires exactly 2 arguments");

                    double[] v1 = parseVectorLiteral(value_text(0));
                    double[] v2 = parseVectorLiteral(value_text(1));

                    if (v1.length != v2.length) throw new SQLException("Vector dimensions do not match");

                    double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
                    for (int i = 0; i < v1.length; i++) {
                        dot   += v1[i] * v2[i];
                        norm1 += v1[i] * v1[i];
                        norm2 += v2[i] * v2[i];
                    }
                    result(1.0 - dot / (Math.sqrt(norm1) * Math.sqrt(norm2) + 1e-10));
                }

                private double[] parseVectorLiteral(String literal) {
                    String[] parts = literal.replaceAll("[\\[\\]]", "").split(",");
                    double[] vec = new double[parts.length];
                    for (int i = 0; i < parts.length; i++) vec[i] = Double.parseDouble(parts[i].trim());
                    return vec;
                }
            });
            LOGGER.info("✅ Registered fallback cosine_distance for Windows (TEXT VECTOR)");
        } catch (SQLException e) {
            LOGGER.error("❌ Failed to register cosine_distance UDF: {}", e.getMessage(), e);
            throw e;
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

    private static double[] deserializeVector(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int len = bytes.length / Double.BYTES;
        double[] vec = new double[len];
        for (int i = 0; i < len; i++) vec[i] = buffer.getDouble();
        return vec;
    }
}
