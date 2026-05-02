package sse.benchmark;

import sse.benchmark.scenario.SearchLatencyByDocsScenario;
import sse.benchmark.scenario.UpdateLatencyByAssociationsScenario;

public final class BenchmarkClient {

    private BenchmarkClient() {
    }

    public static void main(String[] args) throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromArgs(args);
        BenchmarkScenario scenario = selectScenario(config);

        try (BenchmarkResultWriter resultWriter = new BenchmarkResultWriter(config.outputPath())) {
            scenario.writeHeader(resultWriter);
            scenario.run(config, resultWriter);
        }
    }

    static BenchmarkScenario selectScenario(BenchmarkConfig config) {
        String scenarioName = config.scenarioName();
        if ("search-latency-by-docs".equals(scenarioName)) {
            return new SearchLatencyByDocsScenario();
        }
        if ("update-latency-by-associations".equals(scenarioName)) {
            return new UpdateLatencyByAssociationsScenario();
        }

        throw new IllegalArgumentException("Unknown benchmark scenario: " + scenarioName);
    }
}
