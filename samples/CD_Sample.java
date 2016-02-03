
public class CD_Sample {
    Child c = new Child();;

    public void cdTest() {
        c.childTest();
    }
}

class Child {
    SubChild sc = new SubChild();

    public void childTest() {
        sc.subChildTest();
    }
}

class SubChild {
    CD_Sample cds = new CD_Sample();

    public void subChildTest() {
        cds.toString();
    }
}

class Mammooth {
    private int x;

    static class Builder {
        private int x;

        public void setX(int x) {
            this.x = x;
        }

        public Mammooth build() {
            Mammooth m = new Mammooth();
            m.x = x;
            return m;
        }
    }
}

class SuspectBuilderUse {
    private int data;

    public static class Builder {

        int data;

        public void setData(int data) {
            this.data = data;
        }
    }

    public SuspectBuilderUse(Builder b) {
        this.data = b.data;
    }

    public int getData() {
        return data;
    }
}

class GitHubIssue101 {
    protected void onCreate(final String savedInstanceState) {
        final Intent intent = new Intent(ChildWithUsedClassObj.class);
        startActivity(intent);
    }

    private void startActivity(Intent i) {
    }
}

class ChildWithUsedClassObj extends GitHubIssue101 {
}

class Intent {
    Intent(Class c) {
    }
}
