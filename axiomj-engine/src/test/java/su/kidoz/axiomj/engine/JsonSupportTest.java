package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Fact;

/** Dogfood: AxiomJ tests its own JSON string escaping (the foundation of every JSON-ish report). */
class JsonSupportTest {

    @Fact(name = "quote escapes quotes and backslashes")
    void escapesQuotesAndBackslashes() {
        expect(JsonSupport.quote("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
    }

    @Fact(name = "quote escapes newlines and tabs")
    void escapesWhitespace() {
        expect(JsonSupport.quote("a\nb\tc")).isEqualTo("\"a\\nb\\tc\"");
    }

    @Fact(name = "quote escapes control characters as unicode")
    void escapesControlCharacters() {
        // A U+0001 control char has no named escape; it must be emitted as a \\uXXXX sequence and
        // never appear raw, otherwise the JSON is invalid.
        char control = (char) 1;
        var json = JsonSupport.quote("x" + control + "y");
        expect(json).contains("\\u0001");
        expect(json.indexOf(control) < 0).isTrue();
    }

    @Fact(name = "quote wraps null as an empty string")
    void quotesNullAsEmptyString() {
        expect(JsonSupport.quote(null)).isEqualTo("\"\"");
    }
}
