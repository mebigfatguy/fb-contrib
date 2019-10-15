package ex;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class NFF_Sample implements Serializable {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    private final transient List<String> s = new ArrayList<String>();
}