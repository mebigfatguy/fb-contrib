import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@SuppressWarnings("all")
public class SIL_Sample 
{
	public void test(ResultSet rs) throws SQLException
	{
		Connection c = rs.getStatement().getConnection();
		PreparedStatement ps = c.prepareStatement("select foo from boo where moo = ?");
		
		while (rs.next()) 
		{
			int key = rs.getInt(1);
			ps.setInt(1, key);
			ResultSet mrs = ps.executeQuery();
		}
	}
}
