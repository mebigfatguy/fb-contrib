import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class IED_Sample 
{
    public static void noIO(Connection c) throws IOException, SQLException
    {
        PreparedStatement ps = c.prepareStatement("select boo from hoo");
        ResultSet rs = ps.executeQuery();
    }
    
    private void noSQL(File f) throws SQLException, IOException
    {
        InputStream is = new FileInputStream(f);
        int i = is.read();
    }
    
    public final void noCNFE() throws ClassNotFoundException, IOException, SQLException
    {
        noSQL(new File("hello.world"));
    }
}
