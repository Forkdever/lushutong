package com.example.login_test;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.llw.newmapdemo.R;
import com.example.lushutong.MainActivity;

import java.util.List;

/**
 * 登录业务控制器类
 * 封装所有登录、注册相关的业务逻辑和UI交互
 * 与界面解耦，便于维护和复用
 */
public class LoginController {
    // 上下文对象，用于访问Android系统服务和资源
    private AppCompatActivity activity;
    // 登录注册功能管理类，处理数据存储和验证
    private LoginRegister loginRegister;

    // UI组件声明
    private TextView tvUserList;          // 显示用户列表的文本控件
    private EditText etPhoneNumber;       // 手机号输入框
    private EditText etPassword;          // 密码输入框
    private Button btnLogin;              // 登录按钮
    private Button btnRegister;           // 注册按钮

    // 线程控制变量
    private Thread autoRefreshThread;     // 自动刷新数据的后台线程
    private volatile boolean isRefreshing;// 线程运行状态标记，volatile保证多线程可见性

    /**
     * 构造方法，初始化控制器
     * @param activity 关联的Activity上下文，用于操作UI和获取资源
     */
    public LoginController(AppCompatActivity activity) {
        this.activity = activity;
        // 初始化登录注册功能类，传入Activity（关键：用于后续关闭页面）
        this.loginRegister = new LoginRegister(activity);
    }

    /**
     * 控制器初始化方法
     * 执行UI绑定、事件监听设置和自动刷新启动
     */
    public void setup() {
        // 绑定UI组件
        initViews();
        // 设置按钮点击事件监听
        setupClickListeners();
        // 启动用户数据自动刷新
        startAutoRefresh();
    }

    /**
     * 初始化UI组件
     * 绑定布局文件中的控件，并设置初始属性
     */
    private void initViews() {
        etPhoneNumber = activity.findViewById(R.id.reviseText);
        etPassword = activity.findViewById(R.id.msg);
        btnLogin = activity.findViewById(R.id.revise);
        btnRegister = activity.findViewById(R.id.button);

        // 设置输入框提示文本，提升用户体验
        etPhoneNumber.setHint("请输入手机号");
        etPassword.setHint("请输入密码");

        // 设置按钮显示文本
        btnLogin.setText("登录");
        btnRegister.setText("注册");
    }

