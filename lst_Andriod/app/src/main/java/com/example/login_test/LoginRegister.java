package com.example.login_test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.example.lushutong.LoginStatusManager;
/**
 * 登录注册功能管理类
 * 负责处理用户相关的数据库操作
 */
public class LoginRegister {
    private Activity mActivity; // 持有Activity引用用于返回操作
    private Context context;
    private String currentUser;

    // 回调接口，用于UI更新
    public interface DatabaseOperationCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface DataQueryCallback {
        void onDataLoaded(List<UserAccount> userAccounts);
        void onError(String error);
    }

    public interface LoginCallback {
        void onLoginSuccess(String message);
        void onLoginFailure(String message);
        void onError(String error);
    }

    /**
     * 用户账户数据模型
     */
    public static class UserAccount {
        private String phone;
        private String passwordHash;
        private int userId;

        public UserAccount(String phone, String passwordHash, int userId) {
            this.phone = phone;
            this.passwordHash = passwordHash;
            this.userId = userId;
        }

        // Getters
        public String getPhone() { return phone; }
        public String getPasswordHash() { return passwordHash; }
        public int getUserId() { return userId; }

        @Override
        public String toString() {
            return "用户名: " + phone + "\n密码: " + passwordHash + "\n\n";
        }
    }

    // 构造方法接收Activity（关键：用于关闭页面返回）
    public LoginRegister(Activity activity) {
        this.mActivity = activity;
        this.context = activity;
        this.currentUser = "user"; // 默认用户
    }

    /**
     * 设置当前用户
     */
    public void setCurrentUser(String user) {
        this.currentUser = user;
    }

    /**
     * 获取当前用户
     */
    public String getCurrentUser() {
        return currentUser;
    }

    /**
     * 发送消息（注册用户）
     * 现在使用两个参数：手机号和密码
     */
    public void registerUser(String phone, String password, DatabaseOperationCallback callback) {
        new Thread(() -> {
            Connection localCon = null;
            PreparedStatement localStmt = null;

            try {
                // 输入验证
                if (phone == null || phone.trim().isEmpty()) {
                    if (callback != null) {
                        callback.onError("手机号不能为空");
                    }
                    return;
                }

                if (password == null || password.trim().isEmpty()) {
                    if (callback != null) {
                        callback.onError("密码不能为空");
                    }
                    return;
                }

                if (password.length() > 199) {
                    if (callback != null) {
                        callback.onError("密码长度超过限制");
                    }
                    return;
                }

                // 获取数据库连接（需确保MySQLConnections类存在）
                localCon = MySQLConnections.getConnection();

                if (localCon == null) {
                    if (callback != null) {
                        callback.onError("数据库连接失败");
                    }
                    return;
                }

                // 执行插入
                String sql = "INSERT INTO user_account(phone, password_hash) VALUES(?, ?)";
                localStmt = localCon.prepareStatement(sql);
                localStmt.setString(1, phone);
                localStmt.setString(2, password);

                int affectedRows = localStmt.executeUpdate();

                if (affectedRows > 0) {
                    if (callback != null) {
                        callback.onSuccess("注册成功!");
                    }
                } else {
                    if (callback != null) {
                        callback.onError("注册失败");
                    }
                }

            } catch (SQLException e) {
                if (callback != null) {
                    callback.onError("数据库错误: " + e.getMessage());
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("发生错误: " + e.getMessage());
                }
            } finally {
                // 清理资源
                try {
                    if (localStmt != null) localStmt.close();
                    if (localCon != null) localCon.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 用户登录验证
     * @param phone 手机号
     * @param password 密码
     * @param callback 登录结果回调
     */
    public void userLogin(String phone, String password, LoginCallback callback) {
        new Thread(() -> {
            Connection localCon = null;
            PreparedStatement localStmt = null;
            ResultSet localRs = null;

            try {
                // 输入验证
                if (phone == null || phone.trim().isEmpty()) {
                    if (callback != null) {
                        callback.onError("手机号不能为空");
                    }
                    return;
                }

                if (password == null || password.trim().isEmpty()) {
                    if (callback != null) {
                        callback.onError("密码不能为空");
                    }
                    return;
                }

                // 获取数据库连接
                localCon = MySQLConnections.getConnection();

                if (localCon == null) {
                    if (callback != null) {
                        callback.onError("数据库连接失败");
                    }
                    return;
                }

                // 查询用户是否存在且密码匹配
                String sql = "SELECT user_id, phone FROM user_account WHERE phone = ? AND password_hash = ?";
                localStmt = localCon.prepareStatement(sql);
                localStmt.setString(1, phone);
                localStmt.setString(2, password);

                localRs = localStmt.executeQuery();

                if (localRs.next()) {
                    // 找到匹配的用户
                    int userId = localRs.getInt("user_id");
                    String userPhone = localRs.getString("phone");
                    setCurrentUser(userPhone); // 登录成功，设置当前用户

                    // 标记登录状态（需确保LoginStatusManager类存在）
                    LoginStatusManager.setLoggedIn(true);

                    if (callback != null) {
                        callback.onLoginSuccess("登录成功！欢迎用户: " + userPhone);
                    }

                } else {
                    // 没有找到匹配的用户
                    if (callback != null) {
                        callback.onLoginFailure("登录失败：手机号或密码错误");
                    }
                }

            } catch (SQLException e) {
                if (callback != null) {
                    callback.onError("数据库错误: " + e.getMessage());
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("发生错误: " + e.getMessage());
                }
            } finally {
                // 清理资源
                try {
                    if (localRs != null) localRs.close();
                    if (localStmt != null) localStmt.close();
                    if (localCon != null) localCon.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * 读取所有用户数据
     */
    public void readAllUsers(DataQueryCallback callback) {
        new Thread(() -> {
            Connection localCon = null;
            PreparedStatement localStmt = null;
            ResultSet localRs = null;

            try {
                localCon = MySQLConnections.getConnection();

                if (localCon != null) {
                    String sql = "SELECT phone, password_hash, user_id FROM user_account";
                    localStmt = localCon.prepareStatement(sql);
                    localRs = localStmt.executeQuery();

                    List<UserAccount> userAccounts = new ArrayList<>();
                    while (localRs.next()) {
                        String phone = localRs.getString("phone");
                        String password = localRs.getString("password_hash");
                        int userId = localRs.getInt("user_id");
                        userAccounts.add(new UserAccount(phone, password, userId));
                    }

                    if (callback != null) {
                        callback.onDataLoaded(userAccounts);
                    }
                } else {
                    if (callback != null) {
                        callback.onError("数据库连接失败");
                    }
                }

            } catch (Exception e) {
                if (callback != null) {
                    callback.onError("读取数据失败: " + e.getMessage());
                }
            } finally {
                try {
                    if (localRs != null) localRs.close();
                    if (localStmt != null) localStmt.close();
                    if (localCon != null) localCon.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
}