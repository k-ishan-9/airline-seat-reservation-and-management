package Arms;

import java.sql.*;
import java.util.Scanner;

public class Flight_Class extends Flight_Search {

    public static void Economy(int Flight_ID,String user_id) throws ClassNotFoundException, SQLException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);

        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection con = DriverManager.getConnection(url, uname, pass)) {
            String query4 = "SELECT Economy_Class, Eco_count FROM class WHERE Flight_ID = ?";
            try (PreparedStatement st4 = con.prepareStatement(query4)) {
                st4.setInt(1, Flight_ID);
                try (ResultSet rs4 = st4.executeQuery()) {
                    // Display Economy Class price if found
                    if (rs4.next()) {
                        System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        System.out.println("                                                                 Price of Economy Class : " + rs4.getInt("Economy_Class"));
                        System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    } else {
                        System.out.println("Economy Class details not found for Flight ID: " + Flight_ID);
                        Flight_Search.flight(user_id);
                        return;
                    }
                }
            }

            // Query to fetch available Economy Class seats for the specified Flight_ID
            String query = "SELECT Economy_Class,Eco_count FROM class WHERE Flight_ID = ?";
            try (PreparedStatement st = con.prepareStatement(query)) {
                st.setInt(1, Flight_ID);
                try (ResultSet rs = st.executeQuery()) {
                    // Display prompt to enter number of tickets
                    System.out.println("Enter number of tickets you want to buy:");
                    
                    if (rs.next()) {
                        int availableTickets = rs.getInt("Eco_count");
                        int m;
                        
                        // Validate user input for number of tickets
                        while (true) {
                            try {
                                m = Integer.parseInt(sin.nextLine().trim());
                                
                                if (m <= 0) {
                                    System.out.println("Please enter a valid number of tickets (greater than 0).");
                                    System.out.println("Enter number of tickets you want to buy:");
                                } else {
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Please enter a valid integer for number of tickets.");
                                System.out.println("Enter number of tickets you want to buy:");
                            }
                        }
                        
                        // Check if requested number of tickets is available
                        if (availableTickets >= m) {
                            int totalPrice = m * rs.getInt("Economy_Class");
                            System.out.println("Total amount to be paid (Economy Class) : " + totalPrice);
    
                            // Prompt user to proceed with payment
                            System.out.println("Do you want to make a payment [y/n] :");
                            String k = sin.next().trim();
                            
                            if (k.equalsIgnoreCase("y")) {
                                int paymentStatus = Payment.pay(user_id);
                                
                                if (paymentStatus == 1) {
                                    // Update the database with the purchased tickets
                                    String updateQuery = "UPDATE class SET Eco_count = Eco_count - ? WHERE Flight_ID = ?";
                                    
                                    try (PreparedStatement updateSt = con.prepareStatement(updateQuery)) {
                                        updateSt.setInt(1, m);
                                        updateSt.setInt(2, Flight_ID);
                                        int count = updateSt.executeUpdate();
                                        
                                        if (count > 0) {
                                            System.out.println("Tickets purchased successfully!");
                                          
                                           // Update the login table for the user
                                        String loginUpdateQuery = "UPDATE login SET tickets_purchased = tickets_purchased + ?, amount_paid = amount_paid + ? WHERE userid = ?";
                                        
                                        try (PreparedStatement loginUpdateSt = con.prepareStatement(loginUpdateQuery)) {
                                            loginUpdateSt.setInt(1, m);
                                            loginUpdateSt.setInt(2, totalPrice);
                                            loginUpdateSt.setString(3, user_id);
                                            int loginUpdateCount = loginUpdateSt.executeUpdate();
                                            
                                            if (loginUpdateCount > 0) {
                                                System.out.println("Login table updated successfully.");
                                            } else {
                                                System.out.println("Failed to update login table.");
                                            }
                                        }
                                        } else {
                                            System.out.println("Failed to update ticket count.");
                                        }
                                        
                                        Flight_Search.flight(user_id);
                                    }
                                } else {
                                    System.out.println("Tickets not purchased.");
                                    Flight_Search.flight(user_id);
                                }
                            } else {
                                System.out.println("Transaction cancelled.");
                                Flight_Search.flight(user_id);
                            }
                        } else {
                            System.out.println("Tickets are not available.");
                            Flight_Search.flight(user_id);
                        }
                    } else {
                        System.out.println("Flight details not found.");
                        Flight_Search.flight(user_id);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error connecting to database: " + e.getMessage());
        }
    }

    public static void Business(int Flight_ID,String user_id) throws SQLException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection con = DriverManager.getConnection(url, uname, pass)) {
            // Query to fetch Business Class price and available seats for the specified Flight_ID
            String query4 = "SELECT Bussiness_Class, Buss_count FROM class WHERE Flight_ID = ?";
            
            try (PreparedStatement st4 = con.prepareStatement(query4)) {
                st4.setInt(1, Flight_ID);
                
                try (ResultSet rs4 = st4.executeQuery()) {
                    // Display Business Class price if found
                    if (rs4.next()) {
                        System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        System.out.println("                                                                 Price of Business Class : " + rs4.getInt("Bussiness_Class"));
                        System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    } else {
                        System.out.println("Business Class details not found for Flight ID: " + Flight_ID);
                        Flight_Search.flight(user_id);
                        return;
                    }
                }
            }
    
            // Query to fetch available Business Class seats for the specified Flight_ID
            String query = "SELECT Bussiness_Class, Buss_count FROM class WHERE Flight_ID = ?";
            
            try (PreparedStatement st = con.prepareStatement(query)) {
                st.setInt(1, Flight_ID);
                
                try (ResultSet rs = st.executeQuery()) {
                    // Display prompt to enter number of tickets
                    System.out.println("Enter number of tickets you want to buy:");
                    
                    if (rs.next()) {
                        int availableTickets = rs.getInt("Buss_count");
                        int m;
                        
                        // Validate user input for number of tickets
                        while (true) {
                            try {
                                m = Integer.parseInt(sin.nextLine().trim());
                                
                                if (m <= 0) {
                                    System.out.println("Please enter a valid number of tickets (greater than 0).");
                                    System.out.println("Enter number of tickets you want to buy:");
                                } else {
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Please enter a valid integer for number of tickets.");
                                System.out.println("Enter number of tickets you want to buy:");
                            }
                        }
                        
                        // Check if requested number of tickets is available
                        if (availableTickets >= m) {
                            int totalPrice = m * rs.getInt("Bussiness_Class");
                            System.out.println("Total amount to be paid (Business Class) : " + totalPrice);
    
                            // Prompt user to proceed with payment
                            System.out.println("Do you want to make a payment [y/n] :");
                            String k = sin.next().trim();
                            
                            if (k.equalsIgnoreCase("y")) {
                                int paymentStatus = Payment.pay(user_id);
                                
                                if (paymentStatus == 1) {
                                    // Update the database with the purchased tickets
                                    String updateQuery = "UPDATE class SET Buss_count = Buss_count - ? WHERE Flight_ID = ?";
                                    
                                    try (PreparedStatement updateSt = con.prepareStatement(updateQuery)) {
                                        updateSt.setInt(1, m);
                                        updateSt.setInt(2, Flight_ID);
                                        int count = updateSt.executeUpdate();
                                        
                                        if (count > 0) {
                                            System.out.println("Tickets purchased successfully!");
                                                 // Update the login table for the user
                                        String loginUpdateQuery = "UPDATE login SET tickets_purchased = tickets_purchased + ?, amount_paid = amount_paid + ? WHERE userid = ?";
                                        
                                        try (PreparedStatement loginUpdateSt = con.prepareStatement(loginUpdateQuery)) {
                                            loginUpdateSt.setInt(1, m);
                                            loginUpdateSt.setInt(2, totalPrice);
                                            loginUpdateSt.setString(3, user_id);
                                            int loginUpdateCount = loginUpdateSt.executeUpdate();
                                            
                                            if (loginUpdateCount > 0) {
                                                System.out.println("Login table updated successfully.");
                                            } else {
                                                System.out.println("Failed to update login table.");
                                            }
                                        }
                                        } else {
                                            System.out.println("Failed to update ticket count.");
                                        }
                                        
                                        Flight_Search.flight(user_id);
                                    }
                                } else {
                                    System.out.println("Tickets not purchased.");
                                    Flight_Search.flight(user_id);
                                }
                            } else {
                                System.out.println("Transaction cancelled.");
                                Flight_Search.flight(user_id);
                            }
                        } else {
                            System.out.println("Tickets are not available.");
                            Flight_Search.flight(user_id);
                        }
                    } else {
                        System.out.println("Flight details not found.");
                        Flight_Search.flight(user_id);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error connecting to database: " + e.getMessage());
        }
    }

    public static void FirstClass(int Flight_ID,String user_id) throws ClassNotFoundException, SQLException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);

        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection con = DriverManager.getConnection(url, uname, pass)) {
            String query4 = "SELECT First_Class, First_count FROM class WHERE Flight_ID = ?";
            try (PreparedStatement st4 = con.prepareStatement(query4)) {
                st4.setInt(1, Flight_ID);
                try (ResultSet rs4 = st4.executeQuery()) {
                    // Display First Class price if found
                    if (rs4.next()) {
                        System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                        System.out.println("                                                                 Price of First Class : " + rs4.getInt("First_Class"));
                        System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                    } else {
                        System.out.println("First Class details not found for Flight ID: " + Flight_ID);
                        Flight_Search.flight(user_id);
                        return;
                    }
                }
            }

            // Query to fetch available First Class seats for the specified Flight_ID
            String query = "SELECT First_Class,First_count FROM class WHERE Flight_ID = ?";
            try (PreparedStatement st = con.prepareStatement(query)) {
                st.setInt(1, Flight_ID);
                try (ResultSet rs = st.executeQuery()) {
                    // Display prompt to enter number of tickets
                    System.out.println("Enter number of tickets you want to buy:");
                    
                    if (rs.next()) {
                        int availableTickets = rs.getInt("First_count");
                        int m;
                        
                        // Validate user input for number of tickets
                        while (true) {
                            try {
                                m = Integer.parseInt(sin.nextLine().trim());
                                
                                if (m <= 0) {
                                    System.out.println("Please enter a valid number of tickets (greater than 0).");
                                    System.out.println("Enter number of tickets you want to buy:");
                                } else {
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("Please enter a valid integer for number of tickets.");
                                System.out.println("Enter number of tickets you want to buy:");
                            }
                        }
                        
                        // Check if requested number of tickets is available
                        if (availableTickets >= m) {
                            int totalPrice = m * rs.getInt("First_Class");
                            System.out.println("Total amount to be paid (First Class) : " + totalPrice);

                            // Prompt user to proceed with payment
                            System.out.println("Do you want to make a payment [y/n] :");
                            String k = sin.next().trim();
                            
                            if (k.equalsIgnoreCase("y")) {
                                int paymentStatus = Payment.pay(user_id);
                                
                                if (paymentStatus == 1) {
                                    // Update the database with the purchased tickets
                                    String updateQuery = "UPDATE class SET First_count = First_count - ? WHERE Flight_ID = ?";
                                    
                                    try (PreparedStatement updateSt = con.prepareStatement(updateQuery)) {
                                        updateSt.setInt(1, m);
                                        updateSt.setInt(2, Flight_ID);
                                        int count = updateSt.executeUpdate();
                                        
                                        if (count > 0) {
                                            System.out.println("Tickets purchased successfully!");
                                                 // Update the login table for the user
                                        String loginUpdateQuery = "UPDATE login SET tickets_purchased = tickets_purchased + ?, amount_paid = amount_paid + ? WHERE userid = ?";
                                        
                                        try (PreparedStatement loginUpdateSt = con.prepareStatement(loginUpdateQuery)) {
                                            loginUpdateSt.setInt(1, m);
                                            loginUpdateSt.setInt(2, totalPrice);
                                            loginUpdateSt.setString(3, user_id);
                                            int loginUpdateCount = loginUpdateSt.executeUpdate();
                                            
                                            // if (loginUpdateCount > 0) {
                                            //     System.out.println("Login table updated successfully.");
                                            // } else {
                                            //     System.out.println("Failed to update login table.");
                                            // }
                                        }
                                        } else {
                                            System.out.println("Failed to update ticket count.");
                                        }
                                        
                                        Flight_Search.flight(user_id);
                                    }
                                } else {
                                    System.out.println("Tickets not purchased.");
                                    Flight_Search.flight(user_id);
                                }
                            } else {
                                System.out.println("Transaction cancelled.");
                                Flight_Search.flight(user_id);
                            }
                        } else {
                            System.out.println("Tickets are not available.");
                            Flight_Search.flight(user_id);
                        }
                    } else {
                        System.out.println("Flight details not found.");
                        Flight_Search.flight(user_id);
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Error connecting to database: " + e.getMessage());
        }
    }
}
