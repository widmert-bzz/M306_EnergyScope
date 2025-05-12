package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.StromzaehlerDaten;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class XmlParserServiceTest {

    @Autowired
    private XmlParserService xmlParserService;

    @Test
    public void testParseValidatedMeteredDataXml() throws IOException {
        // Load the test XML file
        Resource resource = new ClassPathResource("20200214_093234_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU180263_-1809898866.xml");
        InputStream inputStream = resource.getInputStream();

        // Parse the XML file
        List<EnergyData> energyDataList = xmlParserService.parseXml(inputStream);

        // Verify the results
        assertNotNull(energyDataList);
        assertFalse(energyDataList.isEmpty());

        // Print some information for debugging
        System.out.println("[DEBUG_LOG] Parsed " + energyDataList.size() + " energy data records");
        for (EnergyData energyData : energyDataList) {
            System.out.println("[DEBUG_LOG] Meter ID: " + energyData.getMeterId());
            System.out.println("[DEBUG_LOG] Timestamp: " + energyData.getTimestamp());
            System.out.println("[DEBUG_LOG] Measurements: " + energyData.getMeasurements().size());
        }
    }

    @Test
    public void testParseXmlToStromzaehlerDaten() throws IOException {
        // Load the test XML file
        Resource resource = new ClassPathResource("20200214_093234_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU180263_-1809898866.xml");
        InputStream inputStream = resource.getInputStream();

        // Parse the XML file to StromzaehlerDaten
        Map<String, StromzaehlerDaten> stromzaehlerDatenMap = xmlParserService.parseXmlToStromzaehlerDaten(inputStream);

        // Verify the results
        assertNotNull(stromzaehlerDatenMap);
        assertFalse(stromzaehlerDatenMap.isEmpty());

        // Print some information for debugging
        System.out.println("[DEBUG_LOG] Parsed " + stromzaehlerDatenMap.size() + " StromzaehlerDaten records");
        for (Map.Entry<String, StromzaehlerDaten> entry : stromzaehlerDatenMap.entrySet()) {
            System.out.println("[DEBUG_LOG] Meter ID: " + entry.getKey());
            System.out.println("[DEBUG_LOG] Measurements: " + entry.getValue().getAllMesswerte().size());
        }
    }
}