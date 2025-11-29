package com.example.lushutong

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/plans")
    fun createTravelPlan(@Body plan: TravelPlan): Call<TravelPlan>

    @GET("api/plans/planId/{planId}")
    fun getTravelPlanByPlanId(@Path("planId") planId: String): Call<TravelPlan>

    @PUT("api/plans/planId/{planId}")
    fun updateTravelPlanByPlanId(
        @Path("planId") planId: String,
        @Body plan: TravelPlan
    ): Call<TravelPlan>

    @GET("api/plans")
    fun getTravelPlansByCreatorId(@Query("creatorId") creatorId: Int): Call<List<TravelPlan>>
}