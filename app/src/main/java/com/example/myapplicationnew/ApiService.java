package com.example.myapplicationnew;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    @GET("bank/currency")
    Call<List<Currency>> getCurrencyRates();
}
