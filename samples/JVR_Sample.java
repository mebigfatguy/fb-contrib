import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;



public class JVR_Sample 
{
	public String getBlobAsString(ResultSet rs) throws SQLException
	{
		VendorBlob vb = (VendorBlob)rs.getBlob(1);
		return vb.convertBlobToString();
	}
	
	public String falsePositive(ResultSet rs) throws SQLException
	{
		Blob vb = rs.getBlob(1);
		return vb.getClass().getName();
	}
}

class VendorBlob implements Blob
{
	public String convertBlobToString()
	{
		return "Booya";
	}
	
	public InputStream getBinaryStream() throws SQLException {
		return null;
	}

	public byte[] getBytes(long arg0, int arg1) throws SQLException {
		return null;
	}

	public long length() throws SQLException {
		return 0;
	}

	public long position(Blob arg0, long arg1) throws SQLException {
		return 0;
	}

	public long position(byte[] arg0, long arg1) throws SQLException {
		return 0;
	}

	public OutputStream setBinaryStream(long arg0) throws SQLException {
		return null;
	}

	public int setBytes(long arg0, byte[] arg1, int arg2, int arg3) throws SQLException {
		return 0;
	}

	public int setBytes(long arg0, byte[] arg1) throws SQLException {
		return 0;
	}

	public void truncate(long arg0) throws SQLException {		
	}

	public void free() throws SQLException {
	}

	public InputStream getBinaryStream(long arg0, long arg1) throws SQLException {
		return null;
	}
}