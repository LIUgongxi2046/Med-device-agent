package com.ruoyi.medical.persistence;

import com.ruoyi.medical.contracts.WireValues;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTimestampTest {
    @ParameterizedTest
    @ValueSource(strings = {"2026-10-16T00:00:00", "2026-10-16T12:34:00", "2026-10-16T12:34:56", "2026-10-16T12:34:56.123456"})
    void preservesSecondsAndMicrosecondsForPublishedTimestampContract(String source) {
        var value = LocalDateTime.parse(source);
        var wire = DatabaseTimestamp.format(value);
        WireValues.utcTime(wire);
        assertEquals(source + "Z", wire);
        assertEquals(value, OffsetDateTime.parse(wire).toLocalDateTime());
    }
}
