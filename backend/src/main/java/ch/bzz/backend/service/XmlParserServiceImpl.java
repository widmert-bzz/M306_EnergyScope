package ch.bzz.backend.service;

import ch.bzz.backend.model.EnergyData;
import ch.bzz.backend.model.Measurement;
import ch.bzz.backend.model.Messwert;
import ch.bzz.backend.model.StromzaehlerDaten;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the XmlParserService interface
 */
@Service
@Slf4j
public class XmlParserServiceImpl implements XmlParserService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    @Override
    public List<EnergyData> parseXml(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            String rootElement = document.getDocumentElement().getNodeName();

            if (rootElement.equals("ESLBillingData")) {
                return parseESLBillingData(document);
            } else if (rootElement.contains("ValidatedMeteredData")) {
                return parseValidatedMeteredData(document);
            } else {
                throw new IllegalArgumentException("Unknown XML format: " + rootElement);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException("Error parsing XML file", e);
        }
    }

    private List<EnergyData> parseESLBillingData(Document document) {
        List<EnergyData> result = new ArrayList<>();

        NodeList meterNodes = document.getElementsByTagName("Meter");
        for (int i = 0; i < meterNodes.getLength(); i++) {
            Node meterNode = meterNodes.item(i);
            if (meterNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meterElement = (Element) meterNode;
                String meterId = meterElement.getAttribute("factoryNo");

                NodeList timePeriodNodes = meterElement.getElementsByTagName("TimePeriod");
                for (int j = 0; j < timePeriodNodes.getLength(); j++) {
                    Node timePeriodNode = timePeriodNodes.item(j);
                    if (timePeriodNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element timePeriodElement = (Element) timePeriodNode;
                        String endTimeStr = timePeriodElement.getAttribute("end");
                        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);

                        List<Measurement> measurements = new ArrayList<>();
                        NodeList valueRowNodes = timePeriodElement.getElementsByTagName("ValueRow");
                        for (int k = 0; k < valueRowNodes.getLength(); k++) {
                            Node valueRowNode = valueRowNodes.item(k);
                            if (valueRowNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element valueRowElement = (Element) valueRowNode;
                                String obis = valueRowElement.getAttribute("obis");
                                String valueStr = valueRowElement.getAttribute("value");
                                double value = Double.parseDouble(valueStr);

                                LocalDateTime timestamp = endTime;
                                if (valueRowElement.hasAttribute("valueTimeStamp")) {
                                    String valueTimeStr = valueRowElement.getAttribute("valueTimeStamp");
                                    timestamp = LocalDateTime.parse(valueTimeStr, DATE_TIME_FORMATTER);
                                }

                                // Determine if it's production or consumption based on OBIS code
                                EnergyData.DataType dataType = determineDataTypeFromObis(obis);

                                Measurement measurement = Measurement.builder()
                                        .type(dataType)
                                        .identifier(obis)
                                        .value(value)
                                        .timestamp(timestamp)
                                        .unit("KWH") // Assuming KWH for ESLBillingData
                                        .build();

                                measurements.add(measurement);
                            }
                        }

                        if (!measurements.isEmpty()) {
                            EnergyData energyData = EnergyData.builder()
                                    .meterId(meterId)
                                    .timestamp(endTime)
                                    .build();

                            // Add measurements to energy data
                            for (Measurement measurement : measurements) {
                                energyData.addMeasurement(measurement);
                            }

                            result.add(energyData);
                        }
                    }
                }
            }
        }

        return result;
    }

    private List<EnergyData> parseValidatedMeteredData(Document document) {
        List<EnergyData> result = new ArrayList<>();

        NodeList meteringDataNodes = document.getElementsByTagName("rsm:MeteringData");
        for (int i = 0; i < meteringDataNodes.getLength(); i++) {
            Node meteringDataNode = meteringDataNodes.item(i);
            if (meteringDataNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meteringDataElement = (Element) meteringDataNode;

                // Check if it's production or consumption data
                boolean isProduction = meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").getLength() > 0;
                boolean isConsumption = meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").getLength() > 0;

                EnergyData.DataType dataType = isProduction ? EnergyData.DataType.PRODUCTION : EnergyData.DataType.CONSUMPTION;

                // Get meter ID
                String meterId = "";
                if (isProduction) {
                    Element productionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").item(0);
                    meterId = getTextContent(productionElement, "rsm:VSENationalID");
                } else if (isConsumption) {
                    Element consumptionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").item(0);
                    meterId = getTextContent(consumptionElement, "rsm:VSENationalID");
                }

                // Get interval
                Element intervalElement = (Element) meteringDataElement.getElementsByTagName("rsm:Interval").item(0);
                String startTimeStr = getTextContent(intervalElement, "rsm:StartDateTime");
                String endTimeStr = getTextContent(intervalElement, "rsm:EndDateTime");
                LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DATE_TIME_FORMATTER);
                LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);

                // Get unit
                Element productElement = (Element) meteringDataElement.getElementsByTagName("rsm:Product").item(0);
                String unit = getTextContent(productElement, "rsm:MeasureUnit");

                // Get observations
                List<Measurement> measurements = new ArrayList<>();
                NodeList observationNodes = meteringDataElement.getElementsByTagName("rsm:Observation");
                for (int j = 0; j < observationNodes.getLength(); j++) {
                    Node observationNode = observationNodes.item(j);
                    if (observationNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element observationElement = (Element) observationNode;

                        Element positionElement = (Element) observationElement.getElementsByTagName("rsm:Position").item(0);
                        String sequenceStr = getTextContent(positionElement, "rsm:Sequence");
                        int sequence = Integer.parseInt(sequenceStr);

                        String volumeStr = getTextContent(observationElement, "rsm:Volume");
                        double volume = Double.parseDouble(volumeStr);

                        // Calculate timestamp based on sequence, start time, and resolution
                        Element resolutionElement = (Element) meteringDataElement.getElementsByTagName("rsm:Resolution").item(0);
                        String resolutionStr = getTextContent(resolutionElement, "rsm:Resolution");
                        int resolution = Integer.parseInt(resolutionStr);

                        // Assuming resolution is in minutes
                        LocalDateTime timestamp = startTime.plusMinutes((sequence - 1) * resolution);

                        Measurement measurement = Measurement.builder()
                                .type(dataType)
                                .identifier(String.valueOf(sequence))
                                .value(volume)
                                .timestamp(timestamp)
                                .unit(unit)
                                .build();

                        measurements.add(measurement);
                    }
                }

                if (!measurements.isEmpty()) {
                    EnergyData energyData = EnergyData.builder()
                            .meterId(meterId)
                            .timestamp(endTime)
                            .build();

                    // Add measurements to energy data
                    for (Measurement measurement : measurements) {
                        energyData.addMeasurement(measurement);
                    }

                    result.add(energyData);
                }
            }
        }

        return result;
    }

    private String getTextContent(Element element, String tagName) {
        NodeList nodeList = element.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            return nodeList.item(0).getTextContent();
        }
        return "";
    }

    private EnergyData.DataType determineDataTypeFromObis(String obis) {
        // OBIS codes starting with "1-1:1" are typically for consumption
        // OBIS codes starting with "1-1:2" are typically for production
        if (obis.startsWith("1-1:1")) {
            return EnergyData.DataType.CONSUMPTION;
        } else if (obis.startsWith("1-1:2")) {
            return EnergyData.DataType.PRODUCTION;
        } else {
            // Default to consumption if we can't determine
            return EnergyData.DataType.CONSUMPTION;
        }
    }

    @Override
    public Map<String, StromzaehlerDaten> parseXmlToStromzaehlerDaten(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            String rootElement = document.getDocumentElement().getNodeName();

            if (rootElement.equals("ESLBillingData")) {
                return parseESLBillingDataToStromzaehlerDaten(document);
            } else if (rootElement.contains("ValidatedMeteredData")) {
                return parseValidatedMeteredDataToStromzaehlerDaten(document);
            } else {
                throw new IllegalArgumentException("Unknown XML format: " + rootElement);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException("Error parsing XML file", e);
        }
    }

    private Map<String, StromzaehlerDaten> parseESLBillingDataToStromzaehlerDaten(Document document) {
        Map<String, StromzaehlerDaten> result = new HashMap<>();

        NodeList meterNodes = document.getElementsByTagName("Meter");
        for (int i = 0; i < meterNodes.getLength(); i++) {
            Node meterNode = meterNodes.item(i);
            if (meterNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meterElement = (Element) meterNode;
                String meterId = meterElement.getAttribute("factoryNo");

                // Create or get StromzaehlerDaten for this meter
                StromzaehlerDaten stromzaehlerDaten = result.computeIfAbsent(meterId, StromzaehlerDaten::new);

                NodeList timePeriodNodes = meterElement.getElementsByTagName("TimePeriod");
                for (int j = 0; j < timePeriodNodes.getLength(); j++) {
                    Node timePeriodNode = timePeriodNodes.item(j);
                    if (timePeriodNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element timePeriodElement = (Element) timePeriodNode;
                        String endTimeStr = timePeriodElement.getAttribute("end");
                        LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DATE_TIME_FORMATTER);

                        NodeList valueRowNodes = timePeriodElement.getElementsByTagName("ValueRow");
                        for (int k = 0; k < valueRowNodes.getLength(); k++) {
                            Node valueRowNode = valueRowNodes.item(k);
                            if (valueRowNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element valueRowElement = (Element) valueRowNode;
                                String obis = valueRowElement.getAttribute("obis");
                                String valueStr = valueRowElement.getAttribute("value");
                                double value = Double.parseDouble(valueStr);

                                LocalDateTime timestamp = endTime;
                                if (valueRowElement.hasAttribute("valueTimeStamp")) {
                                    String valueTimeStr = valueRowElement.getAttribute("valueTimeStamp");
                                    timestamp = LocalDateTime.parse(valueTimeStr, DATE_TIME_FORMATTER);
                                }

                                // Determine if it's production or consumption based on OBIS code
                                EnergyData.DataType dataType = determineDataTypeFromObis(obis);

                                // Create Messwert and add to StromzaehlerDaten
                                Messwert messwert = Messwert.builder()
                                        .timestamp(timestamp)
                                        .absoluteValue(value)
                                        .relativeValue(0.0) // Calculate relative value if needed
                                        .unit("KWH") // Assuming KWH for ESLBillingData
                                        .type(dataType)
                                        .build();

                                stromzaehlerDaten.addMesswert(messwert);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private Map<String, StromzaehlerDaten> parseValidatedMeteredDataToStromzaehlerDaten(Document document) {
        Map<String, StromzaehlerDaten> result = new HashMap<>();

        NodeList meteringDataNodes = document.getElementsByTagName("rsm:MeteringData");
        for (int i = 0; i < meteringDataNodes.getLength(); i++) {
            Node meteringDataNode = meteringDataNodes.item(i);
            if (meteringDataNode.getNodeType() == Node.ELEMENT_NODE) {
                Element meteringDataElement = (Element) meteringDataNode;

                // Check if it's production or consumption data
                boolean isProduction = meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").getLength() > 0;
                boolean isConsumption = meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").getLength() > 0;

                EnergyData.DataType dataType = isProduction ? EnergyData.DataType.PRODUCTION : EnergyData.DataType.CONSUMPTION;

                // Get meter ID
                String meterId = "";
                if (isProduction) {
                    Element productionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ProductionMeteringPoint").item(0);
                    meterId = getTextContent(productionElement, "rsm:VSENationalID");
                } else if (isConsumption) {
                    Element consumptionElement = (Element) meteringDataElement.getElementsByTagName("rsm:ConsumptionMeteringPoint").item(0);
                    meterId = getTextContent(consumptionElement, "rsm:VSENationalID");
                }

                // Create or get StromzaehlerDaten for this meter
                StromzaehlerDaten stromzaehlerDaten = result.computeIfAbsent(meterId, StromzaehlerDaten::new);

                // Get interval
                Element intervalElement = (Element) meteringDataElement.getElementsByTagName("rsm:Interval").item(0);
                String startTimeStr = getTextContent(intervalElement, "rsm:StartDateTime");
                LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DATE_TIME_FORMATTER);

                // Get unit
                Element productElement = (Element) meteringDataElement.getElementsByTagName("rsm:Product").item(0);
                String unit = getTextContent(productElement, "rsm:MeasureUnit");

                // Get resolution
                Element resolutionElement = (Element) meteringDataElement.getElementsByTagName("rsm:Resolution").item(0);
                String resolutionStr = getTextContent(resolutionElement, "rsm:Resolution");
                int resolution = Integer.parseInt(resolutionStr);

                // Get observations
                NodeList observationNodes = meteringDataElement.getElementsByTagName("rsm:Observation");
                for (int j = 0; j < observationNodes.getLength(); j++) {
                    Node observationNode = observationNodes.item(j);
                    if (observationNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element observationElement = (Element) observationNode;

                        Element positionElement = (Element) observationElement.getElementsByTagName("rsm:Position").item(0);
                        String sequenceStr = getTextContent(positionElement, "rsm:Sequence");
                        int sequence = Integer.parseInt(sequenceStr);

                        String volumeStr = getTextContent(observationElement, "rsm:Volume");
                        double volume = Double.parseDouble(volumeStr);

                        // Calculate timestamp based on sequence, start time, and resolution
                        // Assuming resolution is in minutes
                        LocalDateTime timestamp = startTime.plusMinutes((sequence - 1) * resolution);

                        // Create Messwert and add to StromzaehlerDaten
                        Messwert messwert = Messwert.builder()
                                .timestamp(timestamp)
                                .absoluteValue(volume)
                                .relativeValue(0.0) // Calculate relative value if needed
                                .unit(unit)
                                .type(dataType)
                                .build();

                        stromzaehlerDaten.addMesswert(messwert);
                    }
                }
            }
        }

        return result;
    }
}
