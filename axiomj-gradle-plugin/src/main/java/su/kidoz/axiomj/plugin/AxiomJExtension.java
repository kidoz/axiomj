package su.kidoz.axiomj.plugin;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import javax.inject.Inject;

public abstract class AxiomJExtension {
    public abstract Property<Integer> getParallelism();

    public abstract Property<Boolean> getFailFast();

    public abstract Property<Long> getSeed();

    @Inject
    public AxiomJExtension(ObjectFactory objects) {
        getParallelism().convention(4);
        getFailFast().convention(false);
    }
}
