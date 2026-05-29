package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import su.kidoz.axiomj.api.DependsBy;
import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.Order;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Requirement;
import su.kidoz.axiomj.api.Scenario;

@ProductArea("Identity")
@Feature(id = "identity.registration", name = "User registration workflow", owner = "identity-team")
public final class FeatureWorkflowTest {
    private static final Set<String> state = ConcurrentHashMap.newKeySet();

    @Fact(tags = "workflow")
    @Order(1)
    @Scenario("new account is created")
    @Requirement("REQ-ID-100")
    void createAccount() {
        state.add("account-created");
        expect(state.contains("account-created")).isTrue();
    }

    @Fact(tags = "workflow", dependsOn = "createAccount")
    @Order(2)
    @Scenario("welcome email waits for account creation")
    @Requirement("REQ-ID-101")
    void sendWelcomeEmail() {
        expect(state.contains("account-created")).isTrue();
        state.add("welcome-email-sent");
    }

    @Fact(tags = "workflow")
    @DependsBy("sendWelcomeEmail")
    @Order(3)
    @Scenario("audit event is written after welcome email")
    @Requirement("REQ-ID-102")
    void writeAuditEvent() {
        expect(state.contains("welcome-email-sent")).isTrue();
        state.add("audit-written");
    }
}
