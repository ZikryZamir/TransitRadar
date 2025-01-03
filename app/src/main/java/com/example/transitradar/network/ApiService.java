package com.example.transitradar.network;

import com.example.transitradar.model.ListLocationModel;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface ApiService {
    //Use Retrofit annotations to define HTTP requests.
    @GET("/api/position")
    //Provide a method for fetching all location data for a specified agency.
    Call<ListLocationModel> getAllLocation(@Query("agency") String agency);
}
