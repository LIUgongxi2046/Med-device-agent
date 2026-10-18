package com.ruoyi.medical.persistence;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** UTC database values must retain seconds even at an exact minute boundary. */
final class DatabaseTimestamp {
    private DatabaseTimestamp() { }
    static String format(LocalDateTime value) {
        return value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
