package pharmacysalestracker;

import java.util.Scanner;
import connector.DbConnection;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

public class PharmacySalesTracker {

    private static final Scanner sc = new Scanner(System.in);
    private static final DbConnection dbConnect = new DbConnection();
    private Map<String, Object> currentUser = null;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {

        if (DbConnection.connectDB() == null) {
            System.out.println("Database connection failed. Exiting.");
            return;
        }

        PharmacySalesTracker app = new PharmacySalesTracker();
        int action = 0;

        do{
            if (app.currentUser == null) {
                System.out.println("\n===== PHARMACY SALES TRACKER SYSTEM =====");
                System.out.println("1. LOGIN");
                System.out.println("2. REGISTER");
                System.out.println("3. EXIT");
            } else {
                String role = (String) app.currentUser.get("U_role");
                if ("Admin".equalsIgnoreCase(role)) {
                    app.adminDashboard();
                } else {
                    app.userDashboard();
                }
                continue;
            }

            System.out.print("Enter Action: ");

            if (!sc.hasNextInt()) {
                sc.nextLine();
                System.out.println("Invalid input. Please enter a number.");
                continue;
            }

            action = sc.nextInt();
            sc.nextLine();

            switch(action){
                case 1:
                    app.loginUser();
                    break;
                case 2:
                    app.registerUser();
                    break;
                case 3:
                    System.out.println("Exiting application.");
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }

            if (app.currentUser == null && action != 3) {
                System.out.print("Continue to Main Menu? (yes/no): ");
                if (!sc.nextLine().equalsIgnoreCase("yes")) action = 3;
            } else if (app.currentUser != null) {
                action = 0;
            }

        }while(action != 3);

        System.out.println("Thank You!");
        sc.close();
    }

    private long countAdmins() {
        String qry = "SELECT COUNT(U_id) FROM tbl_users WHERE U_role = 'Admin'";
        Object result = dbConnect.getSingleValue(qry);
        if (result instanceof Number) {
            return ((Number) result).longValue();
        }
        return 0;
    }

    public void registerUser() {
        System.out.println("\n--- USER REGISTRATION ---");
        System.out.print("Username: ");
        String username = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();

        String role;
        String approvalStatus;

        if (countAdmins() == 0) {
            role = "Admin";
            approvalStatus = "Approved";
            System.out.println("\nSTATUS: First registration detected. User set as Admin (Approved).");
        } else {
            role = "User";
            approvalStatus = "Pending";
            System.out.println("\nSTATUS: Admin already exists. User set as User (Pending).");
        }

        String hashedPassword = DbConnection.hashPassword(password);
        if (hashedPassword == null) {
            System.out.println("Registration failed due to hashing error.");
            return;
        }

        String sql = "INSERT INTO tbl_users (U_username, U_password, U_role, U_approval_status) VALUES (?, ?, ?, ?)";
        int newId = dbConnect.addRecordAndReturnId(sql, username, hashedPassword, role, approvalStatus);

        if (newId > 0) {
            System.out.println("Registration successful. User ID: " + newId);
        } else {
             System.out.println("Registration failed. User may already exist.");
        }
    }

    public void loginUser() {
        System.out.println("\n--- USER LOGIN ---");
        System.out.print("Username: ");
        String username = sc.nextLine();
        System.out.print("Password: ");
        String password = sc.nextLine();

        String hashedPassword = DbConnection.hashPassword(password);
        if (hashedPassword == null) {
            System.out.println("Login failed due to hashing error.");
            return;
        }

        String qry = "SELECT U_id, U_username, U_role, U_approval_status FROM tbl_users WHERE U_username = ? AND U_password = ?";
        List<Map<String, Object>> records = dbConnect.fetchRecords(qry, username, hashedPassword);

        if (records.isEmpty()) {
            System.out.println("Invalid Credentials. Please try again.");
            return;
        }

        currentUser = records.get(0);
        String role = (String) currentUser.get("U_role");
        String approvalStatus = (String) currentUser.get("U_approval_status");

        if ("Admin".equalsIgnoreCase(role) && !"Approved".equalsIgnoreCase(approvalStatus)) {
            System.out.println("Admin account is registered but requires approval before logging in.");
            currentUser = null;
            return;
        }

        System.out.println("Login Successful as " + role + ".");
        if ("User".equalsIgnoreCase(role) && "Pending".equalsIgnoreCase(approvalStatus)) {
            System.out.println("NOTE: Your account is pending Admin promotion approval.");
        }
    }

