package com.loganalyzer.elasticsearch.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LogQueryBuilderIntervalTest {

    @Test
    void errorHistogramInterval_usesOneSecondForOneHour() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plusSeconds(3600);
        assertEquals("1s", LogQueryBuilder.errorHistogramInterval(from, to));
    }

    @Test
    void errorHistogramInterval_usesOneHourForLongRange() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = from.plusSeconds(8 * 24 * 3600);
        assertEquals("1h", LogQueryBuilder.errorHistogramInterval(from, to));
    }
}
