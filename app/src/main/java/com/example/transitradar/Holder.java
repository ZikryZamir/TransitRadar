package com.example.transitradar;

import android.location.Location;

import com.example.transitradar.model.LocationModel;

import java.util.ArrayList;
import java.util.List;

public class Holder {
    private static Location currentLocation;
    private static List<LocationModel> notificationList = new ArrayList<>();
    private static List<LocationModel> locationModels = new ArrayList<>();


    public static List<LocationModel> getNotificationList() {
        return notificationList;
    }

    public static Location getCurrentLocation() {
        return currentLocation;
    }

    public static List<LocationModel> getLocationModels() {
        return locationModels;
    }

    public static void setNotificationList(List<LocationModel> notification) {
        notificationList = notification;
    }

    public static void setCurrentLocation(Location location) {
        currentLocation = location;
    }

    public static void setLocationModels(List<LocationModel> locationModels1) {
        locationModels = locationModels1;
    }
}
