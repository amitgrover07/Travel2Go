import java.text.NumberFormat;
import java.util.Locale;

public class TestFormat2 {
    public static void main(String[] args) {
        long price = 12750000;
        NumberFormat f1 = NumberFormat.getNumberInstance(new Locale("hi", "IN"));
        System.out.println("hi_IN: " + f1.format(price));
        
        System.out.println("manual: " + formatIndianStyle(price));
    }
    
    public static String formatIndianStyle(long value) {
        String s = Long.toString(value);
        if (s.length() <= 3) return s;
        StringBuilder sb = new StringBuilder();
        sb.append(s.substring(s.length() - 3));
        int i = s.length() - 3;
        while (i > 0) {
            sb.insert(0, ",");
            int start = Math.max(0, i - 2);
            sb.insert(0, s.substring(start, i));
            i = start;
        }
        return sb.toString();
    }
}
