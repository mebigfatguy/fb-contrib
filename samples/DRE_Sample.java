import java.io.IOException;
import java.sql.SQLException;

@SuppressWarnings("all")
public class DRE_Sample {
    public void test1(int a) throws NullPointerException {
    }

    public void test2(int b) throws ClassCastException, IOException, IllegalMonitorStateException {
        if (b == 0)
            throw new IOException("test");
    }

    public void test3(int c) throws SQLException {
        if (c == 0)
            throw new SQLException("test");
    }

    public void test4(int d) throws CustomRuntimeException {
        if (d == 0)
            throw new CustomRuntimeException();
    }
}

@SuppressWarnings("all")
class CustomRuntimeException extends RuntimeException {

}