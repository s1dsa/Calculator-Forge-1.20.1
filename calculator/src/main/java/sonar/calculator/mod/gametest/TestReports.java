package sonar.calculator.mod.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** Actual execution artifacts; no parity verdict is inferred from a successful smoke test. */
final class TestReports {
    static final String RUN_ID = UUID.randomUUID().toString();
    private TestReports() {}

    static JsonObject report(String scope) {
        JsonObject result = new JsonObject();
        result.addProperty("schema_version", 1);
        result.addProperty("run_id", RUN_ID);
        result.addProperty("generated_at", Instant.now().toString());
        result.addProperty("minecraft", "1.20.1");
        result.addProperty("java", System.getProperty("java.version"));
        result.addProperty("scope", scope);
        result.addProperty("parity_proven", false);
        return result;
    }

    static void write(String name, JsonObject value) {
        Path directory = directory();
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve(name + ".json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(value) + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write actual GameTest report to " + directory, exception);
        }
    }

    private static Path directory() {
        String explicit = System.getProperty("calculator.testReports");
        if (explicit != null) return Path.of(explicit).toAbsolutePath();
        for (Path cursor = Path.of("").toAbsolutePath(); cursor != null; cursor = cursor.getParent()) {
            if (Files.isRegularFile(cursor.resolve("IMPLEMENTATION-CONTRACT.md")))
                return cursor.resolve("build/reports/calculator-gametest");
        }
        return Path.of("build/reports/calculator-gametest").toAbsolutePath();
    }
}
