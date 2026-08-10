import org.jooq.meta.jaxb.Property

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.spring")
    id("nu.studer.jooq")
    id("org.flywaydb.flyway")
}

val jooqVersion = "3.19.25"

dependencies {
    implementation(project(":backend:shared"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("org.jooq:jooq:$jooqVersion")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

    jooqGenerator("org.postgresql:postgresql:42.7.7")
    jooqGenerator("org.jooq:jooq-meta-extensions:$jooqVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.h2database:h2")
}

jooq {
    version.set(jooqVersion)
    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(false)
            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"
                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        properties = listOf(
                            Property().withKey("scripts").withValue("${projectDir}/src/main/resources/db/migration/*.sql"),
                            Property().withKey("sort").withValue("flyway"),
                            Property().withKey("defaultNameCase").withValue("lower"),
                        )
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

tasks.named("compileKotlin") {
    dependsOn("generateJooq")
}
