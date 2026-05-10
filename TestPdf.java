import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.html.simpleparser.HTMLWorker;
import java.io.FileOutputStream;
import java.io.StringReader;

public class TestPdf {
    public static void main(String[] args) throws Exception {
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, new FileOutputStream("test.pdf"));
        document.open();
        String html = "<p>Here is a paragraph.</p><ul><li>Item 1</li><li>Item 2</li></ul><p>Special <strong>Notes</strong>!</p>";
        java.util.List<com.lowagie.text.Element> elements = HTMLWorker.parseToList(new StringReader(html), null);
        for(com.lowagie.text.Element el : elements) {
            document.add(el);
        }
        document.close();
        System.out.println("Success!");
    }
}
