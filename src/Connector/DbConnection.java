package connector;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class DbConnection {

    public static Connection connectDB() {
        Connection con = null;
        try {
            Class.forName("org.sqlite.JDBC");
            con = DriverManager.getConnection("jdbc:sqlite:pharmacy_sales.db");
            System.out.println("Connection Successful");
        } catch (Exception e) {
            System.out.println("Connection Failed: " + e);
        }
        return con;
    }

    private void setPreparedStatementValues(PreparedStatement pstmt, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            int paramIndex = i + 1;

            if (value instanceof Integer) {
                pstmt.setInt(paramIndex, (Integer) value);
            } else if (value instanceof Double) {
                pstmt.setDouble(paramIndex, (Double) value);
            } else if (value instanceof Float) {
                pstmt.setFloat(paramIndex, (Float) value);
            } else if (value instanceof Long) {
                pstmt.setLong(paramIndex, (Long) value);
            } else if (value instanceof Boolean) {
                pstmt.setBoolean(paramIndex, (Boolean) value);
            } else if (value instanceof java.util.Date) {
                pstmt.setDate(paramIndex, new java.sql.Date(((java.util.Date) value).getTime()));
            } else if (value instanceof java.sql.Date) {
                pstmt.setDate(paramIndex, (java.sql.Date) value);
            } else if (value instanceof java.sql.Timestamp) {
                pstmt.setTimestamp(paramIndex, (java.sql.Timestamp) value);
            } else {
                pstmt.setString(paramIndex, value == null ? null : value.toString());
            }
        }
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(password.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashedBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            System.out.println("Error hashing password: " + e.getMessage());
            return null;
        }
    }

    public void addRecord(String sql, Object... values) {
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setPreparedStatementValues(pstmt, values);
            pstmt.executeUpdate();
            System.out.println("Record added successfully!");
        } catch (SQLException e) {
            System.out.println("Error adding record: " + e.getMessage());
        }
    }

    public int addRecordAndReturnId(String query, Object... params) {
        int generatedId = -1;
        try (Connection conn = connectDB();
             PreparedStatement pstmt = conn.prepareStatement(query, PreparedStatement.RETURN_GENERATED_KEYS)) {

            setPreparedStatementValues(pstmt, params);

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        generatedId = rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error inserting record: " + e.getMessage());
        }
        return generatedId;
    }

    public void updateRecord(String sql, Object... values) {
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setPreparedStatementValues(pstmt, values);
            pstmt.executeUpdate();
            System.out.println("Record updated successfully!");
        } catch (SQLException e) {
            System.out.println("Error updating record: " + e.getMessage());
        }
    }

    public void deleteRecord(String sql, Object... values) {
        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) values[i]);
                } else {
                    pstmt.setString(i + 1, values[i].toString());
                }
            }

            pstmt.executeUpdate();
            System.out.println("Record deleted successfully!");
        } catch (SQLException e) {
            System.out.println("Error deleting record: " + e.getMessage());
        }
    }

    public Object getSingleValue(String sql, Object... params) {
        Object value = null;
        try (Connection conn = connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            setPreparedStatementValues(pstmt, params);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    value = rs.getObject(1);
                }
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving single value: " + e.getMessage());
        }
        return value;
    }

    public List<Map<String, Object>> fetchRecords(String sqlQuery, Object... values) {
        List<Map<String, Object>> records = new ArrayList<>();

        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sqlQuery)) {

            for (int i = 0; i < values.length; i++) {
                pstmt.setObject(i + 1, values[i]);
            }

            ResultSet rs = pstmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    row.put(metaData.getColumnName(i), rs.getObject(i));
                }
                records.add(row);
            }

        } catch (SQLException e) {
            System.out.println("Error fetching records: " + e.getMessage());
        }

        return records;
    }

    public void viewRecords(String sqlQuery, String[] columnHeaders, String[] columnNames, Object... values) {
        if (columnHeaders.length != columnNames.length) {
            System.out.println("Error: Mismatch between column headers and column names.");
            return;
        }

        try (Connection conn = this.connectDB();
             PreparedStatement pstmt = conn.prepareStatement(sqlQuery)) {

            setPreparedStatementValues(pstmt, values);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                final int COL_WIDTH = 20;

                StringBuilder headerLine = new StringBuilder();

                int totalWidth = 0;
                for (String header : columnHeaders) {
                     int width = Math.max(COL_WIDTH, header.length() + 2);
                     totalWidth += width + 1;
                }
                totalWidth += 1;

                for (int i = 0; i < totalWidth; i++) { headerLine.append('-'); }
                String sepLine = headerLine.toString();

                System.out.println(sepLine);
                StringBuilder headerRow = new StringBuilder("|");
                for (String header : columnHeaders) {
                    int width = Math.max(COL_WIDTH, header.length() + 2);
                    String format = " %-" + (width - 2) + "s |";
                    headerRow.append(String.format(format, header));
                }
                System.out.println(headerRow.toString());
                System.out.println(sepLine);

                while (rs.next()) {
                    StringBuilder row = new StringBuilder("|");
                    for (int i = 0; i < columnNames.length; i++) {
                        String colName = columnNames[i];
                        String header = columnHeaders[i];
                        int width = Math.max(COL_WIDTH, header.length() + 2);
                        String format = " %-" + (width - 2) + "s |";

                        String value = rs.getString(colName);

                        if (colName.toLowerCase().contains("price") || colName.toLowerCase().contains("total")) {
                             try {
                                double price = Double.parseDouble(value);
                                value = String.format("$%.2f", price);
                            } catch (NumberFormatException e) {

                            }
                        }

                        row.append(String.format(format, value != null ? value : ""));
                    }
                    System.out.println(row.toString());
                }
                System.out.println(sepLine);
            }
        } catch (SQLException e) {
            System.out.println("Error retrieving records: " + e.getMessage());
        }
    }
}