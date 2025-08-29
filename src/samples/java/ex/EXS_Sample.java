package ex;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

@SuppressWarnings("all")
public class EXS_Sample extends Super {
    @Override
    public void constrainedNone() {
        try {
            InputStream is = new FileInputStream("c:\\test.txt");
        } catch (IOException ioe) {
            throw new RuntimeException("Ooops");
        }
    }

    @Override
    public void constrainedNon() throws SQLException {
        try {
            InputStream is = new FileInputStream("c:\\test.txt");
        } catch (IOException ioe) {
            throw new RuntimeException("Ooops");
        }
    }

    public void notConstrained() {
        try {
            InputStream is = new FileInputStream("c:\\test.txt");
        } catch (IOException ioe) {
            throw new RuntimeException("Ooops");
        }
    }

    @Override
    public void constrainedByRuntime() {
        try {
            InputStream is = new FileInputStream("c:\\test.txt");
        } catch (IOException ioe) {
            throw new RuntimeException("Ooops");
        }
    }

    public static void testFPBug2646424(String[] args) {
        if (args.length == 0) {
            try {
                Class.forName("java.lang.Integer");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                return;
            }
        } else {
            throw new IllegalArgumentException("cannot take arguments");
            // EXS
        }
    }

    public boolean returnFalseOnEx() {
        try {
            Class.forName("java.lang.Integer");
            return true;
        } catch (ClassNotFoundException cnfe) {
            return false;
        }
    }

    public boolean fpReturnFalseOnEx(int i) {
        try {
            if (i == 0) {
                return false;
            }
            Class<?> cls = Class.forName("java.lang.Integer");
            return true;
        } catch (ClassNotFoundException cnfe) {
            return false;
        }
    }

    public boolean fpReturnFalseInFinally() {
        try {
            return true;
        } finally {
            return false;
        }
    }
    
    public long fpRTToRT447(String s) {
    	try {
    		throw new MyRTE447();
    	} catch (MyRTE447 r) {
    		MyRTE447 rr = r.embellish("important!");
    		if (s == null) {
    			throw r;
    		} else {
    			throw rr;
    		}
    	}
    }
}

class MyRTE447 extends RuntimeException {
	String data;

	public MyRTE447() {
	}

	MyRTE447 embellish(String s) {
		data = s;
		return this;
	}
}



class Super {
    public void constrainedNone() {
    }

    public void constrainedNon() throws SQLException {
    }

    public void constrainedByRuntime() throws RuntimeException {
    }
}