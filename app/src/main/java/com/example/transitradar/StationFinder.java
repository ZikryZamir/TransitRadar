package com.example.transitradar;

import com.example.transitradar.model.LocationModel;

public class StationFinder {
    private static class Station {
        String name;
        double latitude;
        double longitude;

        Station(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private static final Station[] LINE_1_STATIONS = {
            new Station("Pelabuhan Klang", 2.999658116433375, 101.39138498016024),
            new Station("Jalan Kastam", 3.0135252319906076, 101.4029748933128),
            new Station("Kg Raja Uda", 3.0207061409312423, 101.41059483342347),
            new Station("Teluk Gadong", 3.0341140493427554, 101.42499013128158),
            new Station("Teluk Pulai", 3.0403867936684126, 101.43171131774668),
            new Station("Klang", 3.0429209928456173, 101.45039788173402),
            new Station("Bukit Badak", 3.0360904304118126, 101.47026550563547),
            new Station("Padang Jawa", 3.0523160543445873, 101.49293553022274),
            new Station("Shah Alam", 3.0565119494646926, 101.5253619344014),
            new Station("Batu Tiga", 3.075871119746039, 101.559605665766),
            new Station("Subang Jaya", 3.0847015808026192, 101.58822493076839),
            new Station("Setia Jaya", 3.083159018333681, 101.61139597481518),
            new Station("Seri Setia", 3.0845268027258035, 101.62194008155461),
            new Station("Kg Dato Harun", 3.0845074630286184, 101.63208463921082),
            new Station("Jalan Templer", 3.0834821862171835, 101.65654249830924),
            new Station("Petaling", 3.086326057418577, 101.6646221312899),
            new Station("Pantai Dalam", 3.0956432085949084, 101.67001430158155),
            new Station("Angkasapuri", 3.113272701807338, 101.67329457812001),
            new Station("Abdullah Hukum", 3.1191263159217657, 101.6734048713672),
            new Station("KL Sentral", 3.134253535641724, 101.68644030728191),
            new Station("Kuala Lumpur", 3.1395722206774517, 101.6937219721346),
            new Station("Bank Negara", 3.154424280955901, 101.69290899453918),
            new Station("Putra", 3.165614620243684, 101.6907803650646),
            new Station("Segambut", 3.1864830294932207, 101.66412816717119),
            new Station("Kepong", 3.202700752318889, 101.63726413276981),
            new Station("Kepong Sentral", 3.2086652632971595, 101.62844156737914),
            new Station("Sungai Buloh", 3.2062100143407704, 101.58024459508377),
            new Station("Kuang", 3.2582253554966973, 101.5548100543733),
            new Station("Rawang", 3.3193161078914803, 101.574802391656),
            new Station("Serendah", 3.3760951119324574, 101.61443692153597),
            new Station("Batang Kali", 3.4683814476572756, 101.6377746193552),
            new Station("Rasa", 3.500302456896405, 101.6341496998099),
            new Station("Kuala Kubu Bharu", 3.553254770683976, 101.63941251730527),
            new Station("Tanjung Malim", 3.6849179473667406, 101.51804264224288)
    };

    private static final Station[] LINE_2_STATIONS = {
            new Station("Batu Caves", 3.2377480242979013, 101.68114410489461),
            new Station("Taman Wahyu", 3.2145019574401728, 101.67216396667675),
            new Station("Kampung Batu", 3.204836659097834, 101.67558979047071),
            new Station("Batu Kentonmen", 3.198271530691951, 101.68118960470858),
            new Station("Sentul", 3.1821219097394624, 101.68871759329832),
            new Station("Putra", 3.165614620243684, 101.6907803650646),
            new Station("Bank Negara", 3.154424280955901, 101.69290899453918),
            new Station("Kuala Lumpur", 3.1395722206774517, 101.6937219721346),
            new Station("KL Sentral", 3.134253535641724, 101.68644030728191),
            new Station("Mid Valley", 3.1186831694246355, 101.67894202635456),
            new Station("Seputeh", 3.113507925541509, 101.68155059603573),
            new Station("Salak Selatan", 3.0982037773130218, 101.70543093522149),
            new Station("Bandar Tasik Selatan", 3.0763634775581, 101.71111624713375),
            new Station("Serdang", 3.0232890530511254, 101.71590582423693),
            new Station("Kajang", 2.9824338971312208, 101.79052090155567),
            new Station("Kajang 2", 2.962604874533639, 101.79195294748179),
            new Station("UKM", 2.9395303299599966, 101.78807012554923),
            new Station("Bangi", 2.9040709704437226, 101.7862917923652),
            new Station("Batang Benar", 2.8304205331406496, 101.82679621222843),
            new Station("Nilai", 2.8023331092842674, 101.79977256967972),
            new Station("Labu", 2.754072039465018, 101.82665684343962),
            new Station("Tiroi", 2.7413422119358497, 101.87189095053556),
            new Station("Seremban", 2.7185869876167468, 101.94090086475661),
            new Station("Senawang", 2.69065092905297, 101.97174368799224),
            new Station("Sungai Gadut", 2.660603554720887, 101.99616619122408),
            new Station("Rembau", 2.5931452529058934, 102.09460070840842),
            new Station("Pulau Sebang", 2.463961922106007, 102.22547356839846)
    };

    public static String getClosestStationName(LocationModel location) {
        Station[] stations = determineStationLine(location.getTripId());
        if (stations == null) return "Unknown";

        Station closestStation = null;
        double minDistance = Double.MAX_VALUE;

        for (Station station : stations) {
            double distance = calculateDistance(
                    location.getLatitude(), location.getLongitude(),
                    station.latitude, station.longitude
            );
            if (distance < minDistance) {
                minDistance = distance;
                closestStation = station;
            }
        }
        return closestStation != null ? closestStation.name : "Unknown";
    }

    private static Station[] determineStationLine(String tripId) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{2})(\\d{2})");
        java.util.regex.Matcher matcher = pattern.matcher(tripId);
        if (!matcher.find()) return null;

        int lineNumber = Integer.parseInt(matcher.group(1));
        return (lineNumber == 21 || lineNumber == 23) ? LINE_1_STATIONS : LINE_2_STATIONS;
    }

    private static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        float[] results = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0] / 1000.0; // Convert meters to kilometers
    }
}