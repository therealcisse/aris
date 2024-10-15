# CQRS Profiling

## How to Profile Using YourKit

1. [Download](https://www.yourkit.com/java/profiler/download/) and install YourKit on your machine.
2. Start YourKit and [create a remote application](https://www.yourkit.com/docs/java-profiler/2022.9/help/direct_connect.jsp) using the default settings, name it whatever you like.
3. `cd {cqrs project}/profiling`
4. Run `./build-docker-image.sh`.  This will create a local Docker image `cqrs:{head commit sha}` and an additional tag, `cqrs:latest`.
5. Run `run-cqrs.sh`.  This will start the benchmark server, exposed on port `8080`, along with the profiler port exposed on `10001`.

If you now switch back to YourKit, you should see that it has detected the cqrs application.  You should now be able to perform whatever profiling you desire.

