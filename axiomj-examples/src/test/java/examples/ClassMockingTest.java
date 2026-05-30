package examples;

import static su.kidoz.axiomj.assertions.Expect.expect;

import su.kidoz.axiomj.api.Fact;
import su.kidoz.axiomj.api.Feature;
import su.kidoz.axiomj.api.ProductArea;
import su.kidoz.axiomj.api.Scenario;
import su.kidoz.axiomj.mock.Arg;
import su.kidoz.axiomj.mock.Mocks;
import su.kidoz.axiomj.mock.bytecode.BytecodeMocks;

@ProductArea("Testing")
@Feature(id = "mock.bytecode", name = "Class mocking", owner = "core-team")
public final class ClassMockingTest {

    @Fact(name = "mock a concrete class")
    @Scenario("a bytecode class mock reuses the core stubbing and verification engine")
    void mockConcreteClass() {
        var calc = BytecodeMocks.mockClass(Calculator.class);
        Mocks.when(() -> calc.add(Arg.anyInt(), Arg.anyInt())).thenReturn(42);

        expect(calc.add(1, 2)).isEqualTo(42);
        expect(calc.add(5, 5)).isEqualTo(42);
        Mocks.verify(() -> calc.add(Arg.anyInt(), Arg.anyInt())).calledTimes(2);
        Mocks.verifyNoMoreInteractions(calc);
    }

    @Fact(name = "unstubbed class method returns default")
    @Scenario("an unstubbed method on a class mock returns the type default, not the real result")
    void unstubbedReturnsDefault() {
        var calc = BytecodeMocks.mockClass(Calculator.class);

        expect(calc.add(2, 2)).isEqualTo(0);
    }

    @Fact(name = "constructor-free instantiation")
    @Scenario("Objenesis allows mocking classes that have no default constructor")
    void constructorFreeInstantiation() {
        var dao = BytecodeMocks.mockClass(ComplexDao.class);
        Mocks.when(() -> dao.fetchData()).thenReturn("mocked");
        expect(dao.fetchData()).isEqualTo("mocked");
    }

    @Fact(name = "mock a static method")
    @Scenario("Instrumentation agent allows mocking static methods")
    void mockStaticMethod() {
        su.kidoz.axiomj.mock.bytecode.StaticMocks.mockStatic(StaticUtil.class);
        try {
            Mocks.when(() -> StaticUtil.hello("World")).thenReturn("Mocked World");
            expect(StaticUtil.hello("World")).isEqualTo("Mocked World");
            Mocks.verify(() -> StaticUtil.hello("World")).calledOnce();
        } finally {
            su.kidoz.axiomj.mock.bytecode.StaticMocks.unmockStatic(StaticUtil.class);
        }
    }

    @Fact(name = "mock construction")
    @Scenario("Instrumentation agent intercepts constructor calls to return a mock")
    void mockConstruction() {
        su.kidoz.axiomj.mock.bytecode.ConstructorMocks.mockConstruction(Calculator.class);
        try {
            Calculator calc = new Calculator();
            Mocks.when(() -> calc.add(Arg.anyInt(), Arg.anyInt())).thenReturn(99);
            expect(calc.add(10, 20)).isEqualTo(99);
            Mocks.verify(() -> calc.add(10, 20)).calledOnce();
        } finally {
            su.kidoz.axiomj.mock.bytecode.ConstructorMocks.unmockConstruction(Calculator.class);
        }
    }

    public static class StaticUtil {
        public static String hello(String name) {
            return "Real " + name;
        }
    }

    public static class ComplexDao {
        private final String connectionString;

        public ComplexDao(String connectionString) {
            if (connectionString == null || connectionString.isBlank()) {
                throw new IllegalArgumentException("Connection string cannot be empty");
            }
            this.connectionString = connectionString;
        }

        public String fetchData() {
            return "real data from " + connectionString;
        }
    }

    public static class Calculator {
        public int add(int a, int b) {
            return a + b;
        }
    }
}
