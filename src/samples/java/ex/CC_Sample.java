package ex;

public class CC_Sample {

    boolean switch1_353(final String name) { // Complexity 7
        switch (name) {
        case "1":
            return false;
        default:
            return true;
        }
    }

    boolean switch2_353(final String name) { // Complexity 10
        switch (name) {
        case "1":
        case "2":
            return false;
        default:
            return true;
        }
    }

    boolean switch3_353(final String name) { // Complexity 13
        switch (name) {
        case "1":
        case "2":
        case "3":
            return false;
        default:
            return true;
        }
    }

    boolean switch4_353(final String name) { // Complexity 13
        switch (name) {
        case "1":
        case "2":
        case "3":
        case "4":
            return false;
        default:
            return true;
        }
    }
}
