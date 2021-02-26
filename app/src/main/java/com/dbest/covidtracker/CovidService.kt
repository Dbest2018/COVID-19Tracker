package com.dbest.covidtracker

import retrofit2.Call
import retrofit2.http.GET

interface CovidService{
    @GET("us/daily.json")
    fun getNationalData(): Call<List<CovidData>>

    @GET("states/daily.json")
    fun getStateData(): Call<List<CovidData>>
}