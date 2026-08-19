package net.shasankp000.Database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static net.shasankp000.Database.VectorExtensionHelper.CpuArchitecture.ARM64;
import static net.shasankp000.Database.VectorExtensionHelper.CpuArchitecture.X86_64;
import static net.shasankp000.Database.VectorExtensionHelper.OperatingSystem.LINUX;
import static org.junit.jupiter.api.Assertions.*;

class VectorExtensionHelperTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void detectsSupportedPlatformsAndAliases() {
        assertEquals(new VectorExtensionHelper.Platform(LINUX, X86_64),
                VectorExtensionHelper.detectPlatform("Linux", "amd64"));
        assertEquals(new VectorExtensionHelper.Platform(LINUX, X86_64),
                VectorExtensionHelper.detectPlatform("GNU/Linux", "x86_64"));
        assertEquals(new VectorExtensionHelper.Platform(LINUX, ARM64),
                VectorExtensionHelper.detectPlatform("Linux", "aarch64"));
        assertEquals(new VectorExtensionHelper.Platform(
                        VectorExtensionHelper.OperatingSystem.MACOS, ARM64),
                VectorExtensionHelper.detectPlatform("Mac OS X", "arm64"));
        assertEquals(new VectorExtensionHelper.Platform(
                        VectorExtensionHelper.OperatingSystem.WINDOWS, X86_64),
                VectorExtensionHelper.detectPlatform("Windows Server 2025", "x64"));
    }

    @Test
    void rejectsUnsupportedAnd32BitPlatformsWithActionableMessages() {
        UnsupportedOperationException x86 = assertThrows(UnsupportedOperationException.class,
                () -> VectorExtensionHelper.detectPlatform("Linux", "i386"));
        assertTrue(x86.getMessage().contains("32-bit JVM"));

        UnsupportedOperationException unknownArchitecture = assertThrows(UnsupportedOperationException.class,
                () -> VectorExtensionHelper.detectPlatform("Linux", "riscv64"));
        assertTrue(unknownArchitecture.getMessage().contains("Unsupported CPU architecture"));

        UnsupportedOperationException unknownOs = assertThrows(UnsupportedOperationException.class,
                () -> VectorExtensionHelper.detectPlatform("Plan 9", "amd64"));
        assertTrue(unknownOs.getMessage().contains("Unsupported operating system"));
    }

    @Test
    void rejectsElfClass32() throws IOException {
        Path library = writeElf("vec0.so", 1, 62);

        VectorExtensionHelper.NativeLibraryValidation validation =
                VectorExtensionHelper.validateNativeLibrary(library,
                        new VectorExtensionHelper.Platform(LINUX, X86_64));

        assertFalse(validation.compatible());
        assertTrue(validation.reason().contains("ELFCLASS32"));
    }

    @Test
    void acceptsElf64ForMatchingX86Architecture() throws IOException {
        Path library = writeElf("vec0.so", 2, 62);

        assertTrue(VectorExtensionHelper.validateNativeLibrary(library,
                new VectorExtensionHelper.Platform(LINUX, X86_64)).compatible());
    }

    @Test
    void acceptsElf64ForMatchingArmArchitecture() throws IOException {
        Path library = writeElf("vec0.so", 2, 183);

        assertTrue(VectorExtensionHelper.validateNativeLibrary(library,
                new VectorExtensionHelper.Platform(LINUX, ARM64)).compatible());
    }

    @Test
    void rejectsElf64ForWrongCpuArchitecture() throws IOException {
        Path library = writeElf("vec0.so", 2, 183);

        VectorExtensionHelper.NativeLibraryValidation validation =
                VectorExtensionHelper.validateNativeLibrary(library,
                        new VectorExtensionHelper.Platform(LINUX, X86_64));

        assertFalse(validation.compatible());
        assertTrue(validation.reason().contains("ELF machine"));
    }

    @Test
    void reusesValidCacheAndRemovesIncompatibleCache() throws IOException {
        VectorExtensionHelper.Platform platform = new VectorExtensionHelper.Platform(LINUX, X86_64);

        Path validLibrary = writeElf("valid.so", 2, 62);
        byte[] originalBytes = Files.readAllBytes(validLibrary);

        assertTrue(VectorExtensionHelper.isReusableCachedLibrary(validLibrary, platform));
        assertArrayEquals(originalBytes, Files.readAllBytes(validLibrary));

        Path invalidLibrary = writeElf("invalid.so", 1, 3);

        assertFalse(VectorExtensionHelper.isReusableCachedLibrary(invalidLibrary, platform));
        assertFalse(Files.exists(invalidLibrary));

        assertFalse(VectorExtensionHelper.isReusableCachedLibrary(
                temporaryDirectory.resolve("missing.so"), platform));
    }

    @Test
    void portableCosineDistanceWorksOnEveryConnection() throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            VectorExtensionHelper.registerCosineDistanceIfNeeded(connection);

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT cosine_distance(?, ?), cosine_distance(?, ?)")) {
                statement.setString(1, "[1.0,0.0]");
                statement.setString(2, "[1.0,0.0]");
                statement.setString(3, "[1.0,0.0]");
                statement.setString(4, "[-1.0,0.0]");

                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(0.0, result.getDouble(1), 1.0e-9);
                    assertEquals(2.0, result.getDouble(2), 1.0e-9);
                }
            }
        }
    }

    private Path writeElf(String fileName, int elfClass, int machine) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        header.put((byte) 0x7f).put((byte) 'E').put((byte) 'L').put((byte) 'F');
        header.put((byte) elfClass);
        header.put((byte) 1); // little-endian
        header.position(16);
        header.putShort((short) 3); // shared object
        header.putShort((short) machine);

        Path library = temporaryDirectory.resolve(fileName);
        Files.write(library, header.array());
        return library;
    }
}
