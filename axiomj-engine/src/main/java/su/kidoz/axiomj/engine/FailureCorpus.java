package su.kidoz.axiomj.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FailureCorpus {
    private static final Path CORPUS_PATH = Path.of(".axiomj-corpus");

    private final Map<String, Set<Long>> failingSeeds = new ConcurrentHashMap<>();

    public FailureCorpus() {
        load();
    }

    private void load() {
        if (!Files.exists(CORPUS_PATH)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(CORPUS_PATH);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String methodId = parts[0].trim();
                    try {
                        long seed = Long.parseLong(parts[1].trim());
                        failingSeeds
                                .computeIfAbsent(methodId, k -> new LinkedHashSet<>())
                                .add(seed);
                    } catch (NumberFormatException _) {
                        // ignore malformed seeds
                    }
                }
            }
        } catch (IOException _) {
            // ignore unreadable corpus
        }
    }

    public void addFailure(String methodId, long seed) {
        failingSeeds.computeIfAbsent(methodId, k -> new LinkedHashSet<>()).add(seed);
    }

    public void removePass(String methodId, long seed) {
        Set<Long> seeds = failingSeeds.get(methodId);
        if (seeds != null) {
            seeds.remove(seed);
            if (seeds.isEmpty()) {
                failingSeeds.remove(methodId);
            }
        }
    }

    public List<Long> getSeeds(String methodId) {
        Set<Long> seeds = failingSeeds.get(methodId);
        return seeds == null ? List.of() : new ArrayList<>(seeds);
    }

    public void save() {
        try {
            List<String> lines = new ArrayList<>();
            lines.add("# AxiomJ property testing failure corpus");
            lines.add("# Automatically generated. Do not edit manually unless you know what you are doing.");
            for (Map.Entry<String, Set<Long>> entry : failingSeeds.entrySet()) {
                for (Long seed : entry.getValue()) {
                    lines.add(entry.getKey() + "=" + seed);
                }
            }
            if (lines.size() > 2) {
                Files.write(CORPUS_PATH, lines);
            } else {
                Files.deleteIfExists(CORPUS_PATH);
            }
        } catch (IOException _) {
            // ignore unwritable corpus
        }
    }
}
