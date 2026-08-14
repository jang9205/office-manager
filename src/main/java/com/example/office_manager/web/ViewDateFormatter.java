package com.example.office_manager.web;

import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.time.temporal.TemporalAccessor;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

@Component("viewDate")
public class ViewDateFormatter {

    public String format(Object value, String pattern) {
        if (value == null) {
            return "";
        }
        if (value instanceof TemporalAccessor temporal) {
            return DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(temporal);
        }
        if (value instanceof Date date) {
            return new SimpleDateFormat(pattern, Locale.KOREAN).format(date);
        }
        return String.valueOf(value);
    }
}
