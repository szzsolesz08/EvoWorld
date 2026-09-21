plugins {
    application
}

dependencies {
    implementation(project(":simulation-core"))
    implementation(project(":simulation-persistence"))
    implementation(libs.slf4j.api)
    // Compile-time, not just runtime: the runner sets log levels from the command line.
    implementation(libs.logback.classic)
}

application {
    mainClass.set("dev.evoworld.runner.EvoWorldRunner")
}

// ./gradlew survey [-Pseeds=1-20] [-Pscenarios=baseline,wounded] [-Pticks=N] [-Pdetail] [-Pout=docs/survey.md]
tasks.register<JavaExec>("survey") {
    group = "verification"
    description = "Runs every scenario on every seed and reports how the agent fared."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.evoworld.runner.survey.Survey")
    // Paths in -Pout are relative to the repository, not to this module.
    workingDir = rootProject.projectDir
    // Always a fresh measurement: the report depends on the code, not on inputs Gradle can see.
    outputs.upToDateWhen { false }

    val forwarded = mapOf(
        "seeds" to "--seeds",
        "scenarios" to "--scenarios",
        "ticks" to "--ticks",
        "out" to "--out",
        "threads" to "--threads",
    )
    val arguments = mutableListOf<String>()
    forwarded.forEach { (property, flag) ->
        project.findProperty(property)?.let { arguments += listOf(flag, it.toString()) }
    }
    if (project.hasProperty("detail")) {
        arguments += "--detail"
    }
    args = arguments
}
