package net.shasankp000.Database;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.Function;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class VectorExtensionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorExtensionHelper.class);

    // === SQLITE-VEC download URLs ===
    private static final String WINDOWS_VEC_URL        = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.9/sqlite-vec-0.1.9-loadable-windows-x86_64.tar.gz";
    private static final String LINUX_X86_VEC_URL      = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.9/sqlite-vec-0.1.9-loadable-linux-x86_64.tar.gz";
    private static final String LINUX_ARM64_VEC_URL    = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.9/sqlite-vec-0.1.9-loadable-linux-aarch64.tar.gz";
    private static final String MACOS_X86_VEC_URL      = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.9/sqlite-vec-0.1.9-loadable-macos-x86_64.tar.gz";
    private static final String MACOS_ARM64_VEC_URL    = "https://github.com/asg017/sqlite-vec/releases/download/v0.1.9/sqlite-vec-0.1.9-loadable-macos-aarch64.tar.gz";

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

    enum OperatingSystem {
        WINDOWS, LINUX, MACOS
    }

    enum CpuArchitecture {
        X86_64, ARM64
    }

    record Platform(OperatingSystem operatingSystem, CpuArchitecture architecture) {
        @Override
        public String toString() {
            return operatingSystem.name().toLowerCase(Locale.ENGLISH) + "/"
                    + architecture.name().toLowerCase(Locale.ENGLISH);
        }
    }

    record NativeLibraryValidation(boolean compatible, String reason) {
        static NativeLibraryValidation success() {
            return new NativeLibraryValidation(true, "compatible");
        }

        static NativeLibraryValidation failure(String reason) {
            return new NativeLibraryValidation(false, reason);
        }
    }

    private record ExtensionAsset(String downloadUrl, String fileName, String dependencyFileName) {
    }

    static Platform detectPlatform(String osNameValue, String archValue) {
        String osName = osNameValue == null ? "" : osNameValue.toLowerCase(Locale.ENGLISH).trim();
        String arch = archValue == null ? "" : archValue.toLowerCase(Locale.ENGLISH).trim();

        OperatingSystem operatingSystem;

        if (osName.contains("win")) {
            operatingSystem = OperatingSystem.WINDOWS;
        } else if (osName.contains("linux") || osName.contains("nux") || osName.contains("nix")) {
            operatingSystem = OperatingSystem.LINUX;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            operatingSystem = OperatingSystem.MACOS;
        } else {
            throw new UnsupportedOperationException("Unsupported operating system for SQLite extensions: '"
                    + osNameValue + "'");
        }

        CpuArchitecture architecture;
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x86-64") || arch.equals("x64")) {
            architecture = CpuArchitecture.X86_64;
        } else if (arch.equals("aarch64") || arch.equals("arm64") || arch.startsWith("armv8")) {
            architecture = CpuArchitecture.ARM64;
        } else if (arch.matches("(?:x86|i[3-6]86|x86_32|arm|arm32|armv7.*)")) {
            throw new UnsupportedOperationException("32-bit JVM architecture '" + archValue
                    + "' is not supported; sqlite-vec requires a 64-bit JVM and native library");
        } else {
            throw new UnsupportedOperationException("Unsupported CPU architecture for SQLite extensions: '"
                    + archValue + "'");
        }

        return new Platform(operatingSystem, architecture);
    }

    private static Platform currentPlatform() {
        return detectPlatform(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    private static ExtensionAsset sqliteVecAsset(Platform platform) {
        return switch (platform.operatingSystem()) {
            case WINDOWS -> {
                if (platform.architecture() != CpuArchitecture.X86_64) {
                    throw new UnsupportedOperationException("sqlite-vec does not publish a Windows ARM64 loadable extension");
                }
                yield new ExtensionAsset(WINDOWS_VEC_URL, VECTOR_FILENAME_WINDOWS, null);
            }
            case LINUX -> new ExtensionAsset(
                    platform.architecture() == CpuArchitecture.ARM64 ? LINUX_ARM64_VEC_URL : LINUX_X86_VEC_URL,
                    VECTOR_FILENAME_LINUX,
                    null);
            case MACOS -> new ExtensionAsset(
                    platform.architecture() == CpuArchitecture.ARM64 ? MACOS_ARM64_VEC_URL : MACOS_X86_VEC_URL,
                    VECTOR_FILENAME_MACOS,
                    null);
        };
    }

    private static ExtensionAsset sqliteVssAsset(Platform platform) {
        return switch (platform.operatingSystem()) {
            case LINUX -> new ExtensionAsset(
                    platform.architecture() == CpuArchitecture.ARM64 ? VSS_LINUX_ARM64_URL : VSS_LINUX_X86_URL,
                    VSS_FILENAME_LINUX,
                    "vector0.so");
            case MACOS -> new ExtensionAsset(
                    platform.architecture() == CpuArchitecture.ARM64 ? VSS_MACOS_ARM64_URL : VSS_MACOS_X86_URL,
                    VSS_FILENAME_MACOS,
                    "vector0.dylib");
            case WINDOWS -> throw new UnsupportedOperationException("sqlite-vss is not supported on Windows");
        };
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

    static NativeLibraryValidation validateNativeLibrary(Path library, Platform platform) throws IOException {
        byte[] header = readPrefix(library, 64 * 1024);
        if (header.length < 4) {
            return NativeLibraryValidation.failure("file is too small to contain a native-library header");
        }

        return switch (platform.operatingSystem()) {
            case LINUX -> validateElf(header, platform.architecture());
            case MACOS -> validateMachO(header, platform.architecture());
            case WINDOWS -> validatePortableExecutable(header, platform.architecture());
        };
    }

    static boolean isReusableCachedLibrary(Path library, Platform platform) throws IOException {
        if (!Files.isRegularFile(library)) {
            return false;
        }

        NativeLibraryValidation validation = validateNativeLibrary(library, platform);
        if (validation.compatible()) {
            LOGGER.info("✅ Reusing compatible native extension: {} ({})", library, platform);
            return true;
        }

        LOGGER.warn("⚠️ Removing incompatible native extension {}: {} (expected {})",
                library, validation.reason(), platform);
        Files.delete(library);
        return false;
    }

    private static byte[] readPrefix(Path file, int maximumBytes) throws IOException {
        long size = Files.size(file);
        int bytesToRead = (int) Math.min(size, maximumBytes);
        ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Keep reading until the prefix is full or EOF is reached.
            }
        }
        byte[] result = new byte[buffer.position()];
        buffer.flip();
        buffer.get(result);
        return result;
    }

    private static NativeLibraryValidation validateElf(byte[] header, CpuArchitecture architecture) {
        if (header.length < 20 || header[0] != 0x7f || header[1] != 'E'
                || header[2] != 'L' || header[3] != 'F') {
            return NativeLibraryValidation.failure("not an ELF library");
        }

        int elfClass = Byte.toUnsignedInt(header[4]);
        if (elfClass != 2) {
            return NativeLibraryValidation.failure(elfClass == 1
                    ? "32-bit ELF (ELFCLASS32)"
                    : "unknown ELF class " + elfClass);
        }

        ByteOrder byteOrder = switch (Byte.toUnsignedInt(header[5])) {
            case 1 -> ByteOrder.LITTLE_ENDIAN;
            case 2 -> ByteOrder.BIG_ENDIAN;
            default -> null;
        };
        if (byteOrder == null) {
            return NativeLibraryValidation.failure("unknown ELF byte order " + Byte.toUnsignedInt(header[5]));
        }

        int machine = Short.toUnsignedInt(ByteBuffer.wrap(header, 18, 2).order(byteOrder).getShort());
        int expectedMachine = architecture == CpuArchitecture.X86_64 ? 62 : 183;
        if (machine != expectedMachine) {
            return NativeLibraryValidation.failure("ELF machine " + machine
                    + " does not match " + architecture.name().toLowerCase(Locale.ENGLISH));
        }
        return NativeLibraryValidation.success();
    }

    private static NativeLibraryValidation validateMachO(byte[] header, CpuArchitecture architecture) {
        int magic = ByteBuffer.wrap(header, 0, 4).order(ByteOrder.BIG_ENDIAN).getInt();
        int expectedCpu = architecture == CpuArchitecture.X86_64 ? 0x01000007 : 0x0100000c;

        if (magic == 0xfeedfacf || magic == 0xcffaedfe) {
            if (header.length < 8) {
                return NativeLibraryValidation.failure("truncated 64-bit Mach-O header");
            }
            ByteOrder order = magic == 0xfeedfacf ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
            int cpuType = ByteBuffer.wrap(header, 4, 4).order(order).getInt();
            return cpuType == expectedCpu
                    ? NativeLibraryValidation.success()
                    : NativeLibraryValidation.failure("Mach-O CPU type 0x"
                    + Integer.toHexString(cpuType) + " does not match "
                    + architecture.name().toLowerCase(Locale.ENGLISH));
        }

        if (magic == 0xfeedface || magic == 0xcefaedfe) {
            return NativeLibraryValidation.failure("32-bit Mach-O library");
        }

        boolean fat64;
        ByteOrder order;
        if (magic == 0xcafebabe || magic == 0xcafebabf) {
            fat64 = magic == 0xcafebabf;
            order = ByteOrder.BIG_ENDIAN;
        } else if (magic == 0xbebafeca || magic == 0xbfbafeca) {
            fat64 = magic == 0xbfbafeca;
            order = ByteOrder.LITTLE_ENDIAN;
        } else {
            return NativeLibraryValidation.failure("not a Mach-O library");
        }

        if (header.length < 8) {
            return NativeLibraryValidation.failure("truncated universal Mach-O header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(header).order(order);
        long architectureCount = Integer.toUnsignedLong(buffer.getInt(4));
        int entrySize = fat64 ? 32 : 20;
        long requiredBytes = 8L + architectureCount * entrySize;
        if (architectureCount == 0 || requiredBytes > header.length) {
            return NativeLibraryValidation.failure("truncated or invalid universal Mach-O architecture table");
        }
        for (int index = 0; index < architectureCount; index++) {
            if (buffer.getInt(8 + index * entrySize) == expectedCpu) {
                return NativeLibraryValidation.success();
            }
        }
        return NativeLibraryValidation.failure("universal Mach-O does not contain "
                + architecture.name().toLowerCase(Locale.ENGLISH));
    }

    private static NativeLibraryValidation validatePortableExecutable(byte[] header, CpuArchitecture architecture) {
        if (header.length < 64 || header[0] != 'M' || header[1] != 'Z') {
            return NativeLibraryValidation.failure("not a Windows PE library");
        }

        int peOffset = ByteBuffer.wrap(header, 0x3c, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (peOffset < 0 || peOffset + 26 > header.length
                || header[peOffset] != 'P' || header[peOffset + 1] != 'E'
                || header[peOffset + 2] != 0 || header[peOffset + 3] != 0) {
            return NativeLibraryValidation.failure("truncated or invalid Windows PE header");
        }

        int machine = Short.toUnsignedInt(ByteBuffer.wrap(header, peOffset + 4, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort());
        int optionalHeaderMagic = Short.toUnsignedInt(ByteBuffer.wrap(header, peOffset + 24, 2)
                .order(ByteOrder.LITTLE_ENDIAN).getShort());
        int expectedMachine = architecture == CpuArchitecture.X86_64 ? 0x8664 : 0xaa64;
        if (optionalHeaderMagic != 0x20b) {
            return NativeLibraryValidation.failure("32-bit or unknown Windows PE optional header");
        }

        if (machine != expectedMachine) {
            return NativeLibraryValidation.failure("Windows PE machine 0x" + Integer.toHexString(machine)
                    + " does not match " + architecture.name().toLowerCase(Locale.ENGLISH));
        }
        return NativeLibraryValidation.success();
    }

    // =========================================================================
    // SQLITE-VEC
    // =========================================================================

    public static Path ensureSqliteVecPresent() throws IOException {
        Platform platform = currentPlatform();
        ExtensionAsset asset = sqliteVecAsset(platform);
        LOGGER.info("💻 Detected SQLite extension platform: {} (os.name='{}', os.arch='{}')",
                platform, System.getProperty("os.name"), System.getProperty("os.arch"));

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path vecDir    = configDir.resolve("sqlite_vector/sqlite-vec");
        Files.createDirectories(vecDir);

        Path outputPath = vecDir.resolve(asset.fileName());

        cleanupOldVecFiles(vecDir, asset.fileName());

        if (isReusableCachedLibrary(outputPath, platform)) {
            return outputPath;
        }

        downloadAndInstall(asset.downloadUrl(), vecDir, "sqlite-vec", platform,
                Map.of(asset.fileName(), outputPath));

        LOGGER.info("✅ sqlite-vec ready at: {}", outputPath);
        return outputPath;
    }

    // =========================================================================
    // SQLITE-VSS
    // =========================================================================

    public static Path ensureSqliteVssPresent() throws IOException {
        Platform platform = currentPlatform();
        ExtensionAsset asset = sqliteVssAsset(platform);
        LOGGER.info("💻 Detected SQLite extension platform: {} (os.name='{}', os.arch='{}')",
                platform, System.getProperty("os.name"), System.getProperty("os.arch"));

        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path vssDir    = configDir.resolve("sqlite_vector/sqlite-vss");
        Files.createDirectories(vssDir);

        Path outputPath = vssDir.resolve(asset.fileName());
        Path vector0OutputPath = vssDir.resolve(asset.dependencyFileName());

        boolean vssReusable = isReusableCachedLibrary(outputPath, platform);
        boolean vectorReusable = isReusableCachedLibrary(vector0OutputPath, platform);
        if (vssReusable && vectorReusable) {
            return outputPath;
        }

        Map<String, Path> files = new LinkedHashMap<>();
        files.put(asset.fileName(), outputPath);
        files.put(asset.dependencyFileName(), vector0OutputPath);
        downloadAndInstall(asset.downloadUrl(), vssDir, "sqlite-vss", platform, files);

        LOGGER.info("✅ sqlite-vss ready at: {}", outputPath);
        return outputPath;
    }

    // =========================================================================
    // Extension loaders
    // =========================================================================

    public static void loadSqliteVector0Extension(Connection conn, Path vssDir) throws SQLException {
        String vector0FileName = sqliteVssAsset(currentPlatform()).dependencyFileName();
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
    // Portable cosine_distance UDF
    // =========================================================================

    public static void registerCosineDistanceIfNeeded(Connection conn) throws SQLException {
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
            LOGGER.info("✅ Registered portable cosine_distance UDF (TEXT VECTOR)");
        } catch (SQLException e) {
            LOGGER.error("❌ Failed to register cosine_distance UDF: {}", e.getMessage(), e);
            throw e;
        }
    }

    private static void downloadAndInstall(String downloadUrl,
                                           Path destinationDirectory,
                                           String archivePrefix,
                                           Platform platform,
                                           Map<String, Path> archiveEntries) throws IOException {
        LOGGER.info("⬇️ Downloading native SQLite extensions for {} from {}", platform, downloadUrl);

        Path compressedArchive = Files.createTempFile(destinationDirectory, archivePrefix + "-", ".tar.gz.part");
        Path tarArchive = Files.createTempFile(destinationDirectory, archivePrefix + "-", ".tar.part");
        Map<String, Path> extractedFiles = new LinkedHashMap<>();

        try {
            try (InputStream in = URI.create(downloadUrl).toURL().openStream()) {
                Files.copy(in, compressedArchive, StandardCopyOption.REPLACE_EXISTING);
            }
            try (GZIPInputStream gzipIn = new GZIPInputStream(Files.newInputStream(compressedArchive));
                 OutputStream out = Files.newOutputStream(tarArchive, StandardOpenOption.TRUNCATE_EXISTING)) {
                gzipIn.transferTo(out);
            }

            for (Map.Entry<String, Path> entry : archiveEntries.entrySet()) {
                Path temporaryLibrary = Files.createTempFile(destinationDirectory,
                        entry.getKey().replace('.', '-') + "-", ".part");
                extractedFiles.put(entry.getKey(), temporaryLibrary);
                try (InputStream tarIn = Files.newInputStream(tarArchive)) {
                    if (!safeExtractTar(tarIn, entry.getKey(), temporaryLibrary)) {
                        throw new IOException("Native extension '" + entry.getKey()
                                + "' was not found in downloaded archive " + downloadUrl);
                    }
                }

                NativeLibraryValidation validation = validateNativeLibrary(temporaryLibrary, platform);
                if (!validation.compatible()) {
                    throw new IOException("Downloaded native extension '" + entry.getKey()
                            + "' is incompatible with " + platform + ": " + validation.reason());
                }
            }

            for (Map.Entry<String, Path> entry : archiveEntries.entrySet()) {
                replaceAtomically(extractedFiles.get(entry.getKey()), entry.getValue());
                LOGGER.info("✅ Installed compatible native extension: {}", entry.getValue());
            }
        } finally {
            for (Path temporaryLibrary : extractedFiles.values()) {
                Files.deleteIfExists(temporaryLibrary);
            }
            Files.deleteIfExists(tarArchive);
            Files.deleteIfExists(compressedArchive);
        }
    }

    private static void replaceAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
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
