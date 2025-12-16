package ex;

public class SI_Sample {

    public void simpleObjectSwitchNull(String game) {
        if (game != null) {
            switch (game) {
            case "Tic": {
                System.out.println("1");
                break;
            }
            case "Tac": {
                System.out.println("2");
                break;
            }
            case "Toe": {
                System.out.println("3");
                break;
            }
            }
        }
    }

    public void simpleEnumSwitchNull(GiantSounds gs) {
        if (gs != null) {
            switch (gs) {
            case Fee: {
                System.out.println("1");
                break;
            }
            case Fi: {
                System.out.println("2");
                break;
            }

            case Fo: {
                System.out.println("3");
                break;
            }

            case Fum: {
                System.out.println("4");
                break;
            }
            }
        }
    }

    enum GiantSounds {
        Fee, Fi, Fo, Fum
    }
}
