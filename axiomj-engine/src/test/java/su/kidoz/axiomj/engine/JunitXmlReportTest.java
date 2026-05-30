package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import su.kidoz.axiomj.api.Fact;

/**
 * Dogfood + regression: AxiomJ renders its own JUnit XML and parses it back with a real XML parser to prove that
 * captured output cannot inject markup or emit characters that make the report non-well-formed.
 */
class JunitXmlReportTest {

    private static TestResult failedWithLog(String log) {
        var descriptor = new TestDescriptor(
                "ex.T#m",
                "ex.T",
                "m",
                "m",
                List.of(),
                "",
                "",
                "",
                "",
                "m",
                List.of(),
                new SourceLocation("", 0, 0, 0),
                List.of(),
                0);
        return new TestResult(
                descriptor, TestStatus.FAILED, 0L, 1L, 1L, new AssertionError("boom"), Map.of("log", log));
    }

    private static Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Fact(name = "captured output cannot break out of CDATA")
    void systemOutCannotInjectMarkup() throws Exception {
        // A test that prints a CDATA terminator followed by markup must not be able to inject real elements.
        var xml = JunitXmlReport.render(
                List.of(failedWithLog("before]]><inject>boom</inject>after")), new RunSummary(1, 0, 1, 0, 1L));

        var doc = parse(xml); // throws if the document is not well-formed
        expect(doc.getDocumentElement().getTagName()).isEqualTo("testsuites");
        expect(doc.getElementsByTagName("inject").getLength()).isEqualTo(0);
    }

    @Fact(name = "illegal XML control characters are stripped")
    void stripsIllegalControlCharacters() throws Exception {
        // U+0001 is illegal in XML 1.0 even inside CDATA; leaving it in would make the report unparseable.
        char control = (char) 1;
        var xml = JunitXmlReport.render(List.of(failedWithLog("a" + control + "b")), new RunSummary(1, 0, 1, 0, 1L));

        var doc = parse(xml); // would throw if the control char survived
        expect(doc.getDocumentElement().getTagName()).isEqualTo("testsuites");
        expect(xml.indexOf(control) < 0).isTrue();
    }
}
