package net.shasankp000.Database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.shasankp000.GameAI.State;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class QTableExporter {

    public static void exportQTable(String qTableFilePath, String exportFilePath) {
        try {
            // Load the QTable from the binary file
            QTable qTable = QTableStorage.load(qTableFilePath);
            System.out.println("Loaded QTable from: " + qTableFilePath);

            // Prepare the export data structure
            Map<String, Object> exportData = new HashMap<>();

            for (Map.Entry<StateActionPair, QEntry> entry : qTable.getTable().entrySet()) {
                StateActionPair pair = entry.getKey();
                QEntry qEntry = entry.getValue();

                // Serialize state-action pair and corresponding QEntry
                String currentState = serializeState(pair.getState());
                Map<String, Object> stateDetails = new HashMap<>();
                stateDetails.put("stateDetails", serializeStateObject(pair.getState()));
                stateDetails.put("NextState", serializeStateObject(qEntry.getNextState()));

                exportData.put(currentState, stateDetails);
            }

            // Convert the export data to JSON
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            String jsonOutput = gson.toJson(exportData);

            // Write JSON to the export file
            Files.write(Paths.get(exportFilePath), jsonOutput.getBytes(StandardCharsets.UTF_8));
            System.out.println("QTable exported successfully to: " + exportFilePath);

        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error exporting QTable: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to serialize a state to a string format
    private static String serializeState(State state) {
        return String.format("X:%d, Y:%d, Z:%d, Health:%d, Hunger:%d, Oxygen:%d, Frost:%d, Time:%s, Dimension:%s, SelectedItem:%s",
                state.getBotX(),
                state.getBotY(),
                state.getBotZ(),
                state.getBotHealth(),
                state.getBotHungerLevel(),
                state.getBotOxygenLevel(),
                state.getFrostLevel(),
                state.getTimeOfDay(),
                state.getDimensionType(),
                state.getSelectedItem());
    }

    // Helper method to convert a State object into a map for detailed JSON export
    private static Map<String, Object> serializeStateObject(State state) {
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("coordinates", Map.of("X", state.getBotX(), "Y", state.getBotY(), "Z", state.getBotZ()));
        stateMap.put("Health", state.getBotHealth());
        stateMap.put("Hunger", state.getBotHungerLevel());
        stateMap.put("Oxygen", state.getBotOxygenLevel());
        stateMap.put("Frost", state.getFrostLevel());
        stateMap.put("Time", state.getTimeOfDay());
        stateMap.put("Dimension", state.getDimensionType());
        stateMap.put("SelectedItem", state.getSelectedItem());
        stateMap.put("NearbyEntities", state.getNearbyEntities());
        stateMap.put("RiskAppetite", state.getRiskAppetite());
        stateMap.put("PodMap", state.getPodMap());
        return stateMap;
    }
}
