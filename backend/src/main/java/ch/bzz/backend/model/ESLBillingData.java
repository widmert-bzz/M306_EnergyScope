package ch.bzz.backend.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Model class representing the ESLBillingData XML format
 */
@Data
public class ESLBillingData {
    private Header header;
    private List<Meter> meters;

    @Data
    public static class Header {
        private String version;
        private LocalDateTime created;
        private String swSystemNameFrom;
        private String swSystemNameTo;
    }

    @Data
    public static class Meter {
        private String factoryNo;
        private String internalNo;
        private List<TimePeriod> timePeriods;
    }

    @Data
    public static class TimePeriod {
        private LocalDateTime end;
        private List<ValueRow> valueRows;
    }

    @Data
    public static class ValueRow {
        private String obis;
        private LocalDateTime valueTimeStamp;
        private Double value;
        private String status;
    }
}