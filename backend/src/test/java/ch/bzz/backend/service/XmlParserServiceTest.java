package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Messwert;
import ch.bzz.backend.model.StromzaehlerDaten;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class XmlParserServiceTest {

    @Autowired
    private XmlParserService xmlParserService;

    @Test
    public void testProcessMultipleFiles() throws IOException {
        // Prepare test files
        List<InputStream> inputStreams = new ArrayList<>();

        // Add one ESL file
        ClassPathResource eslResource = new ClassPathResource("ESL_Files/EdmRegisterWertExport_20190314_eslevu_20190314090341.xml");
        inputStreams.add(eslResource.getInputStream());

        // Add one SDAT file
        ClassPathResource sdatResource = new ClassPathResource("SDAT_Files/20190426_093054_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU130304_-941874069.xml");
        inputStreams.add(sdatResource.getInputStream());

        // Process files
        Map<String, StromzaehlerDaten> result = xmlParserService.processMultipleFiles(inputStreams);

        // Verify results
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify that relative values are calculated
        for (StromzaehlerDaten stromzaehlerDaten : result.values()) {
            TreeMap<LocalDateTime, Messwert> messwerte = stromzaehlerDaten.getAllMesswerte();

            // Skip if there are less than 2 measurements
            if (messwerte.size() < 2) {
                continue;
            }

            // Check that at least one measurement has a non-zero relative value
            boolean hasNonZeroRelativeValue = false;
            for (Messwert messwert : messwerte.values()) {
                if (messwert.getRelativeValue() != 0.0) {
                    hasNonZeroRelativeValue = true;
                    break;
                }
            }

            assertTrue(hasNonZeroRelativeValue, "At least one measurement should have a non-zero relative value");

            // Print some debug information
            System.out.println("[DEBUG_LOG] Meter ID: " + stromzaehlerDaten.getMeterId());
            System.out.println("[DEBUG_LOG] Number of measurements: " + messwerte.size());

            // Print the first few measurements
            int count = 0;
            for (Map.Entry<LocalDateTime, Messwert> entry : messwerte.entrySet()) {
                Messwert messwert = entry.getValue();
                System.out.println("[DEBUG_LOG] Timestamp: " + messwert.getTimestamp() + 
                                   ", Absolute: " + messwert.getAbsoluteValue() + 
                                   ", Relative: " + messwert.getRelativeValue() + 
                                   ", Type: " + messwert.getType());

                count++;
                if (count >= 5) {
                    break;
                }
            }
        }
    }

    @Test
    public void testProcessMultipleFilesWithSameMeterId() throws IOException {
        // Prepare test files
        List<InputStream> inputStreams = new ArrayList<>();

        // Add multiple files that should have the same meter ID
        ClassPathResource eslResource1 = new ClassPathResource("ESL_Files/EdmRegisterWertExport_20190314_eslevu_20190314090341.xml");
        ClassPathResource eslResource2 = new ClassPathResource("ESL_Files/EdmRegisterWertExport_20190403_eslevu_20190403050446.xml");

        inputStreams.add(eslResource1.getInputStream());
        inputStreams.add(eslResource2.getInputStream());

        // Process files
        Map<String, StromzaehlerDaten> result = xmlParserService.processMultipleFiles(inputStreams);

        // Verify results
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Verify that measurements from both files are combined
        for (StromzaehlerDaten stromzaehlerDaten : result.values()) {
            TreeMap<LocalDateTime, Messwert> messwerte = stromzaehlerDaten.getAllMesswerte();

            System.out.println("[DEBUG_LOG] Meter ID: " + stromzaehlerDaten.getMeterId());
            System.out.println("[DEBUG_LOG] Number of measurements: " + messwerte.size());

            // We should have measurements from both files
            assertTrue(messwerte.size() > 1, "Should have measurements from both files");
        }
    }

    @Test
    public void testDocumentIdToObisCodeMapping() throws IOException {
        // Prepare test files
        List<InputStream> inputStreams = new ArrayList<>();

        // Add one ESL file with obis codes
        ClassPathResource eslResource = new ClassPathResource("ESL_Files/EdmRegisterWertExport_20190314_eslevu_20190314090341.xml");
        inputStreams.add(eslResource.getInputStream());

        // Add one SDAT file with DocumentID
        ClassPathResource sdatResource = new ClassPathResource("SDAT_Files/20190915_093227_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU154756_1291601611.xml");
        inputStreams.add(sdatResource.getInputStream());

        // Process files
        Map<String, StromzaehlerDaten> result = xmlParserService.processMultipleFiles(inputStreams);

        // Verify results
        assertNotNull(result);
        assertFalse(result.isEmpty());

        // Check if ID735 (Einspeisung) and ID742 (Bezug) are in the result
        boolean hasID735 = false;
        boolean hasID742 = false;

        for (String meterId : result.keySet()) {
            System.out.println("[DEBUG_LOG] Found meter ID: " + meterId);
            if (meterId.equals("ID735")) {
                hasID735 = true;
            } else if (meterId.equals("ID742")) {
                hasID742 = true;
            }
        }

        // At least one of the IDs should be present, depending on the test files
        assertTrue(hasID735 || hasID742, "Either ID735 or ID742 should be present in the result");

        // If ID735 is present, verify it has production data
        if (hasID735) {
            StromzaehlerDaten stromzaehlerDaten = result.get("ID735");
            boolean hasProductionData = false;

            for (Messwert messwert : stromzaehlerDaten.getAllMesswerte().values()) {
                if (messwert.getType() == EnergyData.DataType.PRODUCTION) {
                    hasProductionData = true;
                    System.out.println("[DEBUG_LOG] ID735 has production data: " + messwert.getAbsoluteValue());
                    break;
                }
            }

            assertTrue(hasProductionData, "ID735 should have production data");
        }

        // If ID742 is present, verify it has consumption data
        if (hasID742) {
            StromzaehlerDaten stromzaehlerDaten = result.get("ID742");
            boolean hasConsumptionData = false;

            for (Messwert messwert : stromzaehlerDaten.getAllMesswerte().values()) {
                if (messwert.getType() == EnergyData.DataType.CONSUMPTION) {
                    hasConsumptionData = true;
                    System.out.println("[DEBUG_LOG] ID742 has consumption data: " + messwert.getAbsoluteValue());
                    break;
                }
            }

            assertTrue(hasConsumptionData, "ID742 should have consumption data");
        }
    }
}