    private void adminDashboard() {
        int action = 0;
        int userId = (Integer) currentUser.get("U_id");
        do{
            System.out.println("\n===== ADMIN DASHBOARD =====");
            System.out.println("Logged in as: " + currentUser.get("U_username") + " (Admin)");
            System.out.println("1. MANAGE PRODUCTS");
            System.out.println("2. RECORD NEW SALE");
            System.out.println("3. VIEW SALES REPORTS (ALL)");
            System.out.println("4. MANAGE USER PROMOTION");
            System.out.println("5. LOGOUT");

            System.out.print("Enter Action: ");

            if (!sc.hasNextInt()) { sc.nextLine(); continue; }
            action = sc.nextInt(); sc.nextLine();

            switch(action){
                case 1:
                    productManagementMenu();
                    break;
                case 2:
                    recordNewSale(userId);
                    break;
                case 3:
                    salesReportingMenu();
                    break;
                case 4:
                    promoteUserToAdmin();
                    break;
                case 5:
                    System.out.println("Logging out...");
                    currentUser = null;
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }

            if (action != 5 && currentUser != null) {
                System.out.print("Continue in Admin Dashboard? (yes/no): ");
                if (!sc.nextLine().equalsIgnoreCase("yes")) action = 5;
            }

        }while(action != 5 && currentUser != null);
    }

    private void promoteUserToAdmin() {
        System.out.println("\n--- PROMOTE USER TO ADMIN ---");

        String qry = "SELECT U_id, U_username, U_role, U_approval_status FROM tbl_users WHERE U_role = 'User' AND U_approval_status = 'Pending'";
        String[] hdrs = {"ID", "Username", "Role", "Status"};
        String[] keys = {"U_id", "U_username", "U_role", "U_approval_status"};

        List<Map<String, Object>> pendingRecords = dbConnect.fetchRecords(qry);

        if (pendingRecords.isEmpty()) {
            System.out.println("No Users are currently pending promotion to Admin.");
            return;
        }

        System.out.println("Users Pending Admin Promotion:");
        dbConnect.viewRecords(qry, hdrs, keys);

        System.out.print("Enter User ID to PROMOTE (or 0 to cancel): ");
        if (!sc.hasNextInt()) { System.out.println("Invalid input. Operation cancelled."); sc.nextLine(); return; }
        int idToPromote = sc.nextInt(); sc.nextLine();

        if (idToPromote == 0) {
            System.out.println("Promotion cancelled.");
            return;
        }

        String updateQry = "UPDATE tbl_users SET U_role = 'Admin', U_approval_status = 'Approved' WHERE U_id = ? AND U_role = 'User' AND U_approval_status = 'Pending'";
        dbConnect.updateRecord(updateQry, idToPromote);
    }

    private void productManagementMenu() {
        int action = 0;
        do {
            System.out.println("\n--- PRODUCT MANAGEMENT ---");
            System.out.println("1. ADD New Product");
            System.out.println("2. VIEW All Products");
            System.out.println("3. UPDATE Product Details");
            System.out.println("4. DELETE Product");
            System.out.println("5. BACK TO DASHBOARD");

            System.out.print("Enter Action: ");
            if (!sc.hasNextInt()) { System.out.println("Invalid input."); sc.nextLine(); continue; }
            action = sc.nextInt(); sc.nextLine();

            switch (action) {
                case 1:
                    addProduct();
                    break;
                case 2:
                    viewProducts();
                    break;
                case 3:
                    updateProductDetails();
                    break;
                case 4:
                    deleteProduct();
                    break;
                case 5:
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }
            if (action != 5) {
                System.out.print("Continue in Product Management? (yes/no): ");
                if (!sc.nextLine().equalsIgnoreCase("yes")) action = 5;
            }
        } while (action != 5);
    }

