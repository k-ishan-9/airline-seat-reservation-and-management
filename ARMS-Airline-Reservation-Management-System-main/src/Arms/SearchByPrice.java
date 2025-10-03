package Arms;

import java.sql.*;
import java.util.Scanner;

abstract class SearchByPrice implements Print{
    @Override

    public void printInterface() {
        System.out.println("                                                                                                                "    );
        System.out.println("                                                                * * * * * * * * * * * * * *                                                          ");
        System.out.println("                                                                *  Welcome to ARMS Portal *     ");
        System.out.println("                                                                * * * * * * * * * * * * * *                               ");
        System.out.println("        ______________________________________________________________________________________________________________________________________________                                                                                                                                                   ");
        System.out.println("        |                                                                                                                                            |");
        System.out.println("        |                                                                                                                                            |");
        System.out.println("        |                            *                    * * * * * * * * *            *                *            * * * * * * * *                 |");
        System.out.println("        |                           * *                   *              *             *  *          *  *            *             *                 |");
        System.out.println("        |                          *   *                  *            *               *    *      *    *            *                               |");
        System.out.println("        |                         *     *                 *          *                 *      *  *      *            *                               |");
        System.out.println("        |                        *       *                *        *                   *        *       *            *                               |");
        System.out.println("        |                       *         *               *      *                     *                *            *                               |");
        System.out.println("        |                      *           *              *    *                       *                *            * * * * * * * *                 |");
        System.out.println("        |                     * * * * * * * *             *  *                         *                *                          *                 |");
        System.out.println("        |                    *               *            *     *                      *                *                          *                 |");
        System.out.println("        |                   *                 *           *        *                   *                *                          *                 |");
        System.out.println("        |                  *                   *          *           *                *                *                          *                 |");
        System.out.println("        |                 *                     *         *              *             *                *            *             *                 |");
        System.out.println("        |                *                       *        *                *           *                *            * * * * * * * *                 |");
        System.out.println("        |                                                                                                                                            |");
        System.out.println("        |                                                                                                                                            |");
        System.out.println("        |____________________________________________________________________________________________________________________________________________|");



    }
    public static void search( String userid) throws SQLException, ClassNotFoundException {
        System.out.println("                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
        System.out.println("                                           |                     1.Economy class by price                        |                       ");
        System.out.println("                                           |                     2.Business class by price                       |           ");
        System.out.println("                                           |                     3.First class by price                          |       ");
        System.out.println("                                           |                     4.Go Back                                         |           ");
        System.out.println("                                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
        System.out.println("Choose your preferable class to proceed:-");
        Scanner sin = new Scanner(System.in);
        try {
        int k = sin.nextInt();

            if (k == 1) {
                Eco_price();
                Flight_Search.flight(userid);
            }
            if (k == 2) {
                Buss_price();
                Flight_Search.flight(userid);
            }
            if (k == 3) {
                First_price();
                Flight_Search.flight(userid);
            }
            else
                Flight_Search.flight(userid);
        }
        catch(Exception e) {
            System.out.println("                                                        * * * * * * * * * * * * * * * * *");
            System.out.println("                                                        * Please Enter a valid Integer  *      ");
            System.out.println("                                                        * * * * * * * * * * * * * * * * *");
            search(userid);
        }
    }
    public static void Eco_price() throws ClassNotFoundException, SQLException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);
        System.out.println("Enter maximum range of the Price:-");
        int k = sin.nextInt();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);
        String query = "select *from flight_search where Flight_ID in (select Flight_ID from class where Economy_Class <= ?)";
        PreparedStatement st = con.prepareStatement(query);
        st.setInt(1,k);
        ResultSet rs = st.executeQuery();
        if(k<2300) {
            System.out.println("                                                         * * * * * * * * * * * * * * * * * * * * * * * * * * * *                                 ");
            System.out.println("                                                         *    No Flights are available for this price range    *  ");
            System.out.println("                                                         * * * * * * * * * * * * * * * * * * * * * * *  *  * * *                                 ");
        }
        else {
            while (rs.next()) {

                System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                                                                                                                                                                                 ");
                String p = "                                       |" + "Flight_Id : " + rs.getInt(1) + "|" + "  Boarding : " + rs.getString(2) + "|" + "  Destination :" + rs.getString(3) + "|" + "  Date :" + rs.getString(4) + "|" + "  Time :" + rs.getString(5);
                System.out.println(p);
                System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                                                                                                                                                                                 ");

            }
            st.close();
        }
        con.close();
    }

    public static void Buss_price() throws ClassNotFoundException, SQLException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);
        System.out.println("Enter Price Range :");
        int k = sin.nextInt();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);
        String query = "select *from flight_search where Flight_ID in (select Flight_ID from class where Bussiness_Class <= ?)";
        PreparedStatement st = con.prepareStatement(query);
        st.setInt(1,k);
        ResultSet rs = st.executeQuery();
        if(k<3500) {
            System.out.println("                                                         * * * * * * * * * * * * * * * * * * * * * * * * * * * *                                 ");
            System.out.println("                                                         *    No Flights are available for this price range    *  ");
            System.out.println("                                                         * * * * * * * * * * * * * * * * * * * * * * *  *  * * *                                 ");
        }
        else {
            while (rs.next()) {
                System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                                                                                                                                                                                 ");
                String p = "                                       |" + "Flight_Id : " + rs.getInt(1) + "  Boarding : " + rs.getString(2) + "  Destination :" + rs.getString(3) + "  Date :" + rs.getString(4) + "  Time :" + rs.getString(5);
                System.out.println(p);
                System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                                                                                                                                                                                 ");

            }
            st.close();
        }
        con.close();
    }

    public static void First_price() throws SQLException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);
        System.out.println("Enter Price Range :");
        int k = sin.nextInt();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);
        String query = "select *from flight_search where Flight_ID in (select Flight_ID from class where First_Class <= ?)";
        PreparedStatement st = con.prepareStatement(query);
        st.setInt(1,k);
        ResultSet rs = st.executeQuery();
        if(k<5000) {
            System.out.println("                                                         * * * * * * * * * * * * * * * * * * * * * * * * * * * *                                 ");
            System.out.println("                                                         *    No Flights are available for this price range    *  ");
            System.out.println("                                                         * * * * * * * * * * * * * * * * * * * * * * *  *  * * *                                 ");
        }
        else {
            while (rs.next()) {
                System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                                                                                                                                                                                 ");
                String p = "                                       |" + "Flight_Id : " + rs.getInt(1) + "|" + "  Boarding : " + rs.getString(2) + "|" + "  Destination :" + rs.getString(3) + "|" + "  Date :" + rs.getString(4) + "|" + "  Time :" + rs.getString(5);
                System.out.println(p);
                System.out.println("                             ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                                                                                                                                                                                 ");

            }
            st.close();
        }
        con.close();
    }

}
