package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Execution;
import su.kidoz.axiomj.api.ExecutionMode;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.mock.Mocks;
import su.kidoz.axiomj.mock.bytecode.ConstructorMocks;
import su.kidoz.axiomj.mock.bytecode.StaticMocks;

/**
 * Advanced mocking capabilities (Static and Constructor) require bytecode instrumentation via a Java Agent. Because
 * these handlers are process-wide, tests using them should generally run sequentially to avoid race conditions.
 */
@ProductArea("Documentation")
@Feature(id = "docs.mocking", name = "Advanced Mocking Examples", owner = "docs-team")
@Execution(ExecutionMode.SEQUENTIAL)
public final class ConstructorAndStaticMockExampleTest {

    public static class FileSystemUtil {
        public static boolean isDirectory(String path) {
            // Imagine this actually hits the disk
            return false;
        }
    }

    public static class NetworkClient {
        public String fetch(String url) {
            // Imagine this makes a real HTTP call
            return "Real data";
        }
    }

    @Fact
    @Scenario("Mocking a static method completely overrides the real implementation")
    void staticMockingExample() {
        // 1. Enable static mocking for the class
        StaticMocks.mockStatic(FileSystemUtil.class);

        try {
            // 2. Set up expectations using the standard core Mocks API
            Mocks.when(() -> FileSystemUtil.isDirectory("/fake/dir")).thenReturn(true);

            // 3. Verify behavior
            expect(FileSystemUtil.isDirectory("/fake/dir")).isTrue();
            expect(FileSystemUtil.isDirectory("/other/dir")).isFalse(); // Unstubbed returns default (false)

            Mocks.verify(() -> FileSystemUtil.isDirectory("/fake/dir")).calledOnce();
        } finally {
            // 4. ALWAYS unmock in a finally block to restore the original bytecode
            StaticMocks.unmockStatic(FileSystemUtil.class);
        }
    }

    @Fact
    @Scenario("Mocking construction intercepts `new` calls and returns a mock object")
    void constructorMockingExample() {
        // 1. Enable constructor mocking
        ConstructorMocks.mockConstruction(NetworkClient.class);

        try {
            // 2. Instantiate the object normally. The agent intercepts this and attaches a mock handler.
            NetworkClient client = new NetworkClient();

            // 3. Stub the instance methods
            Mocks.when(() -> client.fetch("http://example.com")).thenReturn("Mocked data");

            // 4. Verify behavior
            expect(client.fetch("http://example.com")).isEqualTo("Mocked data");
            expect((Object) client.fetch("http://other.com")).isNull(); // Unstubbed object returns default (null)

        } finally {
            // 5. ALWAYS unmock to allow real instantiations again
            ConstructorMocks.unmockConstruction(NetworkClient.class);
        }
    }
}
