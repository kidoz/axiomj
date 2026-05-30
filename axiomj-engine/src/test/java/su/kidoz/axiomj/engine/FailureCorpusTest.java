package su.kidoz.axiomj.engine;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import su.kidoz.axiomj.api.AfterEach;
import su.kidoz.axiomj.api.BeforeEach;
import su.kidoz.axiomj.api.Execution;
import su.kidoz.axiomj.api.ExecutionMode;
import su.kidoz.axiomj.api.Fact;

@Execution(ExecutionMode.SEQUENTIAL)
class FailureCorpusTest {

    private static final Path CORPUS_PATH = Path.of(".axiomj-corpus");

    @BeforeEach
    @AfterEach
    void cleanup() throws Exception {
        Files.deleteIfExists(CORPUS_PATH);
    }

    @Fact
    void remembersFailingSeedsAndPersistsThem() {
        var corpus1 = new FailureCorpus();
        corpus1.addFailure("test1", 123L);
        corpus1.addFailure("test1", 456L);
        corpus1.addFailure("test2", 789L);
        corpus1.save();

        expect(Files.exists(CORPUS_PATH)).isTrue();

        var corpus2 = new FailureCorpus();
        expect(corpus2.getSeeds("test1")).isEqualTo(List.of(123L, 456L));
        expect(corpus2.getSeeds("test2")).isEqualTo(List.of(789L));
        expect(corpus2.getSeeds("unknown")).isEmpty();
    }

    @Fact
    void removesPassesAndDeletesFileWhenEmpty() {
        var corpus = new FailureCorpus();
        corpus.addFailure("test1", 123L);
        corpus.save();

        expect(Files.exists(CORPUS_PATH)).isTrue();

        var corpus2 = new FailureCorpus();
        corpus2.removePass("test1", 123L);
        expect(corpus2.getSeeds("test1")).isEmpty();
        corpus2.save();

        expect(Files.exists(CORPUS_PATH)).isFalse();
    }
}
