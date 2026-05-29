package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.List;
import java.util.function.Supplier;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Inject;
import su.kidoz.axiomj.api.Named;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.api.UseConfig;
import su.kidoz.axiomj.api.UseModules;
import su.kidoz.axiomj.api.Value;
import su.kidoz.axiomj.di.Binder;
import su.kidoz.axiomj.di.Config;
import su.kidoz.axiomj.di.TestModule;

@ProductArea("Testing")
@Feature(id = "di.advanced", name = "Named, multi-binding, and config injection", owner = "core-team")
@UseConfig("test.properties")
@UseModules(DiAdvancedTest.Module.class)
public final class DiAdvancedTest {

    @Inject
    @Named("stripe")
    PaymentGateway gateway;

    @Inject
    List<Validator> validators;

    @Inject
    Config config;

    @Value("db.url")
    String dbUrl;

    @Value("db.poolSize")
    int poolSize;

    @Value("feature.beta")
    boolean betaEnabled;

    @Value(value = "missing.key", orElse = "fallback")
    String fallback;

    @Fact(name = "named binding selects the right implementation")
    @Scenario("@Named picks one of several bindings of the same type")
    void namedBinding() {
        expect(gateway.name()).isEqualTo("stripe");
    }

    @Fact(name = "multi-binding injects all implementations")
    @Scenario("bindAll exposes every provider as an injectable List")
    void multiBinding() {
        expect(validators).hasSize(2);
        expect(validators).allSatisfy(validator -> expect(validator.ok("x")).isTrue());
    }

    @Fact(name = "config values are injected and coerced")
    @Scenario("@Value injects coerced config; defaults apply for missing keys")
    void configInjection() {
        expect(dbUrl).isEqualTo("jdbc:test");
        expect(poolSize).isEqualTo(10);
        expect(betaEnabled).isTrue();
        expect(fallback).isEqualTo("fallback");
        expect(config.getInt("db.poolSize")).isEqualTo(10);
    }

    interface PaymentGateway {
        String name();
    }

    record NamedGateway(String name) implements PaymentGateway {}

    interface Validator {
        boolean ok(String input);
    }

    static final class AlwaysOk implements Validator {
        @Override
        public boolean ok(String input) {
            return true;
        }
    }

    static final class AlsoOk implements Validator {
        @Override
        public boolean ok(String input) {
            return true;
        }
    }

    public static final class Module implements TestModule {
        @Override
        public void configure(Binder binder) {
            binder.bind(PaymentGateway.class, "stripe", () -> new NamedGateway("stripe"));
            binder.bind(PaymentGateway.class, "paypal", () -> new NamedGateway("paypal"));
            binder.bindAll(Validator.class, List.<Supplier<? extends Validator>>of(AlwaysOk::new, AlsoOk::new));
        }
    }
}
