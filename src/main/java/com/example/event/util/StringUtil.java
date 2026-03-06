package com.example.event.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class StringUtil {
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]+");

    public static String makeSlug(String input) {
        if (input == null || input.isEmpty()) return "";
        String nowhitespace = WHITESPACE.matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = normalized.replaceAll("\\p{M}", "");
        slug = slug.replace("Đ", "d").replace("đ", "d");
        slug = slug.toLowerCase(Locale.ENGLISH);
        slug = NONLATIN.matcher(slug).replaceAll("");
        slug = slug.replaceAll("-{2,}", "-");
        return slug.replaceAll("^-|-$", "");
    }
}
