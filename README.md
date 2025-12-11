# lushutong - 旅行规划Android应用
[![Android Studio](https://img.shields.io/badge/Android%20Studio-2022.1.1+-blue.svg)](https://developer.android.com/studio)
[![JDK](https://img.shields.io/badge/JDK-11+-red.svg)](https://www.oracle.com/java/technologies/downloads/)
[![Gradle](https://img.shields.io/badge/Gradle-7.4+-green.svg)](https://gradle.org/)
[![Android SDK](https://img.shields.io/badge/Android%20SDK-24+-orange.svg)](https://developer.android.com/studio/releases/platforms)

## 项目简介
lushutong 是一款面向旅行爱好者的Android端旅行规划应用，核心聚焦「旅行计划管理、地图路径规划、多人实时协作、旅行社区互动」四大核心场景，旨在帮助用户高效制定旅行行程，支持多人协同编辑行程方案，并提供旅行经验分享的社区功能。

### 核心功能
- **旅行计划管理**：创建/编辑/删除旅行计划，支持行程版本记录、标签分类、协作者管理；
- **地图与路径规划**：集成地图SDK实现POI地点搜索、步行/公交路径规划与可视化，支持路径耗时/距离计算；
- **多人实时协作**：基于MongoDB Change Stream/HTTP轮询实现行程实时同步，支持协作码验证加入行程编辑；
- **旅行社区**：发布旅行帖子、查看详情、评论互动，基于登录状态管控发布/互动权限；
- **用户系统**：登录状态管理，支撑社区、协作等权限相关功能。

## 软件架构
### 整体架构（分层设计）
项目采用分层架构设计，解耦UI、业务逻辑与数据层，便于维护与扩展：
| 层级           | 职责说明                                                                 | 核心组件/类                                                                 |
|----------------|--------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| UI层           | 界面展示、用户交互、页面跳转                                             | Activity（Login/CreateTrip/MapDemo/Community）、Fragment、RecyclerView Adapter |
| 业务逻辑层     | 封装核心业务规则，承接UI层请求并处理业务逻辑                             | TravelPlanManager、CollaborationManager、LoginStatusManager、TravelMapController |
| 数据层         | 数据存储与网络交互                                                       | Retrofit/OkHttp（网络请求）、MongoDB（行程数据存储）、Gson（JSON解析）       |
| 第三方服务层   | 集成外部SDK/服务，支撑核心能力                                           | 高德地图SDK（地图/路径）、ScheduledExecutorService（定时轮询）               |

### 核心包结构
```
lst_Andriod/
├── app/
│   ├── src/main/java/
│   │   ├── com.example.lushutong/       # 核心UI组件（Activity/Fragment）
│   │   ├── com.example.plan/            # 旅行计划业务（模型/管理器/上传器）
│   │   ├── com.example.llw.newmapdemo/  # 地图模块（路径/覆盖物/POI搜索）
│   │   ├── com.example.collaboration/   # 协作功能（验证/实时监听）
│   │   ├── com.example.community/       # 社区功能（帖子/评论）
│   │   └── com.example.login/           # 用户登录/状态管理
│   └── src/main/res/                    # 资源文件（布局/图片/字符串）
├── gradle/                              # Gradle构建配置
├── map.jks                              # 应用签名文件
└── build.gradle                         # 项目全局构建配置
```

## 快速开始
### 环境准备
确保本地已安装以下工具：
- Android Studio 2022.1.1 (Chipmunk) 及以上版本
- JDK 11 及以上
- Gradle 7.4 及以上
- Android SDK API 24 (Android 7.0) 及以上

### 1. 克隆仓库
```bash
git clone https://github.com/Forkdever/lushutong.git
cd lushutong/lst_Andriod
```

### 2. 配置第三方服务
#### （1）高德地图SDK配置
1. 前往[高德地图开放平台](https://lbs.amap.com/)注册开发者账号，创建应用并获取Android端API Key；
2. 打开 `app/src/main/AndroidManifest.xml`，替换占位符为你的API Key：
   ```xml
   <meta-data
       android:name="com.amap.api.v2.apikey"
       android:value="你的高德地图API Key"/>
   ```

#### （2）MongoDB配置
1. 搭建MongoDB服务（本地部署/云端Atlas），创建旅行计划数据库（如`travel_plan_db`）；
2. 打开 `com/example/plan/ApiClient_Mongo.java`，替换MongoDB连接信息：
   ```java
   private static final String MONGO_URI = "mongodb://[用户名]:[密码]@[地址]:[端口]/travel_plan_db";
   ```

### 3. 构建并运行
1. 用Android Studio打开`lst_Andriod`目录；
2. 等待Gradle同步完成（首次同步需下载依赖，建议开启科学上网）；
3. 连接Android设备/启动模拟器（Android 7.0+）；
4. 点击「Run 'app'」按钮，或执行命令行构建：
   ```bash
   # 构建Debug包
   ./gradlew assembleDebug
   # 安装到设备
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## 配置需求
### 开发环境配置
| 工具/依赖       | 版本要求                | 备注                     |
|-----------------|-------------------------|--------------------------|
| Android Studio  | 2022.1.1 (Chipmunk)+    | 推荐稳定版               |
| JDK             | 11+                     | 兼容Android Gradle插件   |
| Gradle          | 7.4+                    | 与Android Studio适配     |
| Android SDK     | API 24 (Android 7.0)+   | 最低兼容版本             |

### 第三方服务配置
| 服务名称       | 配置要求                                                                 |
|----------------|--------------------------------------------------------------------------|
| 高德地图SDK    | 申请API Key，配置定位/网络权限                                           |
| MongoDB        | 部署服务并配置读写权限，确保应用可访问数据库                             |
| 网络权限       | AndroidManifest.xml中已配置`INTERNET`和`ACCESS_NETWORK_STATE`权限        |

### 设备要求
- 运行设备：Android 7.0 (API 24) 及以上版本的手机/平板；
- 网络：需联网（地图加载、数据同步、社区互动依赖网络）；
- 权限：需授予「位置权限」（地图定位/路径规划）、「存储权限」（可选，缓存行程数据）。

## 测试
### 测试类型
项目覆盖以下测试维度，验证核心功能稳定性：
1. **单元测试**：验证业务逻辑层核心方法（如行程管理、协作监听），路径：`app/src/test`；
2. **UI测试**：验证页面交互逻辑（如行程创建、协作码验证），路径：`app/src/androidTest`；
3. **集成测试**：验证网络请求、MongoDB数据同步、地图SDK调用等端到端流程。

### 测试环境
- 测试设备：Android 7.0+ 模拟器/真机；
- 依赖：MongoDB测试库（含测试数据）、高德地图测试API Key；
- 网络：确保测试设备可访问MongoDB服务和高德地图API。

### 执行测试
```bash
# 执行单元测试
./gradlew testDebug

# 执行UI测试（需连接设备/模拟器）
./gradlew connectedAndroidTest
```

### 测试报告
测试完成后，报告生成路径：
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/`
- UI测试报告：`app/build/reports/androidTests/connected/`

## 致谢（参考项目/第三方库）
感谢以下开源项目/第三方服务为本项目提供技术支撑：
- [高德地图Android SDK](https://lbs.amap.com/api/android-sdk/summary/)：地图定位、POI搜索、路径规划核心能力；
- [OkHttp](https://square.github.io/okhttp/)：高效的HTTP网络请求框架；
- [Retrofit](https://square.github.io/retrofit/)：类型安全的HTTP客户端封装；
- [Gson](https://github.com/google/gson)：JSON数据序列化/反序列化；
- [MongoDB Java Driver](https://mongodb.github.io/mongo-java-driver/)：MongoDB数据库交互；
- [Android Jetpack](https://developer.android.com/jetpack)：UI组件、生命周期管理基础能力；
- [RecyclerView](https://developer.android.com/guide/topics/ui/layout/recyclerview)：行程地点/社区帖子列表展示。

## 开发成员与联系方式
| 姓名   | 核心职责                 | 联系方式                | GitHub                  |
|--------|--------------------------|-------------------------|-------------------------|
| 王烁烨   | AI推荐      | 2806097558@qq.com    | https://github.com/Forkdever |
| 石林峰   | 云服务器搭建     | -      | - |
| 谷励   | UI设计        | -     | - |
| 王思懿   | 地图规划      | -        | - |
| 邓怡馨   |  数据库搭建     | -        | - |

> 如需技术交流、问题反馈或二次开发合作，可通过上述邮箱联系，或在仓库提交Issue。

### 备注
- 本项目遵循Apache 2.0开源协议，如需二次开发/商用，请遵守相关第三方库的开源协议；
- 运行前需替换所有「占位符」（如GitHub仓库地址、API Key、MongoDB连接信息），否则功能无法正常使用。
