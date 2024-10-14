package pro.toparvion.util.heaptalk;

import static java.util.stream.Collectors.joining;

import org.netbeans.lib.profiler.heap.JavaClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * @author Vladimir Plizga
 */
@Command(name = "heaptalk", mixinStandardHelpOptions = true, version = "0.1",
        description = "SQL schema and queries generator for JVM heap dumps")
public class HeapTalk implements Runnable {

    @Parameters(index = "0", description = "Dump file to extract DDL from")
    private String dumpPath;
    
    @Parameters(index = "1", arity = "1", description = "Base packages of the application (comma separated)")
    private List<String> basePackages;
    
    @Parameters(index = "2", description = "Question to the dump (in human words)")
    private String question;
    
    @Option(names = {"-k", "--api-key"}, description = "API key for OpenAI or Proxy API")
    private String apiKey;
    
    @Option(names = {"-p", "--proxy-api"}, description = "Use Proxy API")
    private boolean useProxyApi;
    
    @Option(names = {"-v", "--verbose"}, description = "Print additional info, including DDL statements")
    private boolean verbose;
    
    @Override
    public void run() {
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
        }
        Logger log = LoggerFactory.getLogger(HeapTalk.class);
        log.debug("Dump path: {}, basePackages: {}, question: {}", dumpPath, basePackages, question);

        HeapSchemaExtractor extractor;
        try {
            extractor = new HeapSchemaExtractor(dumpPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open dump on path '%s'".formatted(dumpPath), e);
        }
        
        Set<JavaClass> appClasses = extractor.findAppClasses(basePackages);

        logAppClasses(log, appClasses);

        String classesDdl = appClasses.stream()
                .map(extractor::composeClassDdl)
                .collect(joining("\n"));
        log.debug("DDL for found classes:\n{}", classesDdl);
        
        AiService aiService = AiServiceProvider.provide(apiKey, useProxyApi, verbose);

        String sql = aiService.composeSql(classesDdl, question);
        if (verbose) {
            log.info("Generated SQL query:\n{}", sql);
        } else {
            System.out.println(sql);
        }
    }

    private static void logAppClasses(Logger log, Set<JavaClass> appClasses) {
        if (log.isTraceEnabled()) {
            String classesText = appClasses.stream()
                    .limit(50)
                    .map(JavaClass::getName)
                    .collect(joining("\n"));
            log.trace("Found {} classes (first 50):\n{}", appClasses.size(), classesText);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new HeapTalk()).execute(args);
        System.exit(exitCode);
    }
}
