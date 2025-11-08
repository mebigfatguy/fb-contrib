package foreign.exchange;

public interface EventProcessor {
    void fp372(GenericEvent ge);

    public static class GenericEvent {
    }

    public static class AEvent extends GenericEvent {
    }

    public static class BEvent extends GenericEvent {
    }
}
