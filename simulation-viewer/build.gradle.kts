dependencies {
    implementation(project(":simulation-core"))
    implementation(project(":simulation-persistence"))
    // Reuses the console's world building, the survey's scenarios and its log levels.
    implementation(project(":simulation-runner"))
    implementation(libs.slf4j.api)
    implementation(libs.gdx.core)
    implementation(libs.gdx.backend.lwjgl3)
    runtimeOnly(variantOf(libs.gdx.platform) { classifier("natives-desktop") })
}

// ./gradlew view [--args="--seed 7 --scenario wounded"]
// Not the application plugin: its "run" task would also answer ./gradlew run,
// opening a window every time someone wanted the console.
tasks.register<JavaExec>("view") {
    group = "application"
    description = "Opens a window and plays a run live."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.evoworld.viewer.ViewerLauncher")
    // Saves and loads are relative to the repository, as they are for the console.
    workingDir = rootProject.projectDir
}
