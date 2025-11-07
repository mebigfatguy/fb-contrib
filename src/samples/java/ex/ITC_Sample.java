package ex;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import foreign.exchange.EventProcessor;

@SuppressWarnings("all")
public class ITC_Sample implements EventProcessor {
    class A {

    }

    class B extends A {
    }

    class C extends A {
    }

    public String testOthers(List<String> l) {
        if (l instanceof ArrayList) {
            return (String) ((ArrayList) l).remove(0);
        } else if (l instanceof LinkedList) {
            return (String) ((LinkedList) l).removeFirst();
        } else if (l instanceof Vector) {
            return (String) ((Vector) l).remove(0);
        } else {
            return null;
        }
    }

    public String testMine(A a) {
        if (a instanceof B) {
            return "Yes";
        } else if (a instanceof C) {
            return "No";
        } else {
            return "Unknown";
        }
    }

    @Override
    public void fp372(GenericEvent event) {
        if (event instanceof AEvent) {
            doA((AEvent) event);
        } else if (event instanceof BEvent) {
            doB((BEvent) event);
        }
    }

    private void doA(AEvent ae) {
    }

    private void doB(BEvent ae) {
    }
}
