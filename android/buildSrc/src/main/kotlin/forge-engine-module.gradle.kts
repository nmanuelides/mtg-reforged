// Builds one upstream Forge Maven module (forge-core, forge-game, ...) as a plain Java
// library, compiling its sources in place. Dependencies are read from the module's
// pom.xml so that upstream changes flow in without editing this build.

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    `java-library`
}

val upstreamDir = rootProject.projectDir.parentFile.resolve(project.name)
require(upstreamDir.resolve("pom.xml").isFile) { "No upstream module at $upstreamDir" }

sourceSets {
    main {
        java.setSrcDirs(listOf(upstreamDir.resolve("src/main/java")))
        resources.setSrcDirs(listOf(upstreamDir.resolve("src/main/resources")))
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
    options.isWarnings = false
    options.compilerArgs.add("-Xlint:none")
    options.compilerArgs.add("-nowarn")
}

fun Element.child(name: String): Element? =
    (0 until childNodes.length).map { childNodes.item(it) }
        .filterIsInstance<Element>().firstOrNull { it.tagName == name }

fun Element.children(name: String): List<Element> =
    (0 until childNodes.length).map { childNodes.item(it) }
        .filterIsInstance<Element>().filter { it.tagName == name }

fun readPom(file: File): Element =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

fun Element.properties(): Map<String, String> =
    child("properties")?.let { props ->
        (0 until props.childNodes.length).map { props.childNodes.item(it) }
            .filterIsInstance<Element>().associate { it.tagName to it.textContent.trim() }
    } ?: emptyMap()

val rootPom = readPom(rootProject.projectDir.parentFile.resolve("pom.xml"))
val modulePom = readPom(upstreamDir.resolve("pom.xml"))
val pomProperties = rootPom.properties() + modulePom.properties()

fun resolve(value: String): String =
    Regex("""\$\{([^}]+)}""").replace(value) { pomProperties[it.groupValues[1]] ?: it.value }

modulePom.child("dependencies")?.children("dependency")?.forEach { dep ->
    val group = dep.child("groupId")!!.textContent.trim()
    val artifact = dep.child("artifactId")!!.textContent.trim()
    val scope = dep.child("scope")?.textContent?.trim() ?: "compile"
    if (scope != "compile" && scope != "runtime") return@forEach

    val configuration = if (scope == "runtime") "runtimeOnly" else "api"
    if (group == "forge") {
        dependencies.add(configuration, project(":engine:$artifact"))
        return@forEach
    }

    val version = resolve(dep.child("version")!!.textContent.trim())
    val exclusions = dep.child("exclusions")?.children("exclusion").orEmpty().map {
        it.child("groupId")!!.textContent.trim() to it.child("artifactId")!!.textContent.trim()
    }
    dependencies.add(configuration, "$group:$artifact:$version") {
        (this as ExternalModuleDependency).apply {
            exclusions.forEach { (g, a) -> exclude(group = g, module = a) }
        }
    }
}
