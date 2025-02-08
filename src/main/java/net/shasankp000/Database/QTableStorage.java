package net.shasankp000.Database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;
import net.shasankp000.GameAI.State;
import net.shasankp000.GameAI.StateActions;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import java.util.Map;
import java.util.Objects;

public class QTableStorage {
    private static final String gameDir = MinecraftClient.getInstance().runDirectory.getAbsolutePath();


    public static void setupQTableStorage() {

        File tableDir = new File(gameDir + "/qtable_storage");

        if (!tableDir.exists()) {
            if(tableDir.mkdirs()) {
                System.out.println("QTable Storage directory created.");
            }
        }
        else{
            System.out.println("QTable Storage directory already exists, ignoring...");
        }

    }



    /**
     * Saves the global epsilon value to a file.
     */
    public static void saveEpsilon(double epsilon, String filePath) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(filePath))) {
            dos.writeDouble(epsilon);
        }
    }


    /**
     *  Saves the last known state of the rlAgent during training to a file, to be used across training sessions and bot respawns.
     */
    public static void saveLastKnownState(State lastKnownState, String filePath) {
        if (lastKnownState == null) {
            System.out.println("No lastKnownState to save.");
            return;
        }

        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(lastKnownState);
            System.out.println("lastKnownState saved to " + filePath);
        } catch (IOException e) {
            System.err.println("Failed to save lastKnownState: " + e.getMessage());
            System.out.println(e.getMessage());
        }
    }

    /**
     * Loads the last known state of the rlAgent from a file, to be used across training sessions and bot respawns
     */
    public static State loadLastKnownState(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("No saved lastKnownState found.");
            return null;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            State lastKnownState = (State) ois.readObject();
            System.out.println("lastKnownState loaded from " + filePath);
            return lastKnownState;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load lastKnownState: " + e.getMessage());
            System.out.println(e.getMessage());
            return null;
        }
    }



    /**
     * Loads the global epsilon value from a file.
     */
    public static double loadEpsilon(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            return 1.0; // Default epsilon
        }

        try (DataInputStream dis = new DataInputStream(new FileInputStream(filePath))) {
            return dis.readDouble();
        }
    }


    /**
     * Saves the QTable object to the specified file path.
     *
     * @param qTable   The QTable object to save.
     * @param filePath The file path where the QTable should be saved.
     */
    public static void saveQTable(QTable qTable, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {

            oos.writeObject(qTable);
            System.out.println("QTable successfully saved to " + filePath);

        } catch (IOException e) {
            System.err.println("Error saving QTable: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load the QTable from a binary file.
     *
     * @param filePath The path to the file where the QTable is stored.
     * @return The loaded QTable object.
     * @throws IOException If an I/O error occurs.
     * @throws ClassNotFoundException If the class for the serialized object cannot be found.
     */
    public static QTable load(String filePath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            QTable loadedQTable = (QTable) ois.readObject();
            System.out.println("QTable loaded successfully from " + filePath);
            return loadedQTable;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error loading QTable: " + e.getMessage());
            throw e;
        }
    }

}


