package it.pagopa.pn.mandate.utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    private DateUtils(){}
    
    public static String formatDate(ZonedDateTime date)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        return date.format(formatter);
    }

    public static String formatTime(ZonedDateTime datetime)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return datetime.format(formatter.withZone(ZoneId.of("Europe/Rome")));
    }

    public static ZonedDateTime parseDate(String date)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE;
        LocalDate locdate = LocalDate.parse(date, formatter);

        return locdate.atStartOfDay(ZoneId.of("Europe/Rome"));
    }

    public static ZonedDateTime parseTime(String date)
    {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        return formatter.parse(date, ZonedDateTime::from);
    }
}
