plugins {
	java
	id("org.springframework.boot") version "4.1.2"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.11.0"
}

group = "dev.curriculotron"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

// Testes de integração (SQLite real em diretório temporário, WireMock) — D-12
val integrationTest: SourceSet = sourceSets.create("integrationTest") {
	compileClasspath += sourceSets.main.get().output
	runtimeClasspath += sourceSets.main.get().output
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	// Dialeto SQLite do Hibernate (versão gerenciada pelo Spring Boot)
	implementation("org.hibernate.orm:hibernate-community-dialects")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.xerial:sqlite-jdbc:3.54.1.0")
	implementation("com.networknt:json-schema-validator:3.0.9")
	// Argon2id do Spring Security crypto depende do Bouncy Castle
	implementation("org.bouncycastle:bcprov-jdk18on:1.87")

	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.wiremock:wiremock-standalone:3.13.2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

val integrationTestTask = tasks.register<Test>("integrationTest") {
	description = "Testes de integração (SQLite temporário, WireMock)."
	group = "verification"
	testClassesDirs = integrationTest.output.classesDirs
	classpath = integrationTest.runtimeClasspath
	shouldRunAfter(tasks.test)
}

tasks.check { dependsOn(integrationTestTask) }

// Copia o build do frontend (Vite) para os estáticos do Spring Boot — uma origem só (D-02)
val copyFrontend = tasks.register<Copy>("copyFrontend") {
	description = "Copia frontend/dist para build/resources/main/static."
	group = "build"
	from(rootDir.resolve("../frontend/dist"))
	into(layout.buildDirectory.dir("resources/main/static"))
	mustRunAfter(tasks.processResources)
}

tasks.named("classes") { dependsOn(copyFrontend) }

spotless {
	java {
		target("src/*/java/**/*.java")
		googleJavaFormat()
		removeUnusedImports()
	}
}

// sqlite-jdbc carrega biblioteca nativa (JEP 472)
tasks.withType<Test> { jvmArgs("--enable-native-access=ALL-UNNAMED") }
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs("--enable-native-access=ALL-UNNAMED")
}
