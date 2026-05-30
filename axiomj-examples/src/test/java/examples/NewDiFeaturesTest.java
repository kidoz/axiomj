package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Inject;
import su.kidoz.axiomj.api.Named;
import su.kidoz.axiomj.api.Profile;
import su.kidoz.axiomj.api.TestLogger;
import su.kidoz.axiomj.api.UseConfig;
import su.kidoz.axiomj.api.UseModules;
import su.kidoz.axiomj.api.Value;
import su.kidoz.axiomj.di.Binder;
import su.kidoz.axiomj.di.TestModule;

@Feature(id = "di.new", name = "New DI features")
@UseConfig("test.properties")
@UseModules({NewDiFeaturesTest.ProdModule.class, NewDiFeaturesTest.TestModuleActive.class})
class NewDiFeaturesTest {

    record DbConfig(String url, int poolSize) {}

    @Profile("prod")
    public static class ProdModule implements TestModule {
        @Override
        public void configure(Binder binder) {
            binder.bindInstance(String.class, "Env", "Production");
        }
    }

    public static class TestModuleActive implements TestModule {
        @Override
        public void configure(Binder binder) {
            binder.bindInstance(String.class, "Env", "Test");
        }
    }

    @Fact
    void testLoggerIsInjectableAndCaptured(@Inject TestLogger logger) {
        logger.info("This is an info message");
        logger.error("This is an error", new RuntimeException("Oops"));

        String log = logger.getOutput();
        expect(log).contains("[INFO] This is an info message");
        expect(log).contains("[ERROR] This is an error");
        expect(log).contains("java.lang.RuntimeException: Oops");
    }

    @Fact
    void configIsCoercedToRecord(@Value("db") DbConfig db) {
        expect(db.url()).isEqualTo("jdbc:test");
        expect(db.poolSize()).isEqualTo(10);
    }

    @Fact
    void profilesAreRespected(@Inject @Named("Env") String env) {
        // Because "prod" profile is not passed, ProdModule is ignored,
        // and TestModuleActive provides "Test".
        expect(env).isEqualTo("Test");
    }
}
