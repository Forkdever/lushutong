package com.example.plan;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 旅行方案数据上传/读取工具类
 */

public class TravelPlanUploader {
    private static final String TAG = "TravelPlanUploader";
    private static final String BASE_URL = "http://39.97.43.117:3000/";

    private Context context;
    private ApiService apiService;
    private Handler mainHandler;

    // 在TravelPlanUploader类中添加以下方法
    public void enableCollaboration(String planId) {
        // 确保文档支持Change Streams（如果需要的话）
        Log.d("TravelPlanUploader", "启用协作模式，planId: " + planId);
    }

    public void disableCollaboration(String planId) {
        Log.d("TravelPlanUploader", "禁用协作模式，planId: " + planId);
    }

    // 回调接口
    public static interface UploadCallback {
        void onSuccess(TravelPlan plan);
        void onFailure(String errorMsg);
    }

    public static interface FetchCallback {
        void onSuccess(TravelPlan plan);
        void onFailure(String errorMsg);
    }

    public TravelPlanUploader(Context context) {
        this.context = context;
        this.apiService = ApiClient_Mongo.getClient(BASE_URL).create(ApiService.class);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 上传旅行方案到数据库
     */
    public void uploadTravelPlan(TravelPlan plan, UploadCallback callback) {
        if (plan == null) {
            String errorMsg = "旅行方案对象不能为空";
            Log.e(TAG, errorMsg);
            if (callback != null) callback.onFailure(errorMsg);
            showToast(errorMsg);
            return;
        }

        if (plan.getCreatedAt() == null) plan.setCreatedAt(new Date());
        if (plan.getUpdatedAt() == null) plan.setUpdatedAt(new Date());

        Call<TravelPlan> call = apiService.createTravelPlan(plan);
        call.enqueue(new Callback<TravelPlan>() {
            @Override
            public void onResponse(Call<TravelPlan> call, Response<TravelPlan> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) callback.onSuccess(response.body());
                    showToast("上传成功");
                } else {
                    String errorMsg = "上传失败: " + response.code();
                    if (callback != null) callback.onFailure(errorMsg);
                    showToast(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<TravelPlan> call, Throwable t) {
                String errorMsg = "网络错误: " + t.getMessage();
                if (callback != null) callback.onFailure(errorMsg);
                showToast(errorMsg);
            }
        });
    }

    /**
     * 通过planId异步读取旅行方案
     */
    public void fetchTravelPlanByPlanId(String planId, FetchCallback callback) {
        if (planId == null || planId.isEmpty()) {
            String errorMsg = "planId不能为空";
            if (callback != null) callback.onFailure(errorMsg);
            showToast(errorMsg);
            return;
        }

        Call<TravelPlan> call = apiService.getTravelPlanByPlanId(planId);
        call.enqueue(new Callback<TravelPlan>() {
            @Override
            public void onResponse(Call<TravelPlan> call, Response<TravelPlan> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) callback.onSuccess(response.body());
                } else {
                    String errorMsg = "读取失败: " + response.code();
                    if (callback != null) callback.onFailure(errorMsg);
                    showToast(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<TravelPlan> call, Throwable t) {
                String errorMsg = "网络错误: " + t.getMessage();
                if (callback != null) callback.onFailure(errorMsg);
                showToast(errorMsg);
            }
        });
    }

    /**
     * 同步读取旅行方案（注意：不要在主线程调用）
     */
    public TravelPlan getTravelPlanByPlanIdSync(String planId) {
        try {
            if (planId == null || planId.isEmpty()) return null;

            Call<TravelPlan> call = apiService.getTravelPlanByPlanId(planId);
            Response<TravelPlan> response = call.execute();

            if (response.isSuccessful() && response.body() != null) {
                return response.body();
            }
        } catch (Exception e) {
            Log.e(TAG, "同步读取失败", e);
        }
        return null;
    }

    /**
     * 异步读取旅行方案并返回数组格式
     */
    public void getTravelPlanArrayByPlanId(String planId, FetchCallback callback) {
        new Thread(() -> {
            TravelPlan plan = getTravelPlanByPlanIdSync(planId);

            if (plan != null) {
                // 转换为数组格式（手动构建）
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

                    TravelPlan arrayFormatPlan = new TravelPlan(
                            plan.getPlanId(),
                            plan.getTitle(),
                            plan.getCreatorId(),
                            plan.getCollaborators(),
                            plan.getStatus(),
                            new Content(
                                    plan.getContent().getDestination(),
                                    plan.getContent().getStartDate(),
                                    plan.getContent().getEndDate(),
                                    plan.getContent().getDays(),
                                    plan.getContent().getTransport(),
                                    null // 替换plan.getContent().getNote()
                            ),
                            plan.getVersionHistory(),
                            plan.getTags(),
                            plan.getCreatedAt(),
                            plan.getUpdatedAt()
                    );

                    // 在主线程回调
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(arrayFormatPlan);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "构建数组格式失败", e);
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onFailure("构建数据失败");
                        }
                    });
                }
            } else {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onFailure("读取数据为空");
                    }
                });
            }
        }).start();
    }

    // 添加新的回调接口（用于列表返回）
    public static interface FetchListCallback {
        void onSuccess(List<TravelPlan> plans);
        void onFailure(String errorMsg);
    }

    /**
     * 通过planId更新旅行方案（全量更新）
     */
    public void updateTravelPlanByPlanId(String planId, TravelPlan plan, UploadCallback callback) {
        if (planId == null || planId.isEmpty()) {
            String errorMsg = "planId不能为空";
            Log.e(TAG, errorMsg);
            if (callback != null) callback.onFailure(errorMsg);
            showToast(errorMsg);
            return;
        }

        if (plan == null) {
            String errorMsg = "旅行方案对象不能为空";
            Log.e(TAG, errorMsg);
            if (callback != null) callback.onFailure(errorMsg);
            showToast(errorMsg);
            return;
        }

        // 更新时间戳为当前时间
        plan.setUpdatedAt(new Date());
        // 确保planId一致
        plan.setPlanId(planId);

        Call<TravelPlan> call = apiService.updateTravelPlanByPlanId(planId, plan);
        call.enqueue(new Callback<TravelPlan>() {
            @Override
            public void onResponse(Call<TravelPlan> call, Response<TravelPlan> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) callback.onSuccess(response.body());
                    showToast("更新成功");
                } else {
                    String errorMsg = "更新失败: " + response.code();
                    if (callback != null) callback.onFailure(errorMsg);
                    showToast(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<TravelPlan> call, Throwable t) {
                String errorMsg = "网络错误: " + t.getMessage();
                if (callback != null) callback.onFailure(errorMsg);
                showToast(errorMsg);
            }
        });
    }

    /**
     * 通过用户ID获取该用户创建的所有行程
     */

    public void getTravelPlansByCreatorId(int creatorId, FetchListCallback callback) {
        Call<List<TravelPlan>> call = apiService.getTravelPlansByCreatorId(String.valueOf(creatorId));
        call.enqueue(new Callback<List<TravelPlan>>() {
            @Override
            public void onResponse(Call<List<TravelPlan>> call, Response<List<TravelPlan>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    if (callback != null) {
                        callback.onSuccess(response.body());
                    }
                } else {
                    String errorMsg = "查询失败: " + response.code();
                    if (callback != null) {
                        callback.onFailure(errorMsg);
                    }
                    showToast(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<List<TravelPlan>> call, Throwable t) {
                String errorMsg = "网络错误: " + t.getMessage();
                if (callback != null) {
                    callback.onFailure(errorMsg);
                }
                showToast(errorMsg);
            }
        });
    }

    /*
    public void getTravelPlansByCreatorId(int creatorId, FetchListCallback callback) {
        Log.d(TAG, "开始查询用户行程，creatorId: " + creatorId);
        Log.d(TAG, "构建的URL: " + BASE_URL + "api/plans?creatorId=" + creatorId);

        Call<List<TravelPlan>> call = apiService.getTravelPlansByCreatorId(String.valueOf(creatorId));
        call.enqueue(new Callback<List<TravelPlan>>() {
            @Override
            public void onResponse(Call<List<TravelPlan>> call, Response<List<TravelPlan>> response) {
                Log.d(TAG, "HTTP状态码: " + response.code());
                Log.d(TAG, "响应消息: " + response.message());

                if (!response.isSuccessful()) {
                    // 打印详细的错误信息
                    if (response.errorBody() != null) {
                        try {
                            String errorBody = response.errorBody().string();
                            Log.e(TAG, "错误响应体: " + errorBody);
                        } catch (Exception e) {
                            Log.e(TAG, "读取错误响应体失败", e);
                        }
                    }
                }

                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "查询成功，返回行程数量: " + response.body().size());
                    if (callback != null) {
                        callback.onSuccess(response.body());
                    }
                } else {
                    String errorMsg = "查询失败: " + response.code();
                    Log.e(TAG, errorMsg);
                    if (callback != null) {
                        callback.onFailure(errorMsg);
                    }
                    showToast(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<List<TravelPlan>> call, Throwable t) {
                String errorMsg = "网络错误: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                if (callback != null) {
                    callback.onFailure(errorMsg);
                }
                showToast(errorMsg);
            }
        });
    }*/

    // 显示Toast
    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }
}