package com.example.transitradar;

public class RouteDeterminer {
    public static String determineRouteText(String tripId) {
        // Extract line and trip numbers from tripId using regex
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{2})(\\d{2})");
        java.util.regex.Matcher matcher = pattern.matcher(tripId);

        if (!matcher.find()) {
            return "Unknown Route";
        }

        int lineNumber = Integer.parseInt(matcher.group(1));
        int tripNumber = Integer.parseInt(matcher.group(2));
        String routeText;

        if (lineNumber % 2 != 0) { // Line 21 or 23 (Port Klang - Tg Malim)
            if (tripNumber % 2 == 0) {
                if (isFullRouteToTgMalim(tripNumber)) {
                    routeText = "Port Klang > Tg Malim";
                } else {
                    routeText = "KL Sentral > Tg Malim";
                }
            } else {
                if (isFullRouteToPortKlang(tripNumber)) {
                    routeText = "Tg Malim > Port Klang";
                } else {
                    routeText = "Tg Malim > KL Sentral";
                }
            }
        } else { // Line 20 or 22 (Batu Caves - P. Sebang)
            if (tripNumber % 2 == 0) {
                if (isPartialRouteToBtCaves(tripNumber)) {
                    routeText = "Sg Gadut > Bt Caves";
                } else {
                    routeText = "P. Sebang > Bt Caves";
                }
            } else {
                if (isPartialRouteToPSebang(tripNumber)) {
                    routeText = "Sentul > P. Sebang";
                } else {
                    if (isPartialRouteFromBtCaves(tripNumber)) {
                        routeText = "Bt Caves > Sg Gadut";
                    } else {
                        routeText = "Bt Caves > P. Sebang";
                    }
                }
            }
        }
        return routeText;
    }

    private static boolean isFullRouteToTgMalim(int tripNumber) {
        return tripNumber == 4 || tripNumber == 10 || tripNumber == 12 ||
                tripNumber == 18 || tripNumber == 22 || tripNumber == 26;
    }

    private static boolean isFullRouteToPortKlang(int tripNumber) {
        return tripNumber == 59 || tripNumber == 63 || tripNumber == 67 || tripNumber == 71 ||
                tripNumber == 75 || tripNumber == 79;
    }

    private static boolean isPartialRouteToBtCaves(int tripNumber) {
        return tripNumber == 16 || tripNumber == 42;
    }

    private static boolean isPartialRouteToPSebang(int tripNumber) {
        return tripNumber == 3;
    }

    private static boolean isPartialRouteFromBtCaves(int tripNumber) {
        return tripNumber == 35 || tripNumber == 59 || tripNumber == 63;
    }
}
