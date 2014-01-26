public class CBX_Sample {
    public String testBuildXML(int val, String info) {
        StringBuffer sb = new StringBuffer();
        sb.append("<sample>");
        sb.append("<test name='" + val + "'>" + info + "</test>");
        sb.append("</sample>");
        return sb.toString();
    }
}
