@SuppressWarnings("all")
public class USBR_Sample {
    public int addEm(String csl) {
        String[] tokens = csl.split(",");
        int sum = 0;
        for (String s : tokens) {
            sum += Integer.parseInt(s);
        }

        int ave = sum / tokens.length;
        return ave;
    }

    public int dontReport(int j) {
        int i;
        try {
            i = 0;
            i = i / j;
        } catch (Exception e) {
            i = -1;
        }
        return i;
    }

    public Exception fpReturnException(String num) {
        try {
            int i = Integer.parseInt(num);
            return null;
        } catch (Exception e) {
            return e;
        }
    }

    public int fpPlusEquals() {
        int i = 0;
        i += 4;
        i += dontReport(i);
        return i;
    }

    public boolean fpOrEquals(boolean b) {
        b |= fpOrEquals(b);
        return b;
    }
}
