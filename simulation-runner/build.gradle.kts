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
