package ch.bzz.backend.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.TreeMap;

/**
 * Represents data for a single electricity meter (Stromzähler)
 * using a TreeMap to store Messwert objects as suggested by Roger Bünzli.
 */
@Data
public class StromzaehlerDaten {
    /**
     * The ID of the electricity meter
     */
    private String meterId;
    
    /**
     * TreeMap to store measurement values with timestamp as key
     * This ensures no duplicate timestamps can exist
     */
    private TreeMap<LocalDateTime, Messwert> messwerte;
    
    /**
     * Constructor to initialize the TreeMap
     */
    public StromzaehlerDaten(String meterId) {
        this.meterId = meterId;
        this.messwerte = new TreeMap<>();
    }
    
    /**
     * Adds a measurement to the TreeMap
     * If a measurement with the same timestamp already exists, it will be replaced
     * 
     * @param messwert The measurement to add
     */
    public void addMesswert(Messwert messwert) {
        messwerte.put(messwert.getTimestamp(), messwert);
    }
    
    /**
     * Gets a measurement by timestamp
     * 
     * @param timestamp The timestamp to look for
     * @return The measurement with the given timestamp, or null if not found
     */
    public Messwert getMesswert(LocalDateTime timestamp) {
        return messwerte.get(timestamp);
    }
    
    /**
     * Gets all measurements in the TreeMap
     * 
     * @return The TreeMap containing all measurements
     */
    public TreeMap<LocalDateTime, Messwert> getAllMesswerte() {
        return messwerte;
    }
}