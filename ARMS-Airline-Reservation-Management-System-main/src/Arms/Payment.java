package Arms;
import java.sql.*;
import java.util.Scanner;

public class Payment {
    public static int pay(String user_id) throws SQLException, ClassNotFoundException {

        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);

            System.out.println("                                                             * * * * * * * * * *                                  ");
            System.out.println("                                                             *  1.Debit card   *                 ");
            System.out.println("                                                             *  2.Credit card  *             ");
            System.out.println("                                                             *  3.UPI          *     ");
            System.out.println("                                                             *  4.Go Back      *             ");
            System.out.println("                                                             * * * * * * * * * *                                  ");
            System.out.println("Select Payment mode :-");
            Scanner sin = new Scanner(System.in);
            int n = 0;
            try {
                int k = sin.nextInt();

                if (k == 1) {
                    System.out.println("Enter Account number");
                    int account = sin.nextInt();
                    System.out.println("Enter Password");
                    String passw = sin.next();
                    String query = "select * from debit";
                    Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery(query);

                    while (rs.next()) {
                        if (account == (rs.getInt(1)) && passw.matches(rs.getString(2))) {
                            n = 1;
                        }
                    }

                    st.close();
                    con.close();

                    if (n == 1) {
                        System.out.println("                                                   * * * * * * * * * * * * * * * * * * * *                                                        ");
                        System.out.println("                                                   *        Payment Successful           *    ");
                        System.out.println("                                                   *  We wish you a very Happy Journey   *                   ");
                        System.out.println("                                                   * * * * * * * * * * * * * * * * * * * *                                                  ");
                        return 1;
                    } else {
                        System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                        System.out.println("                                                        *      Payment UnSuccessful     *  ");
                        System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                        return 0;
                    }
                }
                else if (k == 2) {
                    System.out.println("Enter Account number");
                    int account = sin.nextInt();
                    System.out.println("Enter Password");
                    String passw = sin.next();
                    String query = "select * from credit";
                    Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery(query);

                    while (rs.next()) {
                        if (account == (rs.getInt(1)) && passw.matches(rs.getString(2))) {
                            n = 1;
                        }
                    }
                    st.close();
                    con.close();

                    if (n == 1) {
                        System.out.println("                                                   * * * * * * * * * * * * * * * * * * * *                                                        ");
                        System.out.println("                                                   *        Payment Successful           *    ");
                        System.out.println("                                                   *  We wish you a very Happy Journey   *                   ");
                        System.out.println("                                                   * * * * * * * * * * * * * * * * * * * *                                                  ");
                        return 1;
                    }
                    else {
                        System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                        System.out.println("                                                        *      Payment UnSuccessful     *  ");
                        System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                        return 0;
                    }
                }
                else if (k == 3) {
                    System.out.println("Enter UPI Id");
                    String id = sin.next();
                    System.out.println("Enter pin");
                    int s = sin.nextInt();

                    String query = "select * from upi";
                    Statement st = con.createStatement();
                    ResultSet rs = st.executeQuery(query);

                    while (rs.next()) {
                        if (id.matches(rs.getString(1)) && s == (rs.getInt(2))) {
                            n = 1;
                        }
                    }
                    st.close();
                    con.close();

                    if (n == 1) {
                        System.out.println("                                                   * * * * * * * * * * * * * * * * * * * *                                                        ");
                        System.out.println("                                                   *        Payment Successful           *    ");
                        System.out.println("                                                   *  We wish you a very Happy Journey   *                   ");
                        System.out.println("                                                   * * * * * * * * * * * * * * * * * * * *                                                  ");
                        return 1;
                    } else {
                        System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                        System.out.println("                                                        *      Payment UnSuccessful     *  ");
                        System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                        return 0;
                    }

                }
                else
                    Flight_Search.flight(user_id);
            }
            catch (Exception e) {
                System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                System.out.println("                                                        * Please Enter a valid Integer  *      ");
                System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                pay(user_id);
            }
        return n;
    }
}
