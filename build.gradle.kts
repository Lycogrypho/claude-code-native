import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.9"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("com.diffplug.spotless") version "8.9.0"
}

group = "dev.lain"
version = "6.0.1"

val onWindows = System.getProperty("os.name").startsWith("Windows")
val fakeClaude: String = rootProject.file(if (onWindows) "bin/fake-claude.cmd" else "bin/fake-claude").absolutePath

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

sourceSets {
    create("uiTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += output + compileClasspath
        kotlin.srcDir("src/uiTest/kotlin")
        resources.srcDir("src/uiTest/resources")
    }
    create("driftCheck") {
        compileClasspath += sourceSets.test.get().output + sourceSets.test.get().compileClasspath
        runtimeClasspath += output + sourceSets.test.get().runtimeClasspath
        kotlin.srcDir("src/driftCheck/kotlin")
    }
}

configurations {
    named("uiTestImplementation") { extendsFrom(configurations.testImplementation.get()) }
    named("uiTestRuntimeOnly") { extendsFrom(configurations.testRuntimeOnly.get()) }
}

dependencies {
    intellijPlatform {
        intellijIdea("253.29346.138") {
            useInstaller = false
        }
        bundledPlugin("org.jetbrains.plugins.terminal")
        bundledPlugin("Git4Idea")
        bundledPlugin("org.jetbrains.plugins.github")
        bundledPlugin("com.intellij.java")
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("junit:junit:4.13.2")

    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    intellijPlatform {
        testFramework(TestFrameworkType.Platform)
    }

    "uiTestImplementation"(platform("org.junit:junit-bom:6.1.2"))
    "uiTestImplementation"("org.junit.jupiter:junit-jupiter")
    "uiTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    "uiTestImplementation"("com.intellij.remoterobot:remote-robot:0.11.23")
    "uiTestImplementation"("com.intellij.remoterobot:remote-fixtures:0.11.23")
    "uiTestImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
}

listOf("runtimeClasspath", "testRuntimeClasspath").forEach { bundledByThePlatform ->
    configurations.named(bundledByThePlatform) {
        exclude(mapOf("group" to "org.jetbrains.kotlin", "module" to "kotlin-stdlib"))
        exclude(mapOf("group" to "org.jetbrains", "module" to "annotations"))
    }
}

tasks {
    val npm = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

    val npmInstall by registering(Exec::class) {
        inputs.files("package.json", "package-lock.json")
        outputs.file("node_modules/.package-lock.json")
        commandLine(npm, "ci")
    }

    val compileWeb by registering(Exec::class) {
        dependsOn(npmInstall)
        inputs.dir("src/main/ts/jcef")
        inputs.file("tsconfig.json")
        outputs.dir(layout.buildDirectory.dir("web"))
        doFirst { delete(layout.buildDirectory.dir("web")) }
        commandLine(npm, "run", "build")
    }

    val frontendTest by registering(Exec::class) {
        dependsOn(compileWeb)
        inputs.dir("src/test/frontend")
        inputs.dir("src/main/resources/jcef")
        inputs.dir("src/main/ts/jcef")
        outputs.dir(layout.buildDirectory.dir("reports/frontend"))
        environment("CI", "true")
        commandLine(npm, "test")
    }

    check { dependsOn(frontendTest) }

    processResources {
        from(compileWeb)
        exclude("**/PROJECTMAP.md")
        from(rootProject.file("THIRD-PARTY-NOTICES.md")) { into("META-INF") }
        from(rootProject.file("LICENSE")) { into("META-INF") }
        from(rootProject.file("LICENSES")) { into("META-INF/licenses") }
    }
    runIde {
        jvmArgs("-Djb.privacy.policy.text=<!--999.999-->", "-Djb.consents.confirmation.enabled=false", "-Dclaudejb.debug=true")
    }
    test {
        useJUnitPlatform { excludeTags("driftLive") }
        systemProperty("claudejb.fakeClaude", fakeClaude)
    }

    val checkDrift by registering(Test::class) {
        description = "Download latest SDK + probe the installed binary; report protocol drift (on-demand)."
        group = "verification"
        useJUnitPlatform {
            includeTags("driftLive")
            includeEngines("junit-jupiter")
        }
        filter { includeTestsMatching("dev.lain.claudejb.drift.*") }
        testClassesDirs = sourceSets["driftCheck"].output.classesDirs
        classpath = sourceSets["driftCheck"].runtimeClasspath
        outputs.upToDateWhen { false }
        val binaryPath = (
            providers.gradleProperty("claudeBinary").orNull
                ?: providers.environmentVariable("CLAUDE_BINARY").orNull
                ?: "${System.getProperty("user.home")}/.local/bin/claude"
        )
        systemProperty("claudejb.drift.projectDir", rootProject.projectDir.absolutePath)
        systemProperty(
            "claudejb.drift.sdkDir",
            rootProject.file("node_modules/@anthropic-ai/claude-agent-sdk").absolutePath,
        )
        systemProperty("claudejb.drift.binary", binaryPath)
        systemProperty("claudejb.drift.baseline", rootProject.file("scripts/drift-baseline.properties").absolutePath)
        testLogging { showStandardStreams = true }
    }

    val integrationTest by registering {
        description = "Runs only the headless + fake-claude integration tests (subset of `test`)."
        group = "verification"
        finalizedBy(named("test"))
        doFirst {
            (named("test").get() as Test).filter {
                includeTestsMatching("dev.lain.claudejb.headless.*")
                includeTestsMatching("dev.lain.claudejb.integration.*")
            }
        }
    }

    val uiTest by registering(Test::class) {
        description = "End-to-end UI tests driving the IDE via RemoteRobot (Layer D)."
        group = "verification"
        useJUnitPlatform()
        testClassesDirs = sourceSets["uiTest"].output.classesDirs
        classpath = sourceSets["uiTest"].runtimeClasspath
        jvmArgs(
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-opens=java.base/java.util=ALL-UNNAMED",
            "--add-opens=java.base/java.text=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
            "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
        )
        shouldRunAfter("integrationTest")
        System.getProperty("robot-server.url")?.let { systemProperty("robot-server.url", it) }
        doFirst {
            if (project.findProperty("uiTest.enabled") != "true") {
                throw GradleException(
                    "uiTest needs an IDE already running with robot-server on a display, and does not start " +
                        "one. Boot it with `./gradlew runIdeForUiTests` (under xvfb-run if headless), then " +
                        "re-run this task with -PuiTest.enabled=true.",
                )
            }
        }
    }

    named<Copy>("processUiTestResources") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            plugins {
                robotServerPlugin()
            }
            task {
                args(rootProject.file("src/uiTest/resources/sandbox-project").absolutePath)
                jvmArgs(
                    "-Drobot-server.port=8082",
                    "-Dide.browser.jcef.jsQueryPoolSize=10000",
                    "-Djb.privacy.policy.text=<!--999.999-->",
                    "-Djb.consents.confirmation.enabled=false",
                    "-Dide.show.tips.on.startup.default.value=false",
                    "-Dide.mac.message.dialogs.as.sheets=false",
                    "-Dide.mac.file.chooser.native=false",
                    "-DjbScreenMenuBar.enabled=false",
                    "-Dapple.laf.useScreenMenuBar=false",
                    "-Didea.trust.all.projects=true",
                    "-Dide.show.new.ui.welcome.screen=false",
                    "-Dclaudejb.fakeClaude=$fakeClaude",
                    "-Dclaudejb.fakeFixture=${rootProject.file("src/test/resources/fixtures/multi_message.jsonl").absolutePath}",
                )
            }
        }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.29346.138"
            untilBuild = "263.*"
        }
        changeNotes = provider { latestReleaseNotesHtml() }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    pluginVerification {
        freeArgs = listOf("-mute", "TemplateWordInPluginName")
        externalPrefixes = listOf("org.jetbrains.uast")

        failureLevel =
            listOf(
                VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
                VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
                VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
                VerifyPluginTask.FailureLevel.DEPRECATED_API_USAGES,
            )
        ides {
            val localIdes =
                (
                    providers.gradleProperty("localIdePath").orNull
                        ?: providers.environmentVariable("LOCAL_IDE_PATH").orNull
                )?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.map { file(it) }
                    ?.filter { it.exists() }
                    .orEmpty()
            val offline = localIdes.isNotEmpty() && !providers.environmentVariable("CI").isPresent
            if (offline) {
                localIdes.forEach { local(it) }
            } else {
                create(IntelliJPlatformType.IntellijIdea, "253.29346.138") {
                    useInstaller = false
                }
                recommended()
                select {
                    types =
                        listOf(
                            IntelliJPlatformType.IntellijIdeaCommunity,
                            IntelliJPlatformType.PyCharmCommunity,
                        )
                    channels = listOf(ProductRelease.Channel.EAP, ProductRelease.Channel.RC)
                    sinceBuild = "262"
                    untilBuild = "263.*"
                }
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    source.setFrom(files("src/main/kotlin", "src/test/kotlin"))
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

kover {
    currentProject {
        instrumentation {
            disabledForTestTasks.add("checkDrift")
            disabledForTestTasks.add("uiTest")
        }
        sources {
            excludedSourceSets.add("uiTest")
        }
    }
    reports {
        filters {
            excludes {
                classes(
                    "dev.lain.claudejb.view.*",
                    "dev.lain.claudejb.model.bridge.*",
                    "dev.lain.claudejb.controller.bridge.*",
                    "dev.lain.claudejb.controller.commands.*",
                    "dev.lain.claudejb.controller.context.Link*",
                )
                classes("dev.lain.claudejb.controller.actions.*")
                classes(
                    "dev.lain.claudejb.model.context.*",
                    "dev.lain.claudejb.controller.context.*",
                    "dev.lain.claudejb.controller.process.*",
                )
                classes(
                    "dev.lain.claudejb.controller.git.GitAvailability*",
                    "dev.lain.claudejb.controller.git.GitGateway*",
                    "dev.lain.claudejb.controller.git.GitHistoryService*",
                    "dev.lain.claudejb.controller.git.GitLogNavigator*",
                )
                classes("dev.lain.claudejb.util.*")
                classes("dev.lain.claudejb.controller.vuln.OsvHttp*", "dev.lain.claudejb.controller.vuln.VulnService*")
                classes(
                    "dev.lain.claudejb.controller.mcp.IdeMcpService*",
                    "dev.lain.claudejb.controller.mcp.IdeToolCatalog*",
                    "dev.lain.claudejb.controller.mcp.tools.*",
                    "dev.lain.claudejb.controller.db.*",
                )
                classes("dev.lain.claudejb.controller.github.GitHubGateway*")
            }
        }
        verify {
            rule("every gated package holds its floor") {
                groupBy = kotlinx.kover.gradle.plugin.dsl.GroupingEntityType.PACKAGE
                minBound(65)
                minBound(20, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
            }
            rule("gated code as a whole") {
                minBound(75)
                minBound(40, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
            }
        }
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0").editorConfigOverride(
            mapOf(
                "max_line_length" to "140",
                "ij_kotlin_allow_trailing_comma" to "true",
                "ij_kotlin_allow_trailing_comma_on_call_site" to "true",
                "ktlint_standard_function-signature" to "disabled",
                "ktlint_standard_class-signature" to "disabled",
                "ktlint_standard_function-expression-body" to "disabled",
                "ktlint_standard_max-line-length" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
            ),
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
    }
}

fun latestReleaseNotesHtml(): String {
    val notes = file("RELEASE_NOTES.md")
    if (!notes.exists()) return "See RELEASE_NOTES.md."
    val lines = notes.readLines()
    val start = lines.indexOfFirst { it.startsWith("## v") }
    if (start < 0) return "See RELEASE_NOTES.md."
    val end =
        lines.drop(start + 1).indexOfFirst { it.startsWith("## v") }.let {
            if (it < 0) lines.size else start + 1 + it
        }

    fun inline(s: String): String =
        s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("`(.+?)`"), "<code>$1</code>")

    val html = StringBuilder()
    var inList = false

    fun closeList() {
        if (!inList) return
        html.append("</ul>")
        inList = false
    }

    for (raw in lines.subList(start, end)) {
        val line = raw.trim()
        when {
            line.startsWith("## v") -> {
                html.append("<p><b>").append(inline(line.removePrefix("## ").trim())).append("</b></p>")
            }

            line == "---" || line.isEmpty() -> {
                closeList()
            }

            line.startsWith("- ") -> {
                if (!inList) {
                    html.append("<ul>")
                    inList = true
                }
                html.append("<li>").append(inline(line.removePrefix("- ").trim())).append("</li>")
            }

            else -> {
                closeList()
                html.append("<p>").append(inline(line)).append("</p>")
            }
        }
    }
    closeList()
    return html.toString()
}
