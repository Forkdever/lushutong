package com.example.login_test;

import java.sql.Connection;
import java.sql.DriverManager;
import com.llw.newmapdemo.R;
public class MySQLConnections {
    private String driver = "";
    private String dbURL = "";
    private String user = "";
    private String password = "";
    private static MySQLConnections connection = null;
    private MySQLConnections() throws Exception {
        driver = "com.mysql.jdbc.Driver";
        dbURL = "jdbc:mysql://rm-2ze145k63zmue0ew2bo.mysql.rds.aliyuncs.com:3306/db_login";
        user = "dbuser";
        password = "Lushutong1!";
        System.out.println("dbURL:" + dbURL);
    }
    public static Connection getConnection() {
        Connection conn = null;
        if (connection == null) {
            try {
                connection = new MySQLConnections();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        try {
            Class.forName(connection.driver);
            conn = DriverManager.getConnection(connection.dbURL,
                    connection.user, connection.password);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return conn;
    }
}