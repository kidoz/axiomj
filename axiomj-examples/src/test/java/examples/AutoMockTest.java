package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.Optional;
import su.kidoz.axiomj.api.AutoMock;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Inject;
import su.kidoz.axiomj.api.Mock;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.mock.Mocks;

@ProductArea("Testing")
@Feature(id = "di.automock", name = "Auto-mocked dependencies", owner = "core-team")
@AutoMock
public final class AutoMockTest {

    @Inject
    GreetingService service;

    @Mock
    Repo repo;

    @Fact(name = "auto-mocked dependency is shared with the SUT")
    @Scenario("unbound interfaces become mocks; the @Mock field is the same instance the SUT received")
    void autoMockSharedWithSut() {
        Mocks.when(() -> repo.find("42")).thenReturn(Optional.of("Ada"));

        expect(service.greet("42")).isEqualTo("Hello, Ada");
        expect(service.greet("unknown")).isEqualTo("Hello, guest");
        Mocks.verify(() -> repo.find("42")).calledOnce();
    }

    interface Repo {
        Optional<String> find(String id);
    }

    static final class GreetingService {
        private final Repo repo;

        GreetingService(Repo repo) {
            this.repo = repo;
        }

        String greet(String id) {
            return repo.find(id).map("Hello, "::concat).orElse("Hello, guest");
        }
    }
}
