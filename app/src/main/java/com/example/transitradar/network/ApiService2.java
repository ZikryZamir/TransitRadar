package com.example.transitradar.network;

import com.example.transitradar.model.ListLocationModel;
import com.example.transitradar.model.ListLocationModel2;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

//setup api endpoints
public interface ApiService2 {
    //Use Retrofit annotations to define HTTP requests.
    @GET("/api/servicestatus")
    //Provide a method for fetching all location data for a specified agency.
    Call<ListLocationModel2> getAllLocation();
}
