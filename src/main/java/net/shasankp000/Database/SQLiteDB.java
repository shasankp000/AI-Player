package net.shasankp000.Database;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SQLiteDB {

    private static final Logger logger = LoggerFactory.getLogger("ai-player");

    private static final String DB_URL = "jdbc:sqlite:" +
            FabricLoader.getInstance().getGameDir().resolve("sqlite_databases/memory_agent.db").toAbsolutePath();

    public static void createDB() {
        File dbDir = FabricLoader.getInstance().getGameDir().resolve("sqlite_databases").toFile();
        if (!dbDir.exists() && dbDir.mkdirs()) {
            logger.info("✅ Database directory created: {}", dbDir);
        }

        // Configure SQLite to allow extensions
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);

        try (Connection conn = DriverManager.getConnection(DB_URL, config.toProperties())) {
            Path extPath = VectorExtensionHelper.ensureSqliteVecPresent();
            VectorExtensionHelper.loadSqliteVecExtension(conn, extPath);
            VectorExtensionHelper.registerCosineDistanceIfNeeded(conn);
            logger.info("✅ Using portable cosine_distance UDF for memory similarity");

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
                stmt.execute("PRAGMA journal_mode = WAL;");

                String createTable = """
                    CREATE TABLE IF NOT EXISTS memories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL,
                        timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
                        prompt TEXT,
                        response TEXT,
                        embedding VECTOR
                    );
                """;
                stmt.executeUpdate(createTable);
                logger.info("✅ Memory table created.");
            }
        } catch (SQLException e) {
            logger.error("❌ DB creation failed: SQLState={}, ErrorCode={}, Message={}",
                    e.getSQLState(), e.getErrorCode(), e.getMessage(), e);
            throw new RuntimeException(e);
        } catch (IOException e) {
            logger.error("❌ Extension setup failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public static void storeMemory(String type, String prompt, String response, List<Double> embedding) {
        String sql = """
            INSERT INTO memories (type, prompt, response, embedding)
            VALUES (?, ?, ?, ?);
        """;

        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);

        try (Connection conn = DriverManager.getConnection(DB_URL, config.toProperties());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, type);
            pstmt.setString(2, prompt);
            pstmt.setString(3, response);
            pstmt.setString(4, vectorToLiteral(embedding));

            pstmt.executeUpdate();
            logger.info("📝 Memory stored with vector embedding.");
        } catch (SQLException e) {
            logger.error("❌ Failed to store memory: SQLState={}, ErrorCode={}, Message={}",
                    e.getSQLState(), e.getErrorCode(), e.getMessage());
        }
    }

    public static List<Memory> findRelevantMemories(List<Double> queryEmbedding, String typeFilter, int topK) {
        logger.info("Query embedding size: {}", queryEmbedding.size());


        List<Memory> results = new ArrayList<>();
        String sql = """
            SELECT id, type, timestamp, prompt, response,
                   1 - cosine_distance(embedding, ?) AS similarity
            FROM memories
            WHERE type = ?
            ORDER BY similarity DESC
            LIMIT ?;
        """;

        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);

        try (Connection conn = DriverManager.getConnection(DB_URL, config.toProperties())) {

            // SQLite functions are connection-local, so register this for every search connection.
            VectorExtensionHelper.registerCosineDistanceIfNeeded(conn);

            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, vectorToLiteral(queryEmbedding));
                pstmt.setString(2, typeFilter);
                pstmt.setInt(3, topK);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    results.add(new Memory(
                            rs.getInt("id"),
                            rs.getString("type"),
                            rs.getString("timestamp"),
                            rs.getString("prompt"),
                            rs.getString("response"),
                            rs.getDouble("similarity")
                    ));
                }
            }

        } catch (SQLException e) {
            logger.error("❌ Vector search failed: SQLState={}, ErrorCode={}, Message={}",
                    e.getSQLState(), e.getErrorCode(), e.getMessage());
        }

        return results;
    }

    public static List<SQLiteDB.Memory> fetchInitialResponse() {
        List<SQLiteDB.Memory> results = new ArrayList<>();
        String sql = """
            SELECT id, type, timestamp, prompt, response, 0.0 AS similarity
            FROM memories
            WHERE type = 'conversation'
            ORDER BY id ASC
            LIMIT 1;
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                results.add(new SQLiteDB.Memory(
                        rs.getInt("id"),
                        rs.getString("type"),
                        rs.getString("timestamp"),
                        rs.getString("prompt"),
                        rs.getString("response"),
                        0.0
                ));
            }
        } catch (SQLException e) {
            logger.error("Caught exception while fetching initial response: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        return results;
    }

    private static String vectorToLiteral(List<Double> vec) {
        if (vec == null || vec.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.size(); i++) {
            sb.append(vec.get(i));
            if (i < vec.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    public record Memory(
            int id,
            String type,
            String timestamp,
            String prompt,
            String response,
            double similarity
    ) {}

}
