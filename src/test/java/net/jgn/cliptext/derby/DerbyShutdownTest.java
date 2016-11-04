package net.jgn.cliptext.derby;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author jose
 */
public class DerbyShutdownTest {

    @Test
    public void connectAndShutdownTest() throws Exception {
        String driver = "org.apache.derby.jdbc.EmbeddedDriver";
        Class.forName(driver).newInstance();
        Connection conn = DriverManager.getConnection("jdbc:derby:derbyTestdb;create=true");
        conn.close();

        try {
            DriverManager.getConnection("jdbc:derby:derbyTestdb;shutdown=true");
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }
}
