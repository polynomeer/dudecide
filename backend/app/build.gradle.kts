plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
    id("nu.studer.jooq")
    id("org.flywaydb.flyway")
}

dependencies {
    implementation(project(":backend:shared"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.jooq:jooq:3.20.5")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    jooqGenerator("org.postgresql:postgresql:42.7.7")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

jooq {
    version.set("3.20.5")
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)
            jooqConfiguration.apply {
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = providers.gradleProperty("jooq.jdbc.url").orNull ?: "jdbc:postgresql://localhost:5432/dudecide"
                    user = providers.gradleProperty("jooq.jdbc.user").orNull ?: "dudecide"
                    password = providers.gradleProperty("jooq.jdbc.password").orNull ?: "dudecide"
                }
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "flyway_schema_history"
                    }
                    generate.apply {
                        isPojos = true
                        isDaos = false
                        isRecords = true
                        isFluentSetters = false
                    }
                    target.apply {
                        packageName = "com.dudecide.app.jooq"
                        directory = "${layout.buildDirectory.get().asFile.path}/generated-src/jooq/main"
                    }
                }
            }
        }
    }
}

flyway {
    url = providers.gradleProperty("flyway.jdbc.url").orNull ?: "jdbc:postgresql://localhost:5432/dudecide"
    user = providers.gradleProperty("flyway.jdbc.user").orNull ?: "dudecide"
    password = providers.gradleProperty("flyway.jdbc.password").orNull ?: "dudecide"
    locations = arrayOf("filesystem:${projectDir}/src/main/resources/db/migration")
}

tasks.named("generateJooq") {
    dependsOn("flywayMigrate")
}
