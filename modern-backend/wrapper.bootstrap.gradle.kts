// Minimal build used only for generating the sandbox Gradle wrapper from the parent build.

tasks.wrapper {
    gradleVersion = "9.2.1"
    distributionType = Wrapper.DistributionType.BIN
}
