package hu.garaba.db;

import hu.garaba.gpt.GPTUsage;
import hu.garaba.gpt.Model;
import jakarta.annotation.Nullable;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class UserDatabase {
    private static final System.Logger LOGGER = System.getLogger(UserDatabase.class.getCanonicalName());

    private final Connection connection;

    public UserDatabase(Path path) throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + path.toString());
    }

    public boolean isWhitelisted(long userId) {
        try (var st = connection.prepareStatement("SELECT 1 FROM USERS u WHERE user_id = ?")) {
            st.setLong(1, userId);

            ResultSet resultSet = st.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.DEBUG, e);

            return false;
        }
    }

    public boolean isAdmin(long userId) {
        try (var st = connection.prepareStatement("SELECT u.admin FROM USERS u WHERE user_id = ?")) {
            st.setLong(1, userId);

            ResultSet resultSet = st.executeQuery();
            boolean next = resultSet.next();
            if (next) {
                return resultSet.getBoolean(1);
            }
        } catch (SQLException e) {
            LOGGER.log(System.Logger.Level.DEBUG, e);
        }
        return false;
    }

    public void flushUsage(long userId, GPTUsage usageType, @Nullable Model model, long count) throws SQLException {
        try {
            connection.setAutoCommit(false);

            try (var st = connection.prepareStatement("SELECT count FROM USAGE u WHERE " +
                    "user_id = ? AND month = ? AND type = ? AND model = ?")) {
                st.setLong(1, userId);
                String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
                st.setString(2, month);
                st.setString(3, usageType.name());
                st.setString(4, model != null ? model.name() : null);

                ResultSet rs = st.executeQuery();
                if (rs.next()) {
                    long existingCount = rs.getLong(1);

                    try (var updateSt = connection.prepareStatement("UPDATE USAGE SET count = ? WHERE " +
                            "user_id = ? AND month = ? AND type = ? AND model = ?")) {
                        updateSt.setLong(1, existingCount + count);
                        updateSt.setLong(2, userId);
                        updateSt.setString(3, month);
                        updateSt.setString(4, usageType.name());
                        updateSt.setString(5, model != null ? model.name() : null);

                        int i = updateSt.executeUpdate();
                        if (i != 1) {
                            throw new IllegalStateException("Update of `count` column modified "
                                    + i + " rows instead of a single one!");
                        }
                    }
                } else {
                    try (var insertSt = connection.prepareStatement("INSERT INTO USAGE VALUES (?, ?, ?, ?, ?)")) {
                        insertSt.setLong(1, userId);
                        insertSt.setString(2, month);
                        insertSt.setString(3, usageType.name());
                        insertSt.setString(4, model != null ? model.name() : null);
                        insertSt.setLong(5, count);

                        insertSt.executeUpdate();
                    }
                }
            }
            connection.commit();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void addUser(long userId, String name) throws SQLException {
        try (var insertSt = connection.prepareStatement("INSERT INTO USERS (user_id, name) VALUES (?, ?)")) {
            insertSt.setLong(1, userId);
            insertSt.setString(2, name);

            insertSt.executeUpdate();
        }
    }
}
