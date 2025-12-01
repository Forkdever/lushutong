package com.example.plan;

import android.util.Log;
import com.example.plan.TravelPlan;
import com.google.gson.Gson;
import okhttp3.*;
        import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HttpCollaborationManager {
    private static final String TAG = "HttpCollaborationManager";
    private static HttpCollaborationManager instance;

    private OkHttpClient client;
    private ScheduledExecutorService scheduler;
    private CollaborationListener listener;
    private boolean isListening = false;
    private String currentPlanId;
    private String baseUrl = "https://your-server.com/api"; // 替换为你的服务器地址

    public interface CollaborationListener {
        void onPlanUpdated(TravelPlan updatedPlan);
        void onError(String errorMessage);
    }

    private HttpCollaborationManager() {
        client = new OkHttpClient();
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public static synchronized HttpCollaborationManager getInstance() {
        if (instance == null) {
            instance = new HttpCollaborationManager();
        }
        return instance;
    }

    public void startListening(String planId, CollaborationListener listener) {
        if (isListening) {
            stopListening();
        }

        this.listener = listener;
        this.currentPlanId = planId;
        this.isListening = true;

        // 每3秒轮询一次
        scheduler.scheduleAtFixedRate(this::checkForUpdates, 0, 3, TimeUnit.SECONDS);
        Log.d(TAG, "开始HTTP轮询监听，planId: " + planId);
    }

    private void checkForUpdates() {
        if (!isListening) return;

        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/travel-plans/" + currentPlanId)
                    .get()
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "HTTP请求失败: " + e.getMessage());
                    if (listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            listener.onError("网络请求失败: " + e.getMessage());
                        });
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "HTTP响应失败: " + response.code());
                        return;
                    }

                    try {
                        String responseBody = response.body().string();
                        Gson gson = new Gson();
                        TravelPlan travelPlan = gson.fromJson(responseBody, TravelPlan.class);

                        // 在主线程回调
                        if (listener != null && travelPlan != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                listener.onPlanUpdated(travelPlan);
                            });
                        }

                        Log.d(TAG, "收到行程更新: " + currentPlanId);
                    } catch (Exception e) {
                        Log.e(TAG, "解析响应失败: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "轮询检查更新失败: " + e.getMessage());
        }
    }

    public void stopListening() {
        isListening = false;
        listener = null;
        currentPlanId = null;
        Log.d(TAG, "停止HTTP监听");
    }

    public void cleanup() {
        stopListening();
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
        }
    }

    // 通知服务器有更新（可选）
    public void notifyUpdate(String planId) {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/travel-plans/" + planId + "/notify")
                    .post(RequestBody.create(null, new byte[0]))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "通知更新失败: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    Log.d(TAG, "通知更新成功");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "通知更新异常: " + e.getMessage());
        }
    }
}
