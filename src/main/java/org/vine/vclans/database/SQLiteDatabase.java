package org.vine.vclans.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDatabase extends DatabaseManager {

    private final File dbFile;
    private Connection connection;

    public SQLiteDatabase(File dataFolder, String fileName) {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.dbFile = new File(dataFolder, fileName);
    }

    @Override
    public synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException ignored) {}

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA journal_mode = WAL;");
                s.execute("PRAGMA synchronous = NORMAL;");
                s.execute("PRAGMA foreign_keys = ON;");
                s.execute("PRAGMA busy_timeout = 5000;");
                s.execute("PRAGMA cache_size = -2000;"); // Limitar cache a 2MB de RAM
                s.execute("PRAGMA temp_store = MEMORY;");
                s.execute("PRAGMA mmap_size = 4194304;"); // 4MB mmap max
            }
        }

        // Retornar proxy que intercepta close() para reaproveitar a conexão física sem abrir/fechar conexões em loop
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

