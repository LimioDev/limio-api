package com.limio.api.modulos.anuncio.actions.helper;

public final class DistanciaHelper {

    private static final double RAIO_DA_TERRA_KM = 6371.0;

    private DistanciaHelper() {
    }

    public static double haversineKm(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
        double dLat = Math.toRadians(latitudeB - latitudeA);
        double dLon = Math.toRadians(longitudeB - longitudeA);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitudeA)) * Math.cos(Math.toRadians(latitudeB)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RAIO_DA_TERRA_KM * Math.asin(Math.sqrt(Math.min(1.0, a)));
    }
}
