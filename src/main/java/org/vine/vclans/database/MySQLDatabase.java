package org.vine.vclans.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLDatabase extends DatabaseManager {

    private final String url;
    private final String username;
    private final String password;
    private Connection connection;

    public MySQLDatabase(String host, int port, String database, String username, String password, boolean ssl) {
        this.url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true&useSSL=" + ssl + "&characterEncoding=utf8";
        this.username = username;
        this.password = password;
    }

    @Override
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                try {
                    Class.forName("com.mysql.jdbc.Driver");
                } catch (ClassNotFoundException ignored) {}
            }
            connection = DriverManager.getConnection(url, username, password);
        }

        return (Connection) java.lang.reflect.Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("close".equals(method.getName())) {
                        return null; // Manter conexão física viva
                    }
                    if ("isClosed".equals(method.getName())) {
                        return connection == null || connection.isClosed();
                    }
                    return method.invoke(connection, args);
                }
        );
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                connection = null;
            }
        }
    }
}

