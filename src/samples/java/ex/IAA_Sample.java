package ex;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.CharBuffer;

@SuppressWarnings("all")
public class IAA_Sample {
	
	public String testIAA1Report() {
		Appendable apd = new StringBuilder();
		CharSequence cs = new StringBuilder("foo");
		try {
			apd.append(cs.toString());
		} catch (IOException e) {
			e.printStackTrace();
		}
		return apd.toString();
	}
	
	public String testIAH2Report() {
		Appendable apd = new StringBuilder();
		StringBuilder sb = new StringBuilder("bar");
		try {
			apd.append(sb.toString());
		} catch (IOException e) { 
			e.printStackTrace();
		}
		return sb.toString();
	}

	public String testIAH3Report() {
		PrintWriter pw = null;
		File temp = null;
		try {
			temp = File.createTempFile("myTempFile", ".txt"); 
			temp.deleteOnExit();
			pw = new PrintWriter(temp);
			CharSequence cs = new StringBuilder("foo");
			pw.append(cs.toString());		
			return cs.toString();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		} finally {
			if (pw != null)
				pw.close();			
		}
	}
	
	public String testIAH4Report() {
		FileWriter fw = null;
		try {
			File temp = File.createTempFile("myTempFile", ".txt"); 
			temp.deleteOnExit();
			fw = new FileWriter(temp);
			CharBuffer cb = CharBuffer.allocate(10); 
			cb.put("foobar");
			fw.append(cb.toString());
			fw.close();
			return cb.toString();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public String testIAH5Report() {
		PrintWriter pw = null;
		try {
			pw = new PrintWriter("file.txt");
			StringBuilder cs = new StringBuilder("foobar");
			pw.append(cs.toString(), 1, 2);
			pw.close();
			return cs.toString();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}

	}
	
	public String testIAA6DoNotReport() {
		Appendable sb = new StringBuilder();
		int[] intArr = new int[] { 1, 2, 3, 4 }; 
		try {
			sb.append(intArr.toString());
		} catch (IOException e) { 
			e.printStackTrace();
		}
		return sb.toString();
	}

}
