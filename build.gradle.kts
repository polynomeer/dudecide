import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// Flyway 10+ ships database support as separate modules. The Gradle plugin only sees the
// buildscript classpath, so `flywayMigrate` needs the PostgreSQL module and driver here.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.10.5")
        classpath("org.postgresql:postgresql:42.7.7")
    }
}

plugins {
    kotlin("jvm") version "2.1.21" apply false
    kotlin("plugin.spring") version "2.1.21" apply false
    id("org.springframework.boot") version "3.5.5" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("nu.studer.jooq") version "9.0" apply false
    id("org.flywaydb.flyway") version "11.10.5" apply false
}

allprojects {
    group = "com.dudecide"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
