package ex;

class A {
}

class B {
}

@SuppressWarnings("all")
public class SCR_Sample {
    A a;
    String name = SCR_Sample.class.getName();

    public String testForNameOfThis() {
        try {
            Class c = Class.forName("SCR_Sample");
            return c.getName();
        } catch (ClassNotFoundException cnfe) {
            return null;
        }
    }

    public String testForNameOfMember() {
        try {
            Class c = Class.forName("A");
            return c.getName();
        } catch (ClassNotFoundException cnfe) {
            return null;
        }
    }

    public String testForNameOfLocal(B b) {
        try {
            Class c = Class.forName("B");
            return c.getName();
        } catch (ClassNotFoundException cnfe) {
            return null;
        }
    }

    public String testForNameOfUnknown() {
        try {
            Class c = Class.forName("C");
            return c.getName();
        } catch (ClassNotFoundException cnfe) {
            return null;
        }
    }

    public String testGetClass() {
        Class c = this.getClass();
        return c.getName();
    }

    public String testDotClass() {
        return SCR_Sample.class.getName();
    }

}