    private void addProduct() {
        System.out.println("\n--- ADD NEW PRODUCT ---");
        System.out.print("Product Name: ");
        String name = sc.nextLine();

        double price;
        System.out.print("Unit Price: ");
        while (!sc.hasNextDouble()) { System.out.println("Invalid price. Enter a number:"); sc.nextLine(); System.out.print("Unit Price: "); }
        price = sc.nextDouble(); sc.nextLine();

        int qty;
        System.out.print("Initial Stock Quantity: ");
        while (!sc.hasNextInt()) { System.out.println("Invalid quantity. Enter a whole number:"); sc.nextLine(); System.out.print("Initial Stock Quantity: "); }
        qty = sc.nextInt(); sc.nextLine();

        System.out.print("Category: ");
        String category = sc.nextLine();

        System.out.print("Storage Location: ");
        String storage = sc.nextLine();

        String sql = "INSERT INTO tbl_products (P_name, P_unit_price, P_stock_qty, P_category, P_storage) VALUES (?, ?, ?, ?, ?)";
        dbConnect.addRecord(sql, name, price, qty, category, storage);
    }

    private void viewProducts() {
        System.out.println("\n--- CURRENT INVENTORY ---");
        String qry = "SELECT P_id, P_name, P_unit_price, P_stock_qty, P_category, P_storage FROM tbl_products ORDER BY P_id";
        String[] headers = {"ID", "Name", "Unit Price", "Stock Qty", "Category", "Storage"};
        String[] columns = {"P_id", "P_name", "P_unit_price", "P_stock_qty", "P_category", "P_storage"};

        dbConnect.viewRecords(qry, headers, columns);
    }

    private void updateProductDetails() {
        viewProducts();
        System.out.println("\n--- UPDATE PRODUCT ---");
        System.out.print("Enter Product ID to update: ");
        if (!sc.hasNextInt()) { System.out.println("Invalid ID."); sc.nextLine(); return; }
        int id = sc.nextInt(); sc.nextLine();

        double nPrice = 0.0;
        System.out.print("Enter new Unit Price (0 or negative to skip price update): ");
        if (sc.hasNextDouble()) { nPrice = sc.nextDouble(); } sc.nextLine();

        int nQtyChange = 0;
        System.out.print("Enter quantity change (+ for add, - for remove) (0 to skip qty update): ");
        if (sc.hasNextInt()) { nQtyChange = sc.nextInt(); } sc.nextLine();

        System.out.print("Enter new Storage Location (Enter '-' to skip): ");
        String nStorage = sc.nextLine();

        String sql = "UPDATE tbl_products SET P_unit_price = CASE WHEN ? > 0 THEN ? ELSE P_unit_price END, " +
                     "P_stock_qty = P_stock_qty + ?, " +
                     "P_storage = CASE WHEN ? != '-' THEN ? ELSE P_storage END WHERE P_id = ?";

        dbConnect.updateRecord(sql, nPrice, nPrice, nQtyChange, nStorage, nStorage, id);
    }

    private void deleteProduct() {
        viewProducts();
        System.out.println("\n--- DELETE PRODUCT ---");
        System.out.print("Enter Product ID to delete: ");
        if (!sc.hasNextInt()) { System.out.println("Invalid ID."); sc.nextLine(); return; }
        int id = sc.nextInt(); sc.nextLine();

        System.out.print("Confirm deletion of ID " + id + "? (yes/no): ");
        if (sc.nextLine().equalsIgnoreCase("yes")) {
            String sql = "DELETE FROM tbl_products WHERE P_id = ?";
            dbConnect.deleteRecord(sql, id);
        } else {
            System.out.println("Deletion cancelled.");
        }
    }

