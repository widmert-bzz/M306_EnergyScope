package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.StromzaehlerDaten;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for storing data locally in JSON files
 */
@Service
@Slf4j
public class LocalStorageService {

    private static final String STORAGE_DIR = "data";
    private static final String ENERGY_DATA_DIR = STORAGE_DIR + "/energy-data";
    private static final String STROMZAEHLER_DIR = STORAGE_DIR + "/stromzaehler";
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /**
     * Initialize storage directories
     */
    public LocalStorageService() {
        createDirectories();
    }

    /**
     * Save energy data to local storage
     * @param energyDataList List of energy data to save
     * @return The saved energy data list
     */
    public List<EnergyData> saveEnergyData(List<EnergyData> energyDataList) {
        createDirectories();
        
        List<EnergyData> savedList = new ArrayList<>();
        for (EnergyData energyData : energyDataList) {
            try {
                // Generate a unique filename based on timestamp and meter ID
                String filename = generateFilename(energyData.getMeterId(), energyData.getTimestamp());
                Path filePath = Paths.get(ENERGY_DATA_DIR, filename + ".json");
                
                // Write the energy data to a JSON file
                objectMapper.writeValue(filePath.toFile(), energyData);
                
                savedList.add(energyData);
                log.info("Saved energy data to {}", filePath);
            } catch (IOException e) {
                log.error("Error saving energy data", e);
            }
        }
        
        return savedList;
    }

    /**
     * Save StromzaehlerDaten to local storage
     * @param stromzaehlerDatenMap Map of meter IDs to StromzaehlerDaten
     * @return The saved StromzaehlerDaten map
     */
    public Map<String, StromzaehlerDaten> saveStromzaehlerDaten(Map<String, StromzaehlerDaten> stromzaehlerDatenMap) {
        createDirectories();
        
        Map<String, StromzaehlerDaten> savedMap = new HashMap<>();
        for (Map.Entry<String, StromzaehlerDaten> entry : stromzaehlerDatenMap.entrySet()) {
            try {
                String meterId = entry.getKey();
                StromzaehlerDaten stromzaehlerDaten = entry.getValue();
                
                // Generate a unique filename based on meter ID and current timestamp
                String filename = meterId + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                Path filePath = Paths.get(STROMZAEHLER_DIR, filename + ".json");
                
                // Write the StromzaehlerDaten to a JSON file
                objectMapper.writeValue(filePath.toFile(), stromzaehlerDaten);
                
                savedMap.put(meterId, stromzaehlerDaten);
                log.info("Saved StromzaehlerDaten to {}", filePath);
            } catch (IOException e) {
                log.error("Error saving StromzaehlerDaten", e);
            }
        }
        
        return savedMap;
    }

    /**
     * Get all energy data from local storage
     * @return List of all energy data
     */
    public List<EnergyData> getAllEnergyData() {
        List<EnergyData> energyDataList = new ArrayList<>();
        
        try {
            File dir = new File(ENERGY_DATA_DIR);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
                if (files != null) {
                    for (File file : files) {
                        try {
                            EnergyData energyData = objectMapper.readValue(file, EnergyData.class);
                            energyDataList.add(energyData);
                        } catch (IOException e) {
                            log.error("Error reading energy data from {}", file.getPath(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting all energy data", e);
        }
        
        return energyDataList;
    }

    /**
     * Get energy data by meter ID from local storage
     * @param meterId The meter ID to search for
     * @return List of energy data for the specified meter
     */
    public List<EnergyData> getEnergyDataByMeterId(String meterId) {
        List<EnergyData> energyDataList = new ArrayList<>();
        
        try {
            File dir = new File(ENERGY_DATA_DIR);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".json") && name.contains(meterId));
                if (files != null) {
                    for (File file : files) {
                        try {
                            EnergyData energyData = objectMapper.readValue(file, EnergyData.class);
                            if (energyData.getMeterId().equals(meterId)) {
                                energyDataList.add(energyData);
                            }
                        } catch (IOException e) {
                            log.error("Error reading energy data from {}", file.getPath(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error getting energy data by meter ID", e);
        }
        
        return energyDataList;
    }

    /**
     * Create storage directories if they don't exist
     */
    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(ENERGY_DATA_DIR));
            Files.createDirectories(Paths.get(STROMZAEHLER_DIR));
        } catch (IOException e) {
            log.error("Error creating storage directories", e);
        }
    }

    /**
     * Generate a unique filename based on meter ID and timestamp
     * @param meterId The meter ID
     * @param timestamp The timestamp
     * @return A unique filename
     */
    private String generateFilename(String meterId, LocalDateTime timestamp) {
        return meterId + "_" + timestamp.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }
}