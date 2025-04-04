plugins {
    java
    application
}

group = "org.vineflower"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

application { 
    mainClass = "org.vineflower.tools.minifier.Minifier"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("org.fusesource.jansi:jansi:2.4.1")
    compileOnly("org.jspecify:jspecify:1.0.0")
}
