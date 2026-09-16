plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    // Java 21 LTS. Compilation uses the toolchain, so the JDK that launches
    // Gradle (25 on this machine) doesn't have to be the one that compiles.
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JdbcClient + HikariCP: raw SQL, no ORM.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // HS256 JWTs, carrying the {email, exp, iat} claims the API contract pins.
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // scrypt for the Node-compatible `hex(salt):hex(key)` hash format. The JDK
    // has no scrypt, and every user hash in the database is in that format.
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Record component names, for the repository row mappers (DataClassRowMapper).
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// Deterministic jar name for the Dockerfile (build/libs/app.jar).
tasks.bootJar {
    archiveFileName.set("app.jar")
}

tasks.jar {
    enabled = false
}
