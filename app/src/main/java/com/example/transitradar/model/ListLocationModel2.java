package com.example.transitradar.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

//modelling the api
public class ListLocationModel2 {
    //Map JSON fields to Java objects using @SerializedName annotations.
    @SerializedName("Data")
    private List<LocationModel2> mData2;

    public List<LocationModel2> getmData() {
        return mData2;
    }
}
