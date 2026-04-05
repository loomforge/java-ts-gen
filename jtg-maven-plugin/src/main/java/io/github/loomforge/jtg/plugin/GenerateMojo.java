package io.github.loomforge.jtg.plugin;

import io.github.loomforge.jtg.plugin.RecordParser.RecordDefinition;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Maven goal: {@code jtg:generate}
 *
 * <p>Scans all Java source roots in the current project for records annotated
 * with {@code @TsRecord} and generates a corresponding {@code .ts} file in the
 * same directory as the Java source file.</p>
 *
 * <p>Bound to the {@code generate-sources} phase by default, so it runs
 * automatically as part of any standard build ({@code mvn compile},
 * {@code mvn package}, etc.). Can still be invoked explicitly via
 * {@code mvn jtg:generate}.</p>
 *
 * <h2>Minimal consumer configuration</h2>
 * <pre>{@code
 * <plugin>
 *   <groupId>io.github.tsforge</groupId>
 *   <artifactId>forge-maven-plugin</artifactId>
 *   <version>0.1.0</version>
 * </plugin>
 * }</pre>
 *
 * <h2>Override the phase (optional)</h2>
 * <pre>{@code
 * <executions>
 *   <execution>
 *     <id>forge-generate</id>
 *     <phase>process-sources</phase>
 *     <goals><goal>generate</goal></goals>
 *   </execution>
 * </executions>
 * }</pre>
 */
@Mojo(
    name = "generate",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES,
    threadSafe = true
)
public class GenerateMojo extends AbstractMojo {

    /**
     * The current Maven project — injected automatically.
     * Provides access to source roots and project metadata.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Additional source directories to scan, beyond the project's default source roots.
     * Optional — usually not needed.
     */
    @Parameter(property = "forge.additionalSourceDirs")
    private List<String> additionalSourceDirs;

    /**
     * If {@code true}, the plugin logs extra detail about every file it scans.
     */
    @Parameter(property = "forge.verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * If {@code true}, skip execution entirely (e.g. {@code -Dforge.skip=true}).
     */
    @Parameter(property = "forge.skip", defaultValue = "false")
    private boolean skip;

    // -------------------------------------------------------------------------

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("JTG: skipping generation (forge.skip=true)");
            return;
        }

        List<Path> sourceRoots = collectSourceRoots();
        if (sourceRoots.isEmpty()) {
            getLog().warn("JTG: no source roots found — nothing to do.");
            return;
        }

        getLog().info("JTG: scanning " + sourceRoots.size() + " source root(s)...");

        int filesScanned = 0;
        int recordsFound = 0;
        int filesGenerated = 0;
        List<String> errors = new ArrayList<>();

        for (Path root : sourceRoots) {
            if (!Files.isDirectory(root)) continue;

            try (Stream<Path> walker = Files.walk(root)) {
                List<Path> javaFiles = walker
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

                for (Path javaFile : javaFiles) {
                    filesScanned++;
                    if (verbose) {
                        getLog().debug("JTG: scanning " + javaFile);
                    }

                    try {
                        List<RecordDefinition> defs = RecordParser.parse(javaFile);
                        recordsFound += defs.size();

                        for (RecordDefinition def : defs) {
                            Path tsFile = TsEmitter.emit(def);
                            filesGenerated++;
                            getLog().info("JTG: generated " + tsFile.toAbsolutePath());
                        }

                    } catch (IOException e) {
                        String msg = "Failed to process " + javaFile + ": " + e.getMessage();
                        errors.add(msg);
                        getLog().warn("JTG: " + msg);
                    }
                }

            } catch (IOException e) {
                throw new MojoExecutionException(
                    "JTG: error walking source root " + root, e);
            }
        }

        // Summary
        getLog().info(String.format(
            "JTG: done — scanned %d file(s), found %d @TsRecord record(s), generated %d .ts file(s).",
            filesScanned, recordsFound, filesGenerated
        ));

        if (!errors.isEmpty()) {
            getLog().warn("JTG: " + errors.size() + " file(s) had errors. See warnings above.");
        }
    }

    // -------------------------------------------------------------------------

    private List<Path> collectSourceRoots() {
        List<Path> roots = new ArrayList<>();

        // Standard Maven source roots (src/main/java, etc.)
        for (String dir : project.getCompileSourceRoots()) {
            roots.add(Path.of(dir));
        }

        // Any explicitly configured additional dirs
        if (additionalSourceDirs != null) {
            for (String dir : additionalSourceDirs) {
                roots.add(Path.of(dir));
            }
        }

        return roots;
    }
}
