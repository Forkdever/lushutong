package com.example.plan;

import android.util.Log;
import com.example.plan.TravelPlan;
import com.google.gson.Gson;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import org.bson.Document;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class CollaborationManager {
    private static final String TAG = "CollaborationManager";
    private static CollaborationManager instance;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private ExecutorService executorService;
    private CollaborationListener listener;
    private boolean isListening = false;
    private Thread listeningThread;

    // MongoDB连接配置 - 替换为你的实际连接字符串
    private static final String CONNECTION_STRING = "mongodb+srv://username:password@cluster.mongodb.net/your_database?retryWrites=true&w=majority";
    private static final String DATABASE_NAME = "travel_planner";
    private static final String COLLECTION_NAME = "travel_plans";

    public interface CollaborationListener {
        void onPlanUpdated(TravelPlan updatedPlan);
        void onError(String errorMessage);
    }

    private CollaborationManager() {
        executorService = Executors.newSingleThreadExecutor();
        initializeMongoDB();
    }

    public static synchronized CollaborationManager getInstance() {
        if (instance == null) {
            instance = new CollaborationManager();
        }
        return instance;
    }

    private void initializeMongoDB() {
        try {
            // 初始化MongoDB客户端
            mongoClient = MongoClients.create(CONNECTION_STRING);
            database = mongoClient.getDatabase(DATABASE_NAME);

            Log.d(TAG, "MongoDB客户端初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "MongoDB客户端初始化失败: " + e.getMessage());
        }
    }

    public void startListening(String planId, CollaborationListener listener) {
        if (isListening) {
            stopListening();
        }

        this.listener = listener;
        this.isListening = true;

        listeningThread = new Thread(() -> {
            try {
                MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

                Log.d(TAG, "开始监听Change Stream, planId: " + planId);

                // 使用Change Stream监听特定文档的变化
                for (ChangeStreamDocument<Document> change : collection.watch()) {
                    if (!isListening) {
                        break;
                    }

                    try {
                        Document fullDocument = change.getFullDocument();
                        if (fullDocument != null) {
                            String documentPlanId = fullDocument.getString("planId");
                            if (planId.equals(documentPlanId)) {
                                // 将Document转换为TravelPlan对象
                                Gson gson = new Gson();
                                TravelPlan travelPlan = gson.fromJson(fullDocument.toJson(), TravelPlan.class);

                                // 在主线程回调
                                if (listener != null) {
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        listener.onPlanUpdated(travelPlan);
                                    });
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理变更事件失败: " + e.getMessage());
                        if (listener != null) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                listener.onError("处理变更事件失败: " + e.getMessage());
                            });
                        }
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Change Stream监听失败: " + e.getMessage());
                isListening = false;
                if (listener != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        listener.onError("监听失败: " + e.getMessage());
                    });
                }
            }
        });

        listeningThread.start();
    }

    public void stopListening() {
        isListening = false;
        listener = null;

        if (listeningThread != null && listeningThread.isAlive()) {
            listeningThread.interrupt();
        }

        Log.d(TAG, "停止监听");
    }

    public boolean isListening() {
        return isListening;
    }

    public void cleanup() {
        stopListening();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}