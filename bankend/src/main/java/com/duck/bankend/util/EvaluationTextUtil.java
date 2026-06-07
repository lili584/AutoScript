package com.duck.bankend.util;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EvaluationTextUtil {

    private static final Pattern DIRECT_QUOTE_PATTERN = Pattern.compile("[“\"「『‘]([^”\"」』’]+)[”\"」』’]");

    private EvaluationTextUtil() {
    }

    public static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）《》【】—…-]+", "").toLowerCase();
    }

    public static boolean hasMeaningfulText(String value) {
        return normalize(value).length() >= 3;
    }

    public static double similarity(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        if (!StringUtils.hasText(a) && !StringUtils.hasText(b)) {
            return 1;
        }
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return 0;
        }
        int distance = levenshtein(a, b);
        return 1.0 - (double) distance / Math.max(a.length(), b.length());
    }

    public static boolean containsNormalized(String source, String target) {
        String normalizedSource = normalize(source);
        String normalizedTarget = normalize(target);
        return StringUtils.hasText(normalizedSource)
                && StringUtils.hasText(normalizedTarget)
                && normalizedSource.contains(normalizedTarget);
    }

    public static boolean containsAsDirectQuote(String source, String text) {
        String normalizedText = normalize(text);
        if (!StringUtils.hasText(source) || !StringUtils.hasText(normalizedText)) {
            return false;
        }
        Matcher matcher = DIRECT_QUOTE_PATTERN.matcher(source);
        while (matcher.find()) {
            String quoted = normalize(matcher.group(1));
            if (StringUtils.hasText(quoted) && (quoted.contains(normalizedText) || normalizedText.contains(quoted))) {
                return true;
            }
        }
        return false;
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[b.length()];
    }
}
