package it.pagopa.pn.mandate.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {
    
    public static String formatDate(LocalDate date)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        return date.format(formatter);
    }

    public static String formatTime(LocalDateTime datetime)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        return datetime.format(formatter);
    }
}
