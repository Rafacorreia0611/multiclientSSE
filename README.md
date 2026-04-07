# Byzantine Fault-Tolerant Searchable Symmetric Encryption

This repository contains a master's thesis prototype of a dynamic multi-client Searchable Symmetric Encryption (SSE) protocol built on top of [COBRA](https://github.com/bft-smart/cobra), a confidential Byzantine Fault-Tolerant state machine replication library.

The SSE implementation lives under [`src/main/java/sse`](/Users/rafacorreia0611/Documents/Tese/multiclientSSE/src/main/java/sse). It supports:

- search
- add
- delete

At a high level, this protocol combines SSE with COBRA's confidential replication layer. Confidential data and key material are protected through secret sharing across replicas, so no single server holds the full secret state or can execute the protocol alone. As in COBRA, the system tolerates Byzantine faults as long as `n > 3f + 1`.

## Project Status

This project is a research prototype developed in the context of a master's thesis. It is not production ready.

## Built on COBRA

This repository is a fork built on top of COBRA, and the SSE protocol should be understood as an application/protocol layer over COBRA's confidential BFT infrastructure.

Because of that, the following parts are essentially the same as in COBRA:

- environment and setup expectations
- compilation and packaging with Gradle
- local deployment layout under `build/local`
- runtime scripts such as `smartrun.sh` and `run.cmd`

For the original project and its full documentation, see the [COBRA repository](https://github.com/bft-smart/cobra).

## Requirements

This project is primarily implemented in Java and uses Gradle to compile, package, and deploy local test environments. The confidential layer also depends on native C code used by the pairing/commitment components exposed to Java through JNI.

The original COBRA project was tested with Java 11.0.13. This fork follows the same general build model.

## Compilation and Packaging

Inside the project root, you can:

- compile and package the project with `./gradlew installDist`
- prepare a local deployment with `./gradlew localDeploy`

The `localDeploy` task creates a `build/local` directory with replica folders `rep*` and client folders `cli*`.

### Native C Code

The project uses native C code for the constant commitment scheme through the [`relic`](https://github.com/relic-toolkit/relic) library. If your environment requires rebuilding these components:

1. Inside `pairing`, run `./build_relic.sh`
2. Then run `./build.sh <path to java folder>`


## Running the SSE Demo

After preparing the local deployment with `./gradlew localDeploy`, start the replicas and then launch a client.

### 1. Start the replicas

Run the following commands in four different terminals:

```bash
build/local/rep0$ ./smartrun.sh sse.demo.server.Server 0
build/local/rep1$ ./smartrun.sh sse.demo.server.Server 1
build/local/rep2$ ./smartrun.sh sse.demo.server.Server 2
build/local/rep3$ ./smartrun.sh sse.demo.server.Server 3
```

### 2. Start the client

Once all replicas are ready, start the interactive client:

```bash
build/local/cli0$ ./smartrun.sh sse.demo.client.Client 100
```

On Windows, use `run.cmd` instead of `./smartrun.sh`.

### 3. Use the interactive menu

The client initializes the SSE state when it starts and then exposes a simple interactive menu with:

- `Search`: retrieves the current result set for a keyword
- `Add`: inserts a document identifier for a keyword
- `Delete`: invalidates a previously added document identifier for a keyword
- `Exit`: closes the interactive client

## Assumptions and Limitations

- this is a master's thesis prototype
- the current design allows only one active client at a time
- liveness follows an obstruction-freedom assumption
- the protocol assumes honest clients
- as usual in SSE, some leakage is still present, including search pattern, access pattern, and volume pattern

## Acknowledgment

This work is built on top of COBRA. Credit for the confidential BFT replication layer, packaging workflow, and deployment model belongs to the COBRA project and its authors.
