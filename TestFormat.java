import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.util.Locale;

public class TestFormat {
    public static void main(String[] args) {
        long price = 127500;
        NumberFormat format1 = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        System.out.println("en_IN locale: " + format1.format(price));
        
        DecimalFormat df = new DecimalFormat("##,##,###");
        System.out.println("DecimalFormat: " + df.format(price));
    }
}
