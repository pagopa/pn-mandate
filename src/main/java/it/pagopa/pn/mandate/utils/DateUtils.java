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

    public static LocalDate parseDate(String date)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        return formatter.parse(date, LocalDate::from);
    }

    public static LocalDateTime parseTime(String date)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        return formatter.parse(date, LocalDateTime::from);
    }
}
