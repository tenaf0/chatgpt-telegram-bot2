package hu.garaba.db;

import hu.garaba.gpt.GPTUsage;
import hu.garaba.gpt.Model;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                String month = formatToMonth(LocalDate.now());
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

    private static String formatToMonth(LocalDate date) {
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private static LocalDate parseLocalDate(String str) {
        Pattern date = Pattern.compile("(\\d\\d\\d\\d)-(\\d\\d)");
        Matcher matcher = date.matcher(str);
        boolean found = matcher.find();
        if (!found) {
            throw new IllegalArgumentException(String.format("String '%s' can't be parsed as a date.", str));
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));

        return LocalDate.of(year, month, 1);
    }

    private static boolean isSameMonth(LocalDate date1, LocalDate date2) {
        return date1.getYear() == date2.getYear() && date1.getMonth() == date2.getMonth();
    }

    public String queryUnpaidUsage(long userId) throws SQLException {
        LocalDate lastPaidMonth = queryLastPaidMonth(userId);
        try (var st = connection.prepareStatement("""
            SELECT month, type, model, count FROM USAGE
            WHERE user_id = ? AND month > ?
            ORDER BY month
            """)) {
            st.setLong(1, userId);
            st.setString(2, formatToMonth(lastPaidMonth));

            ResultSet rs = st.executeQuery();

            StringBuilder sb = new StringBuilder();
            if (lastPaidMonth.equals(LocalDate.MIN)) {
                sb.append("Your usage data:\n");
            } else {
                sb.append("Your last payment was for month ")
                        .append(UserDatabase.formatToMonth(lastPaidMonth))
                        .append(".\n");
            }

            sb.append('\n');
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal monthlySum = BigDecimal.ZERO;

            sb.append("Month | Type | Model | Token count | Price\n");

            boolean firstRow = true;
            LocalDate currentMonth = lastPaidMonth.plusMonths(1);
            Map<ModelType, BigDecimal> costMap;

            while (rs.next()) {
                LocalDate month = parseLocalDate(rs.getString("month"));
                if (!firstRow && !isSameMonth(currentMonth, month)) {
                    sb.append("Monthly sum: $")
                            .append(monthlySum.setScale(2, RoundingMode.HALF_UP).toPlainString())
                            .append("\n\n");

                    sum = sum.add(monthlySum);
                    monthlySum = BigDecimal.ZERO;
                }
                firstRow = false;
                currentMonth = month;
                costMap = queryCosts(currentMonth);

                GPTUsage type = GPTUsage.valueOf(rs.getString("type"));
                String model = rs.getString("model");
                long count = rs.getLong("count");

                sb.append(month)
                        .append(" | ")
                        .append(type)
                        .append(" | ")
                        .append(model)
                        .append(" | ")
                        .append(count)
                        .append(" | ");

                BigDecimal cost = costMap.get(new ModelType(type, model));
                if (cost != null) {
                    BigDecimal price = cost.multiply(BigDecimal.valueOf(count));
                    sb.append("$").append(price.setScale(2, RoundingMode.HALF_UP).toPlainString());

                    monthlySum = monthlySum.add(price);
                }
                sb.append('\n');
            }

            sb.append("Monthly sum: $")
                    .append(monthlySum.setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append("\n\n");

            sum = sum.add(monthlySum);

            sb.append('\n')
                    .append("Total price: $").append(sum.setScale(2, RoundingMode.HALF_UP).toPlainString());

            return sb.toString();
        }
    }

    public record ModelType(GPTUsage type, String model) {}

    public Map<ModelType, BigDecimal> queryCosts(LocalDate atDate) throws SQLException {
        try (var st = connection.prepareStatement("""
                SELECT type, model, price, exp FROM costs NATURAL JOIN
                (SELECT type, model, MAX(month) AS mmonth
                            FROM costs
                            WHERE month <= ?
                            GROUP BY type, model) m WHERE costs.month = m.mmonth
            """)) {
            st.setString(1, formatToMonth(atDate));

            ResultSet rs = st.executeQuery();

            Map<ModelType, BigDecimal> result = new HashMap<>();
            while (rs.next()) {
                BigDecimal price = BigDecimal.valueOf(rs.getLong("price"));
                int exp = rs.getInt("exp");
                result.put(new ModelType(
                        GPTUsage.valueOf(rs.getString("type")),
                        rs.getString("model")),
                        exp > 0 ? price.multiply(BigDecimal.TEN.pow(exp)) : price.divide(BigDecimal.TEN.pow(-exp))
                        );
            }

            return result;
        }
    }

    public LocalDate queryLastPaidMonth(long userId) throws SQLException {
        try (var st = connection.prepareStatement("""
                SELECT MAX(month) FROM invoice WHERE user_id = ? GROUP BY user_id
            """)) {
            st.setLong(1, userId);

            ResultSet rs = st.executeQuery();

            boolean hasRow = rs.next();

            if (hasRow) {
                String str = rs.getString(1);
                return parseLocalDate(str);
            } else {
                return LocalDate.MIN;
            }
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
