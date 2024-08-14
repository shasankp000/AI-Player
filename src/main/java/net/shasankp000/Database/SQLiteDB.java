package net.shasankp000.Database;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.util.List;
import java.util.stream.Collectors;

public class SQLiteDB {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteDB.class);
    public static boolean dbExists = false;
    public static boolean dbEmpty = false;

    public static void createDB() {

        String gameDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();

        File dbDir = new File(gameDir + "/sqlite_databases");
        if (!dbDir.exists()) {
            if(dbDir.mkdirs()) {
                System.out.println("Database directory created.");
            }
        }
        else{
            System.out.println("Database directory already exists, ignoring...");
        }

        String dbUrl = "jdbc:sqlite:" + gameDir + "/sqlite_databases/memory_agent.db";

        System.out.println("Connecting to database at: " + dbUrl);
        try (Connection connection = DriverManager.getConnection(dbUrl);
             Statement statement = connection.createStatement()) {

            if (connection.isValid(30)) {

                System.out.println("Connection to database valid.");

            }

            if (connection != null) {
                System.out.println("Connection to SQLite has been established.");
            }

            // Check if the table exists
            String checkTableQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name='conversations'";
            ResultSet tableResultSet = statement.executeQuery(checkTableQuery);

            if (!tableResultSet.next()) {
                // Table does not exist, create table
                String createTableQuery1 = "CREATE TABLE conversations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "prompt TEXT, " +
                        "response TEXT NOT NULL, " +
                        "prompt_embedding BLOB, " +
                        "response_embedding BLOB" +
                        ")";

                String createTableQuery2 = "CREATE TABLE events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "event TEXT NOT NULL, " +
                        "event_context TEXT NOT NULL, " +
                        "event_result TEXT NOT NULL, " +
                        "event_embedding BLOB, " +
                        "event_context_embedding BLOB, " +
                        "event_result_embedding BLOB" +
                        ")"; // Removed the extra comma here

                statement.executeUpdate(createTableQuery1);
                statement.executeUpdate(createTableQuery2);
                System.out.println("Setting up memory database...done.");

                dbExists = true;
                dbEmpty = true;
            }


        } catch (SQLException e) {
            logger.error("Caught SQLException: {}", (Object) e.getStackTrace());
        }
    }

    public static void storeConversationWithEmbedding(String DB_URL,String prompt, String response, List<Double> prompt_embedding, List<Double> response_embedding) throws SQLException {
        String promptEmbeddingString = prompt_embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String responseEmbeddingString = response_embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = connection.prepareStatement(
                     "INSERT INTO conversations (prompt, response, prompt_embedding, response_embedding) VALUES (?, ?, ?, ?)")) {
            pstmt.setString(1, prompt);
            pstmt.setString(2, response);
            pstmt.setString(3, promptEmbeddingString);
            pstmt.setString(4, responseEmbeddingString);
            pstmt.executeUpdate();
            System.out.println("SYSTEM: Conversation saved to database.");

            dbEmpty = false;
        }
    }


    public static void storeInitialResponseWithEmbedding(String DB_URL, String response) throws SQLException {


        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = connection.prepareStatement(
                     "INSERT INTO conversations (response) VALUES (?)")) {
            pstmt.setString(1, response);
            pstmt.executeUpdate();
            System.out.println("SYSTEM: Initial response saved to database.");

            dbEmpty = false;
        }
    }


    public static void storeEventWithEmbedding(String DB_URL,String event, String event_context, String event_result ,List<Double> event_embedding, List<Double> event_context_embedding, List<Double> event_result_embedding) throws SQLException {

        String eventEmbeddingString = event_embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String eventContextEmbeddingString = event_context_embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String eventResultEmbeddingString = event_result_embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        try (Connection connection = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = connection.prepareStatement(
                     "INSERT INTO events (event, event_context, event_result, event_embedding, event_context_embedding, event_result_embedding) VALUES (?, ?, ?, ?, ?, ?)")) {
            pstmt.setString(1, event);
            pstmt.setString(2, event_context);
            pstmt.setString(3, event_result);
            pstmt.setString(4, eventEmbeddingString);
            pstmt.setString(5, eventContextEmbeddingString);
            pstmt.setString(6, eventResultEmbeddingString);
            pstmt.executeUpdate();
            System.out.println("SYSTEM: Event data saved to database.");

            dbEmpty = false;
        }

    }

}
