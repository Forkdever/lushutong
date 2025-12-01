package com.example.plan;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.Query;
import java.util.List;

public interface ApiService {

    // 新增：提交旅行方案到plans集合
    @POST("api/plans")
    Call<TravelPlan> createTravelPlan(@Body TravelPlan plan);

    // 在 ApiService.java 中添加
    @PUT("api/plans/{planId}")
    Call<TravelPlan> updateTravelPlan(@Path ("planId") String planId, @Body TravelPlan plan);

    // 或者使用 PATCH（部分更新）
    @PATCH("api/plans/{planId}")
    Call<TravelPlan> partialUpdateTravelPlan(@Path ("planId") String planId, @Body TravelPlan plan);

    // === 方案1：添加通过 plan_id 查询和更新的方法 ===

    // 通过 plan_id 查询旅行方案
    @GET("api/plans/planId/{planId}")
    Call<TravelPlan> getTravelPlanByPlanId(@Path("planId") String planId);

    // 通过 plan_id 进行部分更新
    @PATCH("api/plans/planId/{planId}")
    Call<TravelPlan> partialUpdateTravelPlanByPlanId(@Path("planId") String planId, @Body TravelPlan plan);

    // 通过 plan_id 进行完整更新
    @PUT("api/plans/planId/{planId}")
    Call<TravelPlan> updateTravelPlanByPlanId(@Path("planId") String planId, @Body TravelPlan plan);

    // 添加：通过用户ID查询所有行程
    //@GET("/api/plans")
    @GET("api/plans")
    Call<List<TravelPlan>> getTravelPlansByCreatorId(@Query("creatorId") String creatorId);

}

