package com.example.transitradar.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ListLocationModel {
    //Map JSON fields to Java objects using @SerializedName annotations.
    @SerializedName("data")
    private List<LocationModel> mData;

    public List<LocationModel> getmData() {
        return mData;
    }
}
