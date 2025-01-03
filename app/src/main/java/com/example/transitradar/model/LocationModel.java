package com.example.transitradar.model;

import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class LocationModel implements Serializable {
    //Nested static classes for Trip, Position, and Vehicle.
    @SerializedName("trip")
    private Trip trip;
    @SerializedName("position")
    private Position position;
    @SerializedName("timestamp")
    private String timestamp;
    @SerializedName("vehicle")
    private Vehicle vehicle;

    public LocationModel(Trip trip, Position position, String timestamp, Vehicle vehicle) {
        this.trip = trip;
        this.position = position;
        this.timestamp = timestamp;
        this.vehicle = vehicle;
    }

    //Provide getter methods to access the data.
    public String getTripId() {
        return trip.getTripId();
    }

    public double getLatitude() {
        return position.getLatitude();
    }

    public double getLongitude() {
        return position.getLongitude();
    }

    public double getBearing() {
        return position.getBearing();
    }

    public double getSpeed() {
        return position.getSpeed();
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getId() {
        return vehicle.getId();
    }

    public String getLabel() {
        return vehicle.getLabel();
    }

    public void setTripId(String tripId) {
        this.trip.tripId = tripId;
    }

    public void setLatitude(double latitude) { this.position.latitude = latitude; }

    public void setLongitude(double longitude) { this.position.longitude = longitude; }

    // Trip class
    static class Trip {
        @SerializedName("tripId")
        private String tripId;

        public String getTripId() {
            return tripId;
        }
    }

    // Position class
    static class Position {
        @SerializedName("latitude")
        private double latitude;
        @SerializedName("longitude")
        private double longitude;
        @SerializedName("bearing")
        private double bearing;
        @SerializedName("speed")
        private double speed;

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public double getBearing() {
            return bearing;
        }

        public double getSpeed() {
            return speed;
        }
    }

    // Vehicle class
    static class Vehicle {
        @SerializedName("id")
        private String id;
        @SerializedName("label")
        private String label;

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }
    }
}
