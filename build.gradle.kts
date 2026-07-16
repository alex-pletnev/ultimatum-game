plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.4"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    kotlin("kapt") version "2.1.20"
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
    jacoco
}

group = "edu.itmo"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val jjwtVersion = "0.11.5"
val mapstructVersion = "1.6.3"
val springwolfVersion = "1.11.0"

dependencies {
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.7.0")
    implementation("io.github.springwolf:springwolf-stomp:$springwolfVersion")
    implementation("io.github.springwolf:springwolf-ui:$springwolfVersion")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-messaging")
    implementation("org.springframework.security:spring-security-config:6.1.0")

    implementation("org.apache.commons:commons-csv:1.11.0")

    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.13.13")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-noarg")

    implementation("org.mapstruct:mapstruct:$mapstructVersion")
    kapt("org.mapstruct:mapstruct-processor:$mapstructVersion")

    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

kapt { correctErrorTypes = true }

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("snapshot")
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register<Test>("generateApiSnapshots") {
    description = "Regenerates src/main/resources/doc/{openapi,asyncapi}.json from live Spring context"
    group = "documentation"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("snapshot")
    }
    outputs.upToDateWhen { false }
    finalizedBy("copyApiSnapshotsToFrontendIntegration")
}

tasks.register<Copy>("copyApiSnapshotsToFrontendIntegration") {
    description = "Copies generated openapi/asyncapi.json to frontend-integration/specs for external consumers"
    group = "documentation"
    from("src/main/resources/doc/openapi.json", "src/main/resources/doc/asyncapi.json")
    into(layout.projectDirectory.dir("frontend-integration/specs"))
}

// ----- JaCoCo (T-012): coverage бизнес-логики -----
//
// Scope: services/* + model.ShuffleStrategy (см. docs/tasks/T-012).
// Инфра/data-слои исключены: configs, controllers, dto, util (mappers),
// repositories, exceptions, JPA-модели.
//
// Порог line coverage 0.80 — ломает `check` при просадке.

jacoco {
    toolVersion = "0.8.12"
}

val coverageIncludes = listOf(
    "edu/itmo/ultimatumgame/services/**",
    "edu/itmo/ultimatumgame/model/ShuffleStrategy*",
    "edu/itmo/ultimatumgame/model/FreeForAllStrategy*",
    "edu/itmo/ultimatumgame/model/TeamBattleStrategy*"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { include(coverageIncludes) }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { include(coverageIncludes) }
        })
    )
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ----- detekt (T-014): статический анализ Kotlin -----
//
// Дефолтные правила + detekt-formatting (обёртка над ktlint).
// Baseline вычищен в T-015; исключения оформлены точечными @Suppress
// / @file:Suppress в коде.

detekt {
    toolVersion = "1.23.7"
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}

// Плагин detekt автоматически добавляет дефолтный `:detekt` таск (без type
// resolution) в `check`. Мы работаем только через type-resolution версии
// (`detektMain`/`detektTest`) — baseline сгенерирован именно под них.
// Отключаем дефолтный, чтобы `check` не падал на нём.
tasks.named("detekt") { enabled = false }

tasks.check {
    dependsOn(tasks.named("detektMain"), tasks.named("detektTest"))
}

// detekt 1.23.7 скомпилирован с Kotlin 2.0.10; в classpath проекта — 2.1.20
// (тянет `kotlin("kapt") version 2.1.20`, рассинхрон с основным `jvm 1.9.25` — см. T-016).
// Форсируем Kotlin в detekt-конфигурациях, чтобы плагин запустился.
configurations.matching { it.name.startsWith("detekt") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion("2.0.10")
        }
    }
}
