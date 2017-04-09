package ex;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("all")
public class PL_Sample {
    List<String> name = new ArrayList<String>();
    List<Integer> age = new ArrayList<Integer>();

    String[] make = new String[100];
    String[] model = new String[100];

    int[] x = new int[10];
    int[] y = new int[10];

    public void printPeople() {
        for (int i = 0; i < name.size(); i++) {
            System.out.println(name.get(i) + " " + age.get(i));
        }
    }

    public String getModel(String cMake) {
        for (int i = 0; i < 100; i++) {
            String carMake = make[i];
            String carModel = model[i];

            if (cMake.equals(carMake))
                return carModel;
        }

        return null;
    }

    public void testFP() {
        int i = 0;
        int xx = x[i];
        i += 1;
        int yy = y[i];
    }
}