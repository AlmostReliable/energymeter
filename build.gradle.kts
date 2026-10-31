import org.gradle.internal.classpath.Instrumented.systemProperty

plugins {
    id("net.neoforged.moddev") version "2.0.143"
    id("com.almostreliable.almostgradle") version "2.4.0"
}

almostgradle.setup {
    withSourcesJar = false
    downloadSources = true
    downloadJavadoc = true
    dataGen = "src/main/generated"
    tests {
        testMod = true
        gameTests = true
        testFramework = true
    }
}

neoForge {
    runs {
        configureEach {
            systemProperties.putAll(
                mapOf(
                    "guideme.${almostgradle.modId}.guide.sources" to file("guidebook").absolutePath,
                    "guideme.${almostgradle.modId}.guide.sourcesNamespace" to almostgradle.modId,
                )
            )
        }

        create("guide") {
            client()
            systemProperty("guideme.showOnStartup", "${almostgradle.modId}:guide")
        }
    }
}

repositories {
    // CC: Tweaked
    maven("https://maven.squiddev.cc/")
}

dependencies {
    // CC: Tweaked
    compileOnly("cc.tweaked:cc-tweaked-${almostgradle.minecraftVersion}-forge-api:${almostgradle.getProperty("cctVersion")}")
    runtimeOnly("cc.tweaked:cc-tweaked-${almostgradle.minecraftVersion}-forge:${almostgradle.getProperty("cctVersion")}")
    // GuideME
    runtimeOnly("org.appliedenergistics:guideme:${almostgradle.getProperty("guideMeVersion")}")
}

tasks.withType<Jar> {
    from("guidebook") {
        into("assets/${almostgradle.modId}/guides/${almostgradle.modId}/guide")
    }
}
