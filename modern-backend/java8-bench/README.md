# java8-bench

Standalone Java 8 scalar JMH benchmarks used to compare runtime performance across JDK 8 / 21 / 25.

This project is intentionally isolated from:
- the legacy Forge 1.12.2 Java 8 mod build (RetroFuturaGradle)
- the `modern-backend` Vector API experiments

## Run

From `modern-backend/` directory, examples:

- JDK 8:
  - `./gradlew -p java8-bench jmh -Pjava_toolchain=8`

- JDK 21:
  - `./gradlew -p java8-bench jmh -Pjava_toolchain=21`

- JDK 25:
  - `./gradlew -p java8-bench jmh -Pjava_toolchain=25`

You can also tune parameters:
- `-Pjmh_warmupIterations=...`
- `-Pjmh_iterations=...`
- `-Pjmh_warmup=1s`
- `-Pjmh_time=1s`
- `-Pjmh_fork=...`
- `-Pjmh_jvmArgsExtra="-XX:-UseSuperWord"`
