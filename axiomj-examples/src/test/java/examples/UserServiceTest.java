package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.Optional;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Inject;
import su.kidoz.axiomj.api.Mock;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Requirement;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.api.UseModules;
import su.kidoz.axiomj.di.Binder;
import su.kidoz.axiomj.di.TestModule;
import su.kidoz.axiomj.mock.Mocks;

@ProductArea("Identity")
@Feature(id = "identity.greeting", name = "User greeting", owner = "identity-team")
@UseModules(UserServiceTest.Module.class)
public final class UserServiceTest {
    private final UserService service;

    @Inject
    public UserServiceTest(UserService service) {
        this.service = service;
    }

    @Mock
    UserRepository repository;

    @Fact(tags = {"di", "mock"})
    @Scenario("known user receives a personalized greeting")
    @Requirement("REQ-ID-010")
    void greetsKnownUser(@Mock UserRepository localRepository) {
        Mocks.when(() -> localRepository.findName("42")).thenReturn(Optional.of("Ada"));
        var localService = new UserService(localRepository);

        expect(localService.greeting("42")).isEqualTo("Hello, Ada");
        Mocks.verify(() -> localRepository.findName("42")).calledOnce();
    }

    @Fact(tags = "di")
    @Scenario("module injection provides default service")
    void moduleProvidesService() {
        expect(service.greeting("missing")).isEqualTo("Hello, stranger");
    }

    public static final class Module implements TestModule {
        @Override
        public void configure(Binder binder) {
            binder.bind(UserRepository.class, () -> id -> Optional.empty());
            binder.bind(UserService.class, () -> new UserService(id -> Optional.empty()));
        }
    }

    interface UserRepository {
        Optional<String> findName(String id);
    }

    record UserService(UserRepository repository) {
        String greeting(String id) {
            return repository.findName(id).map("Hello, "::concat).orElse("Hello, stranger");
        }
    }
}
