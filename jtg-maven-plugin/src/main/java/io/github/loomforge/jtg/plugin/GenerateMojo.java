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
 * <p>Maven runs this goal only when it is <em>bound</em> under {@code <executions>}
 * or when you invoke it explicitly ({@code mvn jtg:generate}). This Mojo declares
 * {@code defaultPhase = generate-sources}: if an execution omits {@code <phase>},
 * Maven binds the goal to {@code generate-sources}, so a normal
 * {@code mvn compile} / {@code mvn package} then triggers generation before
 * compilation.</p>
 *
 * <h2>Typical: bind to the lifecycle</h2>
 * <p>Matches the project README — generation runs on every build.</p>
 * <pre>{@code
 * <plugin>
 *   <groupId>io.github.loomforge</groupId>
 *   <artifactId>jtg-maven-plugin</artifactId>
 *   <version>${version}</version>
 *   <executions>
 *     <execution>
 *       <phase>generate-sources</phase>
 *       <goals><goal>generate</goal></goals>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 * <p>You may omit {@code <phase>} here; it then defaults to {@code generate-sources}
 * from the Mojo metadata.</p>
 *
 * <h2>Without lifecycle binding</h2>
 * <p>Declare the plugin only (no {@code <executions>}) and run {@code mvn jtg:generate}
 * when you want TypeScript emitted.</p>
 * <pre>{@code
 * <plugin>
 *   <groupId>io.github.loomforge</groupId>
 *   <artifactId>jtg-maven-plugin</artifactId>
 *   <version>${version}</version>
 * </plugin>
 * }</pre>
 *
 * <h2>Override the phase (optional)</h2>
 * <pre>{@code
 * <executions>
 *   <execution>
 *     <id>jtg-generate</id>
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
    @Parameter(property = "jtg.additionalSourceDirs")
    private List<String> additionalSourceDirs;

    /**
     * If {@code true}, the plugin logs extra detail about every file it scans.
     */
    @Parameter(property = "jtg.verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * If {@code true}, skip execution entirely (e.g. {@code -Djtg.skip=true}).
     */
    @Parameter(property = "jtg.skip", defaultValue = "false")
    private boolean skip;

    // -------------------------------------------------------------------------

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("JTG: skipping generation (jtg.skip=true)");
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

        getLog().info(String.format(
            "JTG: done — scanned %d file(s), found %d @TsRecord record(s), generated %d .ts file(s).",
            filesScanned, recordsFound, filesGenerated
        ));

        if (!errors.isEmpty()) {
            getLog().warn("JTG: " + errors.size() + " file(s) had errors. See warnings above.");
        }
    }

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
