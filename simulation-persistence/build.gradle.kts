plugins {
    `java-library`
}

dependencies {
    api(project(":simulation-core"))
    implementation(libs.jackson.databind)
}
