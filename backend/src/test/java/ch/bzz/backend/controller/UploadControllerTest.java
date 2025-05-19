package ch.bzz.backend.controller;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.service.LocalStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@AutoConfigureMockMvc
public class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalStorageService localStorageService;

    @Test
    public void testUploadFile() throws Exception {
        // Load the test XML file from the main resources directory
        Resource resource = new ClassPathResource("data/20200214_093234_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU180263_-1809898866.xml");
        byte[] content = Files.readAllBytes(resource.getFile().toPath());

        System.out.println("[DEBUG_LOG] Loaded test file: " + resource.getFilename());

        // Create a mock multipart file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "20200214_093234_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU180263_-1809898866.xml",
                MediaType.APPLICATION_XML_VALUE,
                content
        );

        // Perform the upload
        mockMvc.perform(MockMvcRequestBuilders.multipart("/upload")
                        .file(file))
                .andExpect(MockMvcResultMatchers.status().isOk());

        // Verify that the data was saved to local storage
        List<EnergyData> energyDataList = localStorageService.getAllEnergyData();
        assertNotNull(energyDataList);
        assertFalse(energyDataList.isEmpty());

        // Print some information for debugging
        System.out.println("[DEBUG_LOG] Saved " + energyDataList.size() + " energy data records to local storage");
        for (EnergyData energyData : energyDataList) {
            System.out.println("[DEBUG_LOG] ID: " + energyData.getId());
            System.out.println("[DEBUG_LOG] Meter ID: " + energyData.getMeterId());
            System.out.println("[DEBUG_LOG] Timestamp: " + energyData.getTimestamp());
            System.out.println("[DEBUG_LOG] Measurements: " + energyData.getMeasurements().size());
        }
    }

    @Test
    public void testUploadFileToStromzaehlerDaten() throws Exception {
        // Load the test XML file from the main resources directory
        Resource resource = new ClassPathResource("data/20200214_093234_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU180263_-1809898866.xml");
        byte[] content = Files.readAllBytes(resource.getFile().toPath());

        System.out.println("[DEBUG_LOG] Loaded test file for StromzaehlerDaten: " + resource.getFilename());

        // Create a mock multipart file
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "20200214_093234_12X-0000001216-O_E66_12X-LIPPUNEREM-T_ESLEVU180263_-1809898866.xml",
                MediaType.APPLICATION_XML_VALUE,
                content
        );

        // Perform the upload
        mockMvc.perform(MockMvcRequestBuilders.multipart("/upload/stromzaehler")
                        .file(file))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }
}
