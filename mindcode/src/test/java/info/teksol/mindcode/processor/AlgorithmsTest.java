package info.teksol.mindcode.processor;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AlgorithmsTest extends AbstractProcessorTest {

    public static final String SCRIPTS_DIRECTORY = "src/test/resources/info/teksol/mindcode/processor/algorithms";

    protected String getScriptsDirectory() {
        return SCRIPTS_DIRECTORY;
    }

    @BeforeAll
    static void init() {
        AbstractProcessorTest.init();
    }

    @AfterAll
    static void done() throws IOException {
        AbstractProcessorTest.done(SCRIPTS_DIRECTORY, AlgorithmsTest.class.getSimpleName());
    }

    @Test
    void memoryBitReadTest() throws IOException {
        testAndEvaluateFile("bitmap-get.mnd",
                IntStream.range(0, 16).map(i -> i % 2).mapToObj(String::valueOf).collect(Collectors.toList())
        );
    }

    @Test
    void memoryBitReadWriteTest() throws IOException {
        testAndEvaluateFile("bitmap-get-set.mnd",
                IntStream.range(1, 17).map(i -> i % 2).mapToObj(String::valueOf).collect(Collectors.toList())
        );
    }

    void executeSortingAlgorithmTest(String fileName, int arrayLength) throws IOException {
        TestCompiler compiler = createTestCompiler();
        Random rnd = new Random(0);
        double[] array = rnd.ints().mapToDouble(i -> Math.abs(i) % 1000).limit(arrayLength).toArray();
        List<String> expectedOutput = Arrays.stream(array).mapToInt(d -> (int) d)
                .sorted().mapToObj(String::valueOf).toList();

        testAndEvaluateCode(
                compiler,
                "sorting with " + fileName,
                "SIZE = " + arrayLength + "\n" + readFile(fileName),
                List.of(MindustryMemory.createMemoryBank("bank2", array)),
                createEvaluator(compiler, expectedOutput),
                Path.of(getScriptsDirectory(), fileName.replace(".mnd", "") + ".log")
        );
    }

    @TestFactory
    public List<DynamicTest> sortsArrays() {
        final List<DynamicTest> result = new ArrayList<>();
        Map<String, Integer> definitions = Map.of(
                "bubble-sort.mnd", 64,
                "heap-sort.mnd", 512,
                "insert-sort.mnd", 128,
                "quick-sort.mnd", 512,
                "select-sort.mnd", 128
        );

//        Map<String, Integer> definitions = Map.of(
//                "heap-sort.mnd", 512
//        );

        for (final String script : definitions.keySet()) {
            result.add(DynamicTest.dynamicTest(script, null,
                    () -> executeSortingAlgorithmTest(script, definitions.get(script))));
        }

        return result;
    }

    @TestFactory
    public List<DynamicTest> computesScriptTests() {
        final List<DynamicTest> result = new ArrayList<>();
        final List<String> definitions = List.of(
                "memory-read-write.mnd", "10",
                "compute-recursive-fibonacci.mnd", "55",
                "compute-sum-of-primes.mnd", "21536"
        );

        for (int i = 0; i < definitions.size(); i += 2) {
            processFile(result, definitions.get(i), definitions.get(i + 1));
        }

        return result;
    }

    private void processFile(List<DynamicTest> result, String fileName, String expectedOutput) {
        result.add(DynamicTest.dynamicTest(fileName, null, () -> testAndEvaluateFile(
                fileName,
                s -> s,
                List.of(MindustryMemory.createMemoryBank("bank2")),
                List.of(expectedOutput)
        )));
    }
}
