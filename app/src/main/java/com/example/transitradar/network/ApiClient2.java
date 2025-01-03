package com.example.transitradar.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient2 {
    //Define the base URL for the API.
    public static final String BASE_url = "https://api.mtrec.name.my";
    //Create and configure a Retrofit instance.
    public static Retrofit retrofit = null;
    //Provide a method to get the ApiService2 interface.
    public static ApiService2 getRetrofit(){
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_url)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService2.class);
    }
}
