public class TestRichText {
    public static void main(String[] args) {
        String html = "<p>Here is a note.</p><ul><li>Bring sunscreen</li><li>Wear hats</li></ul><p>Enjoy!</p>";
        String processed = html.replaceAll("(?i)<li>", "• ")
                               .replaceAll("(?i)</li>", "\n")
                               .replaceAll("(?i)</p>", "\n")
                               .replaceAll("(?i)<br\\s*/?>", "\n")
                               .replaceAll("&nbsp;", " ");
        processed = processed.replaceAll("<[^>]*>", "").trim();
        
        String[] lines = processed.split("\\n");
        for(String line : lines) {
            System.out.println("Line: [" + line.trim() + "]");
        }
    }
}