    private void salesReportingMenu() {
        int action = 0;
        do {
            System.out.println("\n--- SALES REPORTS (ALL) ---");
            System.out.println("1. View All Sales Summaries");
            System.out.println("2. View Detailed Sale Report by ID");
            System.out.println("3. BACK TO DASHBOARD");

            System.out.print("Enter Action: ");
            if (!sc.hasNextInt()) { System.out.println("Invalid input."); sc.nextLine(); continue; }
            action = sc.nextInt(); sc.nextLine();

            switch (action) {
                case 1:
                    viewSalesSummaries();
                    break;
                case 2:
                    viewDetailedSale();
                    break;
                case 3:
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }
            if (action != 3) {
                System.out.print("Continue in Sales Reports? (yes/no): ");
                if (!sc.nextLine().equalsIgnoreCase("yes")) action = 3;
            }
        } while (action != 3);
    }

    private void viewSalesSummaries() {
        System.out.println("\n--- ALL SALES SUMMARY ---");
        String qry = "SELECT S_id, S_date, S_total, U_username, S_storage_location FROM tbl_sales sh JOIN tbl_users u ON sh.U_cashier_id = u.U_id ORDER BY S_id DESC";
        String[] headers = {"Sale ID", "Date", "Total", "Cashier", "Location"};
        String[] columns = {"S_id", "S_date", "S_total", "U_username", "S_storage_location"};

        dbConnect.viewRecords(qry, headers, columns);
    }

    private void viewDetailedSale() {
        viewSalesSummaries();
        System.out.println("\n--- DETAILED SALE REPORT ---");
        System.out.print("Enter Sale ID: ");
        if (!sc.hasNextInt()) { System.out.println("Invalid ID."); sc.nextLine(); return; }
        int saleId = sc.nextInt(); sc.nextLine();

        String headerQry = "SELECT S_id, S_date, S_total, U_username, S_storage_location FROM tbl_sales sh JOIN tbl_users u ON sh.U_cashier_id = u.U_id WHERE S_id = ?";
        String[] headerHeaders = {"Sale ID", "Date", "Total", "Cashier", "Location"};
        String[] headerColumns = {"S_id", "S_date", "S_total", "U_username", "S_storage_location"};

        System.out.println("\n--- Transaction Header ---");
        dbConnect.viewRecords(headerQry, headerHeaders, headerColumns, saleId);

        String itemsQry = "SELECT si.SI_id, p.P_name, si.SI_quantity, p.P_unit_price, si.SI_subtotal, p.P_storage FROM tbl_sales_items si JOIN tbl_products p ON si.P_id = p.P_id WHERE si.S_id = ?";
        String[] itemHeaders = {"Item ID", "Product Name", "Qty", "Unit Price", "Subtotal", "Storage"};
        String[] itemColumns = {"SI_id", "P_name", "SI_quantity", "P_unit_price", "SI_subtotal", "P_storage"};

        System.out.println("\n--- Items Sold ---");
        dbConnect.viewRecords(itemsQry, itemHeaders, itemColumns, saleId);
    }

    private void userDashboard() {
        int action = 0;
        int userId = (Integer) currentUser.get("U_id");
        do {
            System.out.println("\n===== USER DASHBOARD =====");
            System.out.println("Logged in as: " + currentUser.get("U_username") + " (User)");
            System.out.println("1. MANAGE PRODUCTS");
            System.out.println("2. RECORD NEW SALE");
            System.out.println("3. VIEW OWN SALES REPORTS");
            System.out.println("4. LOGOUT");
            System.out.print("Enter Action: ");

            if (!sc.hasNextInt()) { sc.nextLine(); continue; }
            action = sc.nextInt(); sc.nextLine();

            switch(action){
                case 1:
                    productManagementMenu();
                    break;
                case 2:
                    recordNewSale(userId);
                    break;
                case 3:
                    viewOwnSalesSummaries(userId);
                    break;
                case 4:
                    System.out.println("Logging out...");
                    currentUser = null;
                    break;
                default:
                    System.out.println("Invalid option.");
                    break;
            }

            if (action != 4 && currentUser != null) {
                System.out.print("Continue in User Dashboard? (yes/no): ");
                if (!sc.nextLine().equalsIgnoreCase("yes")) action = 4;
            }
        } while (action != 4 && currentUser != null);
    }

