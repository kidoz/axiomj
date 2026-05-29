package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;
import static su.kidoz.axiomj.assertions.Expect.expectThrown;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Mock;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.mock.Arg;
import su.kidoz.axiomj.mock.ArgumentCaptor;
import su.kidoz.axiomj.mock.Mocks;

@ProductArea("Testing")
@Feature(id = "mock.matchers", name = "Mock argument matchers", owner = "core-team")
public final class MockingFeaturesTest {

    @Fact(name = "matchers and thenThrow")
    @Scenario("any() matches everything; a later exact stub overrides it and can throw")
    void matchersAndThrow(@Mock UserRepository repo) {
        Mocks.when(() -> repo.findName(Arg.any())).thenReturn(Optional.of("Ada"));
        Mocks.when(() -> repo.findName("boom")).thenThrow(new IllegalStateException("nope"));

        expect(repo.findName("anyone")).isPresent().hasValue("Ada");
        expectThrown(() -> repo.findName("boom")).isInstanceOf(IllegalStateException.class);
    }

    @Fact(name = "sequential returns")
    @Scenario("thenReturn(a, b) yields a then b, repeating the last")
    void sequentialReturns(@Mock UserRepository repo) {
        Mocks.when(() -> repo.findName("u")).thenReturn(Optional.of("a"), Optional.of("b"));

        expect(repo.findName("u")).hasValue("a");
        expect(repo.findName("u")).hasValue("b");
        expect(repo.findName("u")).hasValue("b");
    }

    @Fact(name = "captor and verification modes")
    @Scenario("captures arguments and verifies call counts with matchers")
    void captorAndVerificationModes(@Mock UserRepository repo) {
        Mocks.when(() -> repo.save(Arg.matches((String id) -> id.startsWith("u")), Arg.anyInt()))
                .thenReturn(true);

        expect(repo.save("u1", 20)).isTrue();
        repo.save("u2", 30);

        var ids = ArgumentCaptor.<String>forClass(String.class);
        Mocks.verify(() -> repo.save(Arg.capture(ids), Arg.anyInt())).calledTimes(2);
        expect(ids.values()).isEqualTo(List.of("u1", "u2"));

        Mocks.verify(() -> repo.save(Arg.any(), Arg.anyInt())).atLeast(2);
        Mocks.verify(() -> repo.save(Arg.any(), Arg.anyInt())).atMost(2);
    }

    @Fact(name = "no more interactions")
    @Scenario("verifyNoMoreInteractions passes once every call is verified")
    void noMoreInteractions(@Mock UserRepository repo) {
        repo.touch("a");

        Mocks.verify(() -> repo.touch("a")).calledOnce();
        Mocks.verifyNoMoreInteractions(repo);
    }

    @Fact(name = "mixed sequential answers")
    @Scenario("thenReturn then thenThrow are consumed in order")
    void mixedSequencing(@Mock UserRepository repo) {
        Mocks.when(() -> repo.findName("k"))
                .thenReturn(Optional.of("first"))
                .thenThrow(new IllegalStateException("boom"));

        expect(repo.findName("k")).hasValue("first");
        expectThrown(() -> repo.findName("k")).isInstanceOf(IllegalStateException.class);
    }

    @Fact(name = "lenient mocks tolerate unused stubs")
    @Scenario("strict=false allows configured-but-unused stubs")
    void lenientUnusedStub(@Mock(strict = false) UserRepository repo) {
        Mocks.when(() -> repo.findName("never-called")).thenReturn(Optional.of("X"));

        expect(repo.findName("other")).isEmpty();
    }

    @Fact(name = "spy delegates unstubbed calls")
    @Scenario("a spy runs the real method unless a stub overrides it")
    void spyPartialMock() {
        var greeter = Mocks.spy(Greeter.class, new RealGreeter());
        Mocks.when(() -> greeter.greet("Ada")).thenReturn("Stubbed");

        expect(greeter.greet("Ada")).isEqualTo("Stubbed");
        expect(greeter.farewell("Ada")).isEqualTo("Bye, Ada");
        Mocks.verify(() -> greeter.greet("Ada")).calledOnce();
    }

    @Fact(name = "BDD given/willReturn alias")
    @Scenario("given(...).willReturn(...) reads as a stubbing alias for when(...)")
    void bddGivenWillReturn(@Mock UserRepository repo) {
        Mocks.given(() -> repo.findName("ada")).willReturn(Optional.of("Ada"));

        expect(repo.findName("ada")).hasValue("Ada");
    }

    @Fact(name = "in-order verification")
    @Scenario("verifies calls occurred in the expected sequence")
    void inOrderVerification(@Mock UserRepository repo) {
        repo.touch("first");
        repo.findName("second");
        repo.touch("third");

        var order = Mocks.inOrder(repo);
        order.verify(() -> repo.touch("first"));
        order.verify(() -> repo.findName("second"));
        order.verify(() -> repo.touch("third"));
    }

    @Fact(name = "deep stubs follow chained calls")
    @Scenario("a deep mock stubs a chained call without mocking each link")
    void deepStubs() {
        var account = Mocks.mockDeep(Account.class);
        Mocks.when(() -> account.owner().name()).thenReturn("Ada");

        expect(account.owner().name()).isEqualTo("Ada");
    }

    @Fact(name = "timed verification waits for async calls")
    @Scenario("within(...) waits for an interaction produced on another thread")
    void timedVerification(@Mock UserRepository repo) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            repo.touch("async");
        });

        Mocks.verify(() -> repo.touch("async")).within(Duration.ofSeconds(2)).calledOnce();
    }

    interface UserRepository {
        Optional<String> findName(String id);

        boolean save(String id, int age);

        void touch(String id);
    }

    interface Greeter {
        String greet(String name);

        String farewell(String name);
    }

    interface Account {
        Owner owner();
    }

    interface Owner {
        String name();
    }

    static final class RealGreeter implements Greeter {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }

        @Override
        public String farewell(String name) {
            return "Bye, " + name;
        }
    }
}
