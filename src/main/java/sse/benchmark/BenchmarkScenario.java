package sse.benchmark;

public interface BenchmarkScenario {

    String name();

    BenchmarkOperation operation();

    void run(BenchmarkConfig config, BenchmarkResultWriter resultWriter) throws Exception;
}
