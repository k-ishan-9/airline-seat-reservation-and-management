package Arms_Load;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;

import static java.lang.Integer.parseInt;

public class LoadCSV {

    public static void load_Login() throws SQLException, ClassNotFoundException, IOException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url, uname, pass);
        con.setAutoCommit(false);

        String query1 = "create table if not exists login(userid varchar(20) , password varchar(20) , tickets_purchased int , amount_paid int)";
        PreparedStatement st1 = con.prepareStatement(query1);
        st1.executeUpdate();
        st1.close();

        String filepath = "E:\\Projects\\ARMS\\ARMS\\src\\Arms_Load\\login.csv";
        int batch = 20;
        String queryCheck = "SELECT userid FROM login WHERE userid = ?";
        String queryInsert = "INSERT INTO login (userid, password, tickets_purchased, amount_paid) VALUES (?, ?, ?, ?)";
        PreparedStatement checkStmt = con.prepareStatement(queryCheck);
        PreparedStatement insertStmt = con.prepareStatement(queryInsert);

        BufferedReader line = new BufferedReader(new FileReader(filepath));
        String lineText = null;

        int count = 0;

        line.readLine(); // Skip header
        while ((lineText = line.readLine()) != null) {
            String[] data = lineText.split(",");

            String userid = data[0];
            String password = data[1];
            String tickets = data[2];
            String amount = data[3];

            // Check if userid already exists
            checkStmt.setString(1, userid);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) { // userid does not exist, insert the record
                insertStmt.setString(1, userid);
                insertStmt.setString(2, password);
                insertStmt.setInt(3, parseInt(tickets));
                insertStmt.setInt(4, parseInt(amount));

                insertStmt.addBatch();

                if (++count % batch == 0) {
                    insertStmt.executeBatch();
                }
            }

            rs.close();
        }

        line.close();
        insertStmt.executeBatch();
        con.commit();

        checkStmt.close();
        insertStmt.close();
        con.close();
    }

    public static void load_Flight_Search() throws SQLException, IOException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url, uname, pass);
        con.setAutoCommit(false);

        String query1 = "create table if not exists flight_search(Flight_ID int, Boarding varchar(30), Destination varchar(30), Date varchar(30), Time varchar(30))";
        PreparedStatement st1 = con.prepareStatement(query1);
        st1.executeUpdate();
        st1.close();

        String filepath = "E:\\Projects\\ARMS\\ARMS\\src\\Arms_Load\\flight_search.csv";
        int batch = 20;
        String queryCheck = "SELECT Flight_ID FROM flight_search WHERE Flight_ID = ?";
        String queryInsert = "INSERT INTO flight_search (Flight_ID, Boarding, Destination, Date, Time) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement checkStmt = con.prepareStatement(queryCheck);
        PreparedStatement insertStmt = con.prepareStatement(queryInsert);

        BufferedReader line = new BufferedReader(new FileReader(filepath));
        String lineText = null;

        int count = 0;

        line.readLine(); // Skip header
        while ((lineText = line.readLine()) != null) {
            if (lineText.trim().isEmpty()) continue;
            String[] data = lineText.split(",");
            if (data.length < 5) {
                // System.err.println("Skipping incomplete line: " + lineText);
                continue;
            }
            int Flight_ID = Integer.parseInt(data[0].trim());
            String Boarding = data[1].trim();
            String Destination = data[2].trim();
            String Date = data[3].trim();
            String Time = data[4].trim();

            // Check if Flight_ID already exists
            checkStmt.setInt(1, Flight_ID);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) { // Flight_ID does not exist, insert the record
                insertStmt.setInt(1, Flight_ID);
                insertStmt.setString(2, Boarding);
                insertStmt.setString(3, Destination);
                insertStmt.setString(4, Date);
                insertStmt.setString(5, Time);

                insertStmt.addBatch();

                if (++count % batch == 0) {
                    insertStmt.executeBatch();
                }
            }

            rs.close();
        }

        line.close();
        insertStmt.executeBatch();
        con.commit();

        checkStmt.close();
        insertStmt.close();
        con.close();
    }

    public static void load_class() throws SQLException, IOException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url, uname, pass);
        con.setAutoCommit(false);

        String query1 = "create table if not exists class(Flight_ID int ,Economy_Class int , Bussiness_Class int ,First_Class int ,Eco_count int,Buss_count int,First_count int)";
        PreparedStatement st1 = con.prepareStatement(query1);
        st1.executeUpdate();
        st1.close();

        String filepath = "E:\\Projects\\ARMS\\ARMS\\src\\Arms_Load\\class.csv";
        int batch = 20;
        String queryCheck = "SELECT Flight_ID FROM class WHERE Flight_ID = ?";
        String queryInsert = "INSERT INTO class (Flight_ID, Economy_Class, Bussiness_Class, First_Class, Eco_count, Buss_count, First_count) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement checkStmt = con.prepareStatement(queryCheck);
        PreparedStatement insertStmt = con.prepareStatement(queryInsert);

        BufferedReader line = new BufferedReader(new FileReader(filepath));
        String lineText = null;

        int count = 0;

        line.readLine(); // Skip header
        while ((lineText = line.readLine()) != null) {
            String[] data = lineText.split(",");

            int Flight_ID = Integer.parseInt(data[0].trim());
            int Economy_Class = Integer.parseInt(data[1].trim());
            int Bussiness_Class = Integer.parseInt(data[2].trim());
            int First_Class = Integer.parseInt(data[3].trim());
            int Eco_count = Integer.parseInt(data[4].trim());
            int Buss_count = Integer.parseInt(data[5].trim());
            int First_count = Integer.parseInt(data[6].trim());

            // Check if Flight_ID already exists
            checkStmt.setInt(1, Flight_ID);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) { // Flight_ID does not exist, insert the record
                insertStmt.setInt(1, Flight_ID);
                insertStmt.setInt(2, Economy_Class);
                insertStmt.setInt(3, Bussiness_Class);
                insertStmt.setInt(4, First_Class);
                insertStmt.setInt(5, Eco_count);
                insertStmt.setInt(6, Buss_count);
                insertStmt.setInt(7, First_count);

                insertStmt.addBatch();

                if (++count % batch == 0) {
                    insertStmt.executeBatch();
                }
            }

            rs.close();
        }

        line.close();
        insertStmt.executeBatch();
        con.commit();

        checkStmt.close();
        insertStmt.close();
        con.close();
    }

    public static void load_debit() throws SQLException, ClassNotFoundException, IOException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url, uname, pass);
        con.setAutoCommit(false);

        String query1 = "create table if not exists debit(account_no int , password varchar(20))";
        PreparedStatement st1 = con.prepareStatement(query1);
        st1.executeUpdate();
        st1.close();

        String filepath = "E:\\Projects\\ARMS\\ARMS\\src\\Arms_Load\\debit.csv";
        int batch = 20;
        String queryCheck = "SELECT account_no FROM debit WHERE account_no = ?";
        String queryInsert = "INSERT INTO debit (account_no, password) VALUES (?, ?)";
        PreparedStatement checkStmt = con.prepareStatement(queryCheck);
        PreparedStatement insertStmt = con.prepareStatement(queryInsert);

        BufferedReader line = new BufferedReader(new FileReader(filepath));
        String lineText = null;

        int count = 0;

        line.readLine(); // Skip header
        while ((lineText = line.readLine()) != null) {
            String[] data = lineText.split(",");

            int account_no = Integer.parseInt(data[0].trim());
            String password = data[1].trim();

            // Check if account_no already exists
            checkStmt.setInt(1, account_no);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) { // account_no does not exist, insert the record
                insertStmt.setInt(1, account_no);
                insertStmt.setString(2, password);

                insertStmt.addBatch();

                if (++count % batch == 0) {
                    insertStmt.executeBatch();
                }
            }

            rs.close();
        }

        line.close();
        insertStmt.executeBatch();
        con.commit();

        checkStmt.close();
        insertStmt.close();
        con.close();
    }

    public static void load_credit() throws IOException, SQLException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url, uname, pass);
        con.setAutoCommit(false);

        String query1 = "create table if not exists credit(account_no int , password varchar(20))";
        PreparedStatement st1 = con.prepareStatement(query1);
        st1.executeUpdate();
        st1.close();

        String filepath = "E:\\Projects\\ARMS\\ARMS\\src\\Arms_Load\\credit.csv";
        int batch = 20;
        String queryCheck = "SELECT account_no FROM credit WHERE account_no = ?";
        String queryInsert = "INSERT INTO credit (account_no, password) VALUES (?, ?)";
        PreparedStatement checkStmt = con.prepareStatement(queryCheck);
        PreparedStatement insertStmt = con.prepareStatement(queryInsert);

        BufferedReader line = new BufferedReader(new FileReader(filepath));
        String lineText = null;

        int count = 0;

        line.readLine(); // Skip header
        while ((lineText = line.readLine()) != null) {
            String[] data = lineText.split(",");

            int account_no = Integer.parseInt(data[0].trim());
            String password = data[1].trim();

            // Check if account_no already exists
            checkStmt.setInt(1, account_no);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) { // account_no does not exist, insert the record
                insertStmt.setInt(1, account_no);
                insertStmt.setString(2, password);

                insertStmt.addBatch();

                if (++count % batch == 0) {
                    insertStmt.executeBatch();
                }
            }

            rs.close();
        }

        line.close();
        insertStmt.executeBatch();
        con.commit();

        checkStmt.close();
        insertStmt.close();
        con.close();
    }

    public static void load_upi() throws SQLException, IOException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url, uname, pass);
        con.setAutoCommit(false);

        String query1 = "create table if not exists upi(upi_id varchar(20),upi_pin int)";
        PreparedStatement st1 = con.prepareStatement(query1);
        st1.executeUpdate();
        st1.close();

        String filepath = "E:\\Projects\\ARMS\\ARMS\\src\\Arms_Load\\upi.csv";
        int batch = 20;
        String queryCheck = "SELECT upi_id FROM upi WHERE upi_id = ?";
        String queryInsert = "INSERT INTO upi (upi_id, upi_pin) VALUES (?, ?)";
        PreparedStatement checkStmt = con.prepareStatement(queryCheck);
        PreparedStatement insertStmt = con.prepareStatement(queryInsert);

        BufferedReader line = new BufferedReader(new FileReader(filepath));
        String lineText = null;

        int count = 0;

        line.readLine(); // Skip header
        while ((lineText = line.readLine()) != null) {
            String[] data = lineText.split(",");

            String upi_id = data[0].trim();
            int upi_pin = Integer.parseInt(data[1].trim());

            // Check if upi_id already exists
            checkStmt.setString(1, upi_id);
            ResultSet rs = checkStmt.executeQuery();

            if (!rs.next()) { // upi_id does not exist, insert the record
                insertStmt.setString(1, upi_id);
                insertStmt.setInt(2, upi_pin);

                insertStmt.addBatch();

                if (++count % batch == 0) {
                    insertStmt.executeBatch();
                }
            }

            rs.close();
        }

        line.close();
        insertStmt.executeBatch();
        con.commit();

        checkStmt.close();
        insertStmt.close();
        con.close();
    }

    public static void load() throws SQLException, IOException, ClassNotFoundException {
        LoadCSV.load_Login();
        LoadCSV.load_Flight_Search();
        LoadCSV.load_class();
        LoadCSV.load_debit();
        LoadCSV.load_credit();
        LoadCSV.load_upi();
    }
}
