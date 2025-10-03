package Arms;

import Arms_Load.*;
import java.sql.*;
import java.util.*;

class Login {
     static String username;
     static String password;

    public static void login() throws SQLException, ClassNotFoundException {

        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);
        System.out.println("Enter your ARMS userid");
        username = sin.nextLine();
        System.out.println("Enter your password");
        password = sin.nextLine();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);
        String query = "select * from login";
        Statement st = con.createStatement();
        ResultSet rs = st.executeQuery(query);
        int n = 0;
        String user_id = null;
        while (rs.next()) {
            if (username.equals(rs.getString(1)) && password.equals(rs.getString(2))) {
                n = 1;
                user_id= username;
                break; // Exit the loop once a match is found
            } else if (username.equals(rs.getString(1)) && !(password.equals(rs.getString(2)))) {
                n = 2;
                break; // Exit the loop once a match with incorrect password is found
            }
        }
        if (n == 0) {
            n = 3; // No matching username found in the ResultSet
        }
        System.out.println(n);
        st.close();

        if(n==1) {
            System.out.println("                                                            ~~~~~~~~~~~~~~~~~~~~~~~                                           ");
            System.out.println("                                                            |   Login Successful  |   ");
            System.out.println("                                                            ~~~~~~~~~~~~~~~~~~~~~~~                                           ");
            Flight_Search.flight(user_id);
        }
        else if(n==2) {
            forget();
        }
        else if(n==3) {
            System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
            System.out.println("                                                    |              Account not found               |                  ");
            System.out.println("                                                    |    To proceed further Create ARMS Account    |          ");
            System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~+~~~~~~~~~~~~                                                        ");
            System.out.println("Do u want to continue [y/n] : ");
            String  k = sin.next();
            if(k.matches("y")) {
                create();
            }
            else
                return;
        }
        con.close();
    }

    public static void create() throws SQLException, ClassNotFoundException {

        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Scanner sin = new Scanner(System.in);
        System.out.println("Enter userid");
        String username1 = sin.nextLine();
        System.out.println("Enter password");
        String password1 = sin.nextLine();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);

        String query1 = "select *from login";
        Statement st1 = con.createStatement();
        ResultSet rs1 = st1.executeQuery(query1);
        int s =0;
        while(rs1.next()) {
            if(username1.matches(rs1.getString(1 ))){
               s = 1;
            }
        }
        if(s==1) {
            System.out.println("                                                          * * * * * * * * * * * * * *             ");
            System.out.println("                                                          *  Account already exists * ");
            System.out.println("                                                          * * * * * * * * * * * * * *        ");

        }
        else {
            String query = "insert into login values(?,?,0,0)";
            PreparedStatement st = con.prepareStatement(query);
            st.setString(1, username1);
            st.setString(2, password1);
            int count = st.executeUpdate();
            System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
            System.out.println("                                                     |        Account Created Successfully        |  ");
            System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
            st.close();
        }

        con.close();
    }

    public static void delete() throws SQLException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        System.out.println("                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                           ");
        System.out.println("                                                 |   Please enter your details again for security reasons  |        ");
        System.out.println("                                                 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
        Scanner sin = new Scanner(System.in);
        System.out.println("Enter userid again-");
        String username2 = sin.nextLine();
        System.out.println("Enter password again:-");
        String password2 = sin.nextLine();
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);

        String query1 = "select *from login";
        Statement st1 = con.createStatement();
        ResultSet rs1 = st1.executeQuery(query1);
        int s =0;
        while(rs1.next()) {
            if(username2.matches(rs1.getString(1 )) && password2.matches(rs1.getString(2))){
                s = 1;
            }
        }
        if(s==1) {
            String query = "delete from login where userid = ? ";
            PreparedStatement st = con.prepareStatement(query);
            st.setString(1,username2);
            int count = st.executeUpdate();

            System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
            System.out.println("                                                     |       Account Deleted Successfully         |       ");
            System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");

            st.close();
        }
        else {
            System.out.println("                                                           * * * * * * * * * * * * * *             ");
            System.out.println("                                                           *  Account Doesn't exists * ");
            System.out.println("                                                           * * * * * * * * * * * * * *        ");
            con.close();
        }
    }

    public static void forget() throws SQLException, ClassNotFoundException {
        String url = "jdbc:mysql://localhost:3306/arms";
        String uname = "root";
        String pass = "vikas";
        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection con = DriverManager.getConnection(url,uname,pass);

        String username2;
        String newpass;
        Scanner sin = new Scanner(System.in);

        System.out.println("Have you forgot password [y/n]");
        String p1 = sin.nextLine();


        if((p1.matches("y"))) {
            System.out.println("Enter userid -");
            username2 = sin.nextLine();

            String query5 = "select *from login";
            Statement st5 = con.createStatement();
            ResultSet rs5 = st5.executeQuery(query5);
            int a =0;
            while(rs5.next()) {
                if(username2.matches(rs5.getString(1))) {
                    a=1;
                }
            }
                if(a==1) {
                    System.out.println("Enter any random number you wish(OTP):-");
                    sin.nextLine();
                    System.out.println("Enter new password");
                    newpass = sin.nextLine();

                    String query1 = "select *from login";
                    Statement st1 = con.createStatement();
                    ResultSet rs1 = st1.executeQuery(query1);
                    int s = 0;
                    while (rs1.next()) {
                        if (username2.matches(rs1.getString(1))) {
                            s = 1;
                        }
                    }
                    if (s == 1) {
                        String query = "update login set password = ? where userid = ? ";
                        PreparedStatement st = con.prepareStatement(query);
                        st.setString(1, newpass);
                        st.setString(2, username2);
                        int count = st.executeUpdate();

                        System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
                        System.out.println("                                                     |       Password Updated Successfully         |       ");
                        System.out.println("                                                     ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");

                        st.close();

                    }
                }
                else{
                    System.out.println("                                                              * * * * * * * * * * * * * *             ");
                    System.out.println("                                                              *    Account not found    * ");
                    System.out.println("                                                              * * * * * * * * * * * * * *        ");                    System.out.println("Do u want to continue[y/n]");
                    String  k = sin.next();
                    if(k.matches("y")) {
                        create();
                    }
                }

            }
            else {
              return;
            }

        con.close();
    }
}
