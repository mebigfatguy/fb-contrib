public class SGSU_Sample {
    private SGSU_Sample foo;
    private SGSU_Sample foo2;

    public void testSGSULocals(SGSU_Sample s1, SGSU_Sample s2) {
        s1.setSGSU(s1.getSGSU());
    }

    public void testSGSUFields() {
        foo.setSGSU(foo.getSGSU());
    }

    public void fpSGSUFields() {
        foo.setSGSU(foo2.getSGSU());
    }

    public void setSGSU(SGSU_Sample f) {
        foo = f;
    }

    public SGSU_Sample getSGSU() {
        return foo;
    }
}
