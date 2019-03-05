package ex;

public class PCOA_Sample {
    public PCOA_Sample() {
        overridableMethod();
    }

    public PCOA_Sample(int ok) {
        nonOverridableMethod();
    }

    public PCOA_Sample(String ok) {
    	//on jdk 8 this is INVOKESPECIAL, on jdk11 it's invokevirtual
        privateNonFinalMethod();
    }

    public PCOA_Sample(long privateCallsOverridable) {
        privateCallsOverridable();
    }

    public void overridableMethod() {
    }

    private void privateNonFinalMethod() {
    }

    public final void nonOverridableMethod() {
    }

    private void privateCallsOverridable() {
        overridableMethod();
    }

    final static class FinalClass {
        public FinalClass() {
            aMethod();
        }

        public void aMethod() {
        }
    }
}
