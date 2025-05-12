package ch.bzz.backend.controller;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.service.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest
@AutoConfigureMockMvc
public class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocalStorageService localStorageService;

    @BeforeEach
    public void setup() {
        // Create test data
        List<EnergyData> testData = createTestData();
        
        // Save test data to local storage
        for (EnergyData data : testData) {
            localStorageService.saveEnergyData(List.of(data));
        }
        
        System.out.println("[DEBUG_LOG] Created test data: " + testData.size() + " energy data records");
    }

    @Test
    public void testExportJson() throws Exception {
        // Test JSON export without meter ID
        mockMvc.perform(MockMvcRequestBuilders.get("/export/json")
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].sensorId").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].data").isArray());

        // Test JSON export with meter ID
        mockMvc.perform(MockMvcRequestBuilders.get("/export/json")
                .param("meterId", "ID742")
                .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].sensorId").value("ID742"));
    }

    @Test
    public void testExportCsv() throws Exception {
        // Test CSV export without meter ID
        mockMvc.perform(MockMvcRequestBuilders.get("/export/csv")
                .accept(MediaType.TEXT_PLAIN))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(MockMvcResultMatchers.content().string(containsString("timestamp,value")));

        // Test CSV export with meter ID
        mockMvc.perform(MockMvcRequestBuilders.get("/export/csv")
                .param("meterId", "ID742")
                .accept(MediaType.TEXT_PLAIN))
                .andDo(print())
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(MockMvcResultMatchers.content().string(containsString("timestamp,value")));
    }

    private List<EnergyData> createTestData() {
        List<EnergyData> testData = new ArrayList<>();
        
        // Create test data for ID742 (Bezug)
        EnergyData data1 = EnergyData.builder()
                .meterId("ID742")
                .timestamp(LocalDateTime.now())
                .build();
        
        data1.addMeasurement(Measurement.builder()
                .timestamp(LocalDateTime.now().minusHours(2))
                .value(82.03)
                .build());
        
        data1.addMeasurement(Measurement.builder()
                .timestamp(LocalDateTime.now().minusHours(1))
                .value(85.47)
                .build());
        
        // Create test data for ID735 (Einspeisung)
        EnergyData data2 = EnergyData.builder()
                .meterId("ID735")
                .timestamp(LocalDateTime.now())
                .build();
        
        data2.addMeasurement(Measurement.builder()
                .timestamp(LocalDateTime.now().minusHours(2))
                .value(1129336.0)
                .build());
        
        data2.addMeasurement(Measurement.builder()
                .timestamp(LocalDateTime.now().minusHours(1))
                .value(1129339.0)
                .build());
        
        testData.add(data1);
        testData.add(data2);
        
        return testData;
    }
}