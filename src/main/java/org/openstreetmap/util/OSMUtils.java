package org.openstreetmap.util;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class OSMUtils {
    // OSM currently saves coordinates with a precision of 7 fractional digits
    private static final int PRECISION = 7;
    private static final int MULTIPLICATION_FACTOR;

    public static final NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);
    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"); // Not Thread safe!

    static {
        int result = 1;
        for (int i = 0; i < PRECISION; i++) {
            result *= 10;
        }
        MULTIPLICATION_FACTOR = result;

        numberFormat.setMinimumFractionDigits(7);

        TimeZone timeZone = TimeZone.getTimeZone("UTC");
        dateFormat.setTimeZone(timeZone);
    }

    public static int convertCoordinateToInt(double coord) {
        return (int) Math.round(coord * MULTIPLICATION_FACTOR);
    }

    public static double convertCoordinateToDouble(int coord) {
        return ((double) coord) / MULTIPLICATION_FACTOR;
    }

    public static String convertCoordinateToString(int coord) {
        return numberFormat.format(OSMUtils.convertCoordinateToDouble(coord));
    }

}