    private void viewOwnSalesSummaries(int userId) {
        System.out.println("\n--- YOUR SALES SUMMARY ---");
        String qry = "SELECT S_id, S_date, S_total, U_username, S_storage_location FROM tbl_sales sh JOIN tbl_users u ON sh.U_cashier_id = u.U_id WHERE sh.U_cashier_id = ? ORDER BY S_id DESC";
        String[] headers = {"Sale ID", "Date", "Total", "Cashier", "Location"};
        String[] columns = {"S_id", "S_date", "S_total", "U_username", "S_storage_location"};

        dbConnect.viewRecords(qry, headers, columns, userId);
    }

    private Map<String, Object> getProductDetails(int prodId) {
        String qry = "SELECT P_unit_price, P_stock_qty, P_name FROM tbl_products WHERE P_id = ?";
        List<Map<String, Object>> records = dbConnect.fetchRecords(qry, prodId);
        return records.isEmpty() ? new HashMap<>() : records.get(0);
    }

    private void recordNewSale(int userId) {
        System.out.println("\n--- RECORD NEW SALE ---");
        System.out.print("Enter Sale Location: ");
        String saleLocation = sc.nextLine();
        String saleDate = LocalDateTime.now().format(DATE_FORMAT);

        String insertHeaderSql = "INSERT INTO tbl_sales (S_date, S_total, U_cashier_id, S_storage_location) VALUES (?, ?, ?, ?)";
        int salesId = dbConnect.addRecordAndReturnId(insertHeaderSql, saleDate, 0.0, userId, saleLocation);

        if (salesId < 0) {
            System.out.println("Failed to start new sale transaction.");
            return;
        }

        double saleTotal = 0.0;
        int prodId = 0;

        do {
            viewProducts();
            System.out.print("Enter Product ID (0 to finalize sale): ");
            if (!sc.hasNextInt()) {
                System.out.println("Invalid input. Please enter a number.");
                sc.nextLine();
                continue;
            }
            prodId = sc.nextInt(); sc.nextLine();

            if (prodId == 0) break;

            int qty = 0;
            System.out.print("Enter Quantity: ");
            if (!sc.hasNextInt()) {
                System.out.println("Invalid quantity. Please enter a whole number.");
                sc.nextLine();
                continue;
            }
            qty = sc.nextInt(); sc.nextLine();

            Map<String, Object> product = getProductDetails(prodId);

            if (product.isEmpty()) {
                System.out.println("Product not found."); continue;
            }

            double price = ((Number) product.get("P_unit_price")).doubleValue();
            int stock = ((Number) product.get("P_stock_qty")).intValue();
            String name = (String) product.get("P_name");

            if (qty <= 0) {
                System.out.println("Quantity must be greater than zero.");
                continue;
            }

            if (qty > stock) {
                System.out.println("Insufficient stock. Available: " + stock); continue;
            }

            double subtotal = price * qty;

            String insertItemSql = "INSERT INTO tbl_sales_items (S_id, P_id, SI_quantity, SI_subtotal) VALUES (?, ?, ?, ?)";
            dbConnect.addRecord(insertItemSql, salesId, prodId, qty, subtotal);

            String updateStockSql = "UPDATE tbl_products SET P_stock_qty = P_stock_qty - ? WHERE P_id = ?";
            dbConnect.updateRecord(updateStockSql, qty, prodId);

            saleTotal += subtotal;
            System.out.printf("Item '%s' added. Current Total: $%.2f\n", name, saleTotal);

        } while (prodId != 0);

        String updateHeaderSql = "UPDATE tbl_sales SET S_total = ? WHERE S_id = ?";
        dbConnect.updateRecord(updateHeaderSql, saleTotal, salesId);

        System.out.printf("\n*** SALE COMPLETED *** Transaction ID: %d, Final Total: $%.2f\n", salesId, saleTotal);
    }
}