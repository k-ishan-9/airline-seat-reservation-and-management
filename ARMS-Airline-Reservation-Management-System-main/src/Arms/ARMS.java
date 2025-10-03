package Arms;

import Arms_Load.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Scanner;

public class ARMS {
    public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
        SearchByPrice s = new SearchByPrice() {
        };
        s.printInterface();
        LoadCSV.load();
        while(true) {

            System.out.println("                                                             * * * * * * * * * * *                                  ");
            System.out.println("                                                             *     1.Login       *       ");
            System.out.println("                                                             *     2.Create      *        ");
            System.out.println("                                                             *     3.Delete      *        ");
            System.out.println("                                                             *     4.Forget      *        ");
            System.out.println("                                                             *     5.Exit        *                 ");
            System.out.println("                                                             * * * * * * * * * * *                    ");;
            System.out.println("Enter your choice from above options:-");
            Scanner sin = new Scanner(System.in);
            try {
                int k = sin.nextInt();
                if (k == 1) {
                    Login.login();
                }
                else if (k == 2) {
                    Login.create();
                }
                else if (k == 3) {
                    Login.delete();
                }
                else if(k==4) {
                    Login.forget();
                }
                else {
                    System.out.println("                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");
                    System.out.println("                                          |    Thank you very much, hope you enjoyed a lot, please Visit Again   |   ");
                    System.out.println("                                          ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~                                                            ");

                    break;
                }
            }
            catch (Exception e) {
                System.out.println("                                                        * * * * * * * * * * * * * * * * *");
                System.out.println("                                                        * Please Enter a valid Integer  *      ");
                System.out.println("                                                        * * * * * * * * * * * * * * * * *");
            }
        }
    }
}
