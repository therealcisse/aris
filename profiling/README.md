# Profiling

## How to Profile Using YourKit

1. [Download](https://www.yourkit.com/java/profiler/download/) and install YourKit on your machine.
2. Start YourKit and [create a remote application](https://www.yourkit.com/docs/java-profiler/2022.9/help/direct_connect.jsp) using the default settings, name it whatever you like.
3. `cd {youtoo project}/profiling`
4. Run `./build-docker-image.sh ingestion yourkit` or `./build-docker-image.sh migration yourkit`.  This will create a local Docker image `youtoo-profiling:{head commit sha}` and an additional tag, `youtoo-profiling:latest`.
5. Run `start-loadtests.sh`.  This will start the benchmark server, exposed on port `8181`, along with the profiler port exposed on `10001`.

If you now switch back to YourKit, you should see that it has detected the youtoo application.  You should now be able to perform whatever profiling you desire.


## How to Profile Using JFR

1. `cd {youtoo project}/profiling`
2. Run `./build-docker-image.sh ingestion jfr` or `./build-docker-image.sh migration jfr`.  This will create a local Docker image `youtoo-profiling:{head commit sha}` and an additional tag, `youtoo-profiling:latest`.
3. Run `start-loadtests.sh`.  This will start the benchmark server, exposed on port `8181` and run a load test.
4. Run `./jfr/profile.sh run` to run profiling for 60 seconds. Profiling can be stopped with `./jfr/profile.sh stop`.
6. The flamegraph and recording can be found at `./.cache`

## How to Profile Using Async-Profiler

1. `cd {youtoo project}/profiling`
2. Run `./build-docker-image.sh ingestion asprof` or `./build-docker-image.sh migration asprof`.  This will create a local Docker image `youtoo-profiling:{head commit sha}` and an additional tag, `youtoo-profiling:latest`.
3. Run `start-loadtests.sh`.  This will start the benchmark server, exposed on port `8181` and run a load test.
4. Run `./asprof/profile.sh cpu` to run CPU profiling for 30 seconds. Profiling can be stopped with `./asprof/profile.sh stop`.
6. The report can be found at `./.cache/profile_cpu.html`