    /**
     * 设置按钮点击事件监听
     * 为登录和注册按钮绑定点击处理逻辑
     */
    private void setupClickListeners() {
        // 登录按钮点击事件
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理登录逻辑
                handleLoginAction();
            }
        });

        // 注册按钮点击事件
        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 处理注册逻辑
                handleRegisterAction();
            }
        });
    }

    /**
     * 处理登录按钮点击事件
     * 包含输入验证和登录请求处理
     */
    private void handleLoginAction() {
        // 获取输入框内容并去除首尾空格
        String phone = etPhoneNumber.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 输入验证，若验证失败则直接返回
        if (!validateInput(phone, password)) return;

        // 调用登录注册管理类的登录方法
        loginRegister.userLogin(phone, password, new LoginRegister.LoginCallback() {
            /**
             * 登录成功回调
             * @param message 成功提示信息
             */
            @Override
            public void onLoginSuccess(String message) {
                // 在UI线程显示提示信息并关闭LoginActivity返回上一页
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showToast(message);
                        // 标记登录成功结果，关闭当前Activity返回MainActivity
                        activity.setResult(AppCompatActivity.RESULT_OK);
                        activity.finish(); // 核心：关闭当前页面返回上一级
                    }
                });
            }

            /**
             * 登录失败回调（如密码错误）
             * @param message 失败原因信息
             */
            @Override
            public void onLoginFailure(String message) {
                showToast(message);
            }

            /**
             * 登录错误回调（如数据库异常）
             * @param error 错误详细信息
             */
            @Override
            public void onError(String error) {
                showToast(error);
            }
        });
    }

    /**
     * 处理注册按钮点击事件
     * 包含输入验证和注册请求处理
     */
    private void handleRegisterAction() {
        // 获取输入框内容并去除首尾空格
        String phone = etPhoneNumber.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // 输入验证，若验证失败则直接返回
        if (!validateInput(phone, password)) return;

        // 调用登录注册管理类的注册方法
        loginRegister.registerUser(phone, password, new LoginRegister.DatabaseOperationCallback() {
            /**
             * 注册成功回调
             * @param message 成功提示信息
             */
            @Override
            public void onSuccess(String message) {
                showToast(message);
                // 注册成功后清空输入框
                clearInputFields();
            }

            /**
             * 注册错误回调
             * @param error 错误详细信息
             */
            @Override
            public void onError(String error) {
                showToast(error);
            }
        });
    }

    /**
     * 输入内容验证方法
     * 检查手机号和密码是否为空
     * @param phone 手机号输入内容
     * @param password 密码输入内容
     * @return 验证结果：true为通过，false为失败
     */
    private boolean validateInput(String phone, String password) {
        // 检查手机号是否为空
        if (phone.isEmpty()) {
            showToast("请输入手机号");
            return false;
        }
        // 检查密码是否为空
        if (password.isEmpty()) {
            showToast("请输入密码");
            return false;
        }
        // 所有验证通过
        return true;
    }

    /**
     * 启动用户数据自动刷新线程
     * 每2秒从数据库读取一次用户数据并更新UI
     */
    private void startAutoRefresh() {
        // 设置线程运行状态为true
        isRefreshing = true;
        // 创建后台线程
        autoRefreshThread = new Thread(new Runnable() {
            @Override
            public void run() {
                // 循环执行刷新操作，直到状态标记为false或线程被中断
                while (isRefreshing && !Thread.currentThread().isInterrupted()) {
                    // 从数据库读取所有用户数据
                    loginRegister.readAllUsers(new LoginRegister.DataQueryCallback() {
                        /**
                         * 数据加载成功回调
                         * @param userAccounts 用户账户列表
                         */
                        @Override
                        public void onDataLoaded(List<LoginRegister.UserAccount> userAccounts) {
                            // 在UI线程更新显示内容
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //updateUserDisplay(userAccounts);
                                }
                            });
                        }

                        /**
                         * 数据加载错误回调
                         * @param error 错误信息
                         */
                        @Override
                        public void onError(String error) {
                            // 静默处理错误，避免频繁弹窗影响用户体验
                        }
                    });

                    try {
                        // 线程休眠2秒（2000毫秒）
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        // 捕获中断异常，恢复线程中断状态
                        Thread.currentThread().interrupt();
                        // 退出循环，结束线程
                        break;
                    }
                }
            }
        });
        // 启动后台线程
        autoRefreshThread.start();
    }

    /**
     * 显示Toast提示信息
     * 封装Toast调用，简化代码
     * @param message 要显示的提示文本
     */
    private void showToast(String message) {
        // 检查当前线程是否是UI线程
        if (activity == null) return;

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 清空输入框内容
     * 用于注册成功后重置输入状态
     */
    private void clearInputFields() {
        etPhoneNumber.setText("");
        etPassword.setText("");
    }

    /**
     * 控制器销毁方法
     * 释放资源，停止后台线程
     */
    public void onDestroy() {
        // 设置线程运行状态为false
        isRefreshing = false;
        // 检查线程是否存在
        if (autoRefreshThread != null) {
            // 中断线程，确保线程退出
            autoRefreshThread.interrupt();
        }
    }

    // ------------------- 对外提供的接口方法 -------------------

    /**
     * 外部调用的登录方法
     * @param phone 手机号
     * @param password 密码
     * @param callback 登录结果回调
     */
    public void performLogin(String phone, String password, LoginRegister.LoginCallback callback) {
        loginRegister.userLogin(phone, password, callback);
    }

    /**
     * 外部调用的注册方法
     * @param phone 手机号
     * @param password 密码
     * @param callback 注册结果回调
     */
    public void performRegister(String phone, String password, LoginRegister.DatabaseOperationCallback callback) {
        loginRegister.registerUser(phone, password, callback);
    }

    /**
     * 外部调用的刷新用户数据方法
     * @param callback 数据加载回调
     */
    public void refreshUserData(LoginRegister.DataQueryCallback callback) {
        loginRegister.readAllUsers(callback);
    }
}