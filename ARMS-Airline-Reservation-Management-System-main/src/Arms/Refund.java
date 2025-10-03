package Arms;

import Arms_Load.*;
import java.sql.*;

public class Refund {
    public static void refund(String user_id) throws SQLException, ClassNotFoundException {

        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);
        String query = "select * from login";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int n = 0;
        int s = 0;

        Cancellation.cancel();
        while(rs.next()) {
           if(rs.getInt("amount_paid") != 0) {
               s = rs.getInt("amount_paid");
               n = 1;
           }
        }

        st.close();
        con.close();
        double r = 0.5*s;
        if(n==1) {
            System.out.println("                                * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *                                ");
            System.out.println("                                *                       Tickets Cancelled Successfully                                  *                                                ");
            System.out.println("                                *       Your refund money " + r + " will be refunded within 3 working days                 *                                       ");
            System.out.println("                                * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *                                ");
        }
        else
        System.out.println("                                   Tickets are not Cancelled or not purchased using this ARMS account, please recheck your details.                         ");
        System.out.println("                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");


    }

}
