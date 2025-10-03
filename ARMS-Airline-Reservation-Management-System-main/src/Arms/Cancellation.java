package Arms;

import Arms_Load.*;
import java.sql.*;
import java.util.Scanner;

public class Cancellation extends Login{
    public static void cancel() throws SQLException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);
        System.out.println("                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
        System.out.println("                                                            Please Re-Enter for security purpose            ");
        System.out.println("                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
        System.out.println("Enter userid");
        username = sin.nextLine();
        System.out.println("Enter password");
        String password = sin.nextLine();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);
        String query = "select * from login";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);

        while(rs.next()) {
            if (username.matches(rs.getString(1)) && password.matches(rs.getString(2))) {
                System.out.println("Do u want to cancel tickets[y/n] : ");
                String n = sin.next();
                if(n.matches("y")) {
                    String query1 = "update login set tickets_purchased = 0 where userid = ?";
                    PreparedStatement st1 = con.prepareStatement(query1);
                    st1.setString(1,username);
                    int count3  = st1.executeUpdate();
                    st1.close();

                    String query2 = "update login set amount_paid = 0 where userid = ?";
                    PreparedStatement st2 = con.prepareStatement(query2);
                    st2.setString(1,username);
                    int count  = st2.executeUpdate();
                    st2.close();
                    System.out.println("                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
                    System.out.println("                                                              Sorry for Inconvenience caused                           ");
                    System.out.println("                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
                }
            }
        }
        st.close();
        con.close();

 }
}
