package com.example.event.util;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

public class DateUtil {
    public static String convertMonth(Integer month, String language) {
        Locale locale = language.equalsIgnoreCase("vi") ? new Locale("vi", "VN") : Locale.ENGLISH;
        return Month.of(month).getDisplayName(TextStyle .FULL, locale);
    }
}
