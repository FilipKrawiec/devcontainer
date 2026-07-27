plugins {
    kotlin("jvm") version "2.1.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("devcli.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
