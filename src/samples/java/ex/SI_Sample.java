package ex;

public class SI_Sample {

    public void simpleSwitchNull(String game) {
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
}
