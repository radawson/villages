plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
}

group = "org.clockworx.villages"

repositories {
  maven {
    name = "papermc"
    url = uri("https://repo.papermc.io/repository/maven-public/")
  }
  maven {
    name = "hangar"
    url = uri("https://maven.papermc.io/repository/maven-public/")
  }
  mavenCentral()
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  
  // Use CommandAPI 11.1.0 from local file (downloaded from Hangar)
  // Download URL: https://hangarcdn.papermc.io/plugins/Skepter/CommandAPI/versions/11.1.0/PAPER/CommandAPI-11.1.0-Paper.jar
  // This is needed because 11.1.0 isn't published to Maven repositories yet
  // Once 11.1.0 is available in Maven, this can be changed to:
  // implementation("dev.jorel:commandapi-bukkit-shade:11.1.0")
  implementation(files("libs/commandapi-bukkit-shade-11.1.0.jar"))
  
  paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
  // Process resources to replace version placeholders
  // This replaces ${version} and ${project.version} in resource files during build
  // without modifying source files - processed files go to build output directory
  processResources {
    // Process all YAML resource files for version expansion
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
      // Replace ${version} and ${project.version} with actual version from gradle.properties
      expand(
        "version" to project.version,
        "project.version" to project.version
      )
      // Also handle $version (without braces) for compatibility
      filter { line ->
        line.replace("\$version", project.version.toString())
      }
    }
  }
  
  // Note: Java source file @version annotation is not processed automatically
  // to avoid modifying source files. Update it manually when changing versions,
  // or use a build-time annotation processor if needed.
  
  // Configure the JAR task to include plugin.yml
  jar {
    archiveBaseName.set("Villages")
    archiveVersion.set(project.property("version") as String)
    
    // Include plugin.yml in the JAR
    from(sourceSets.main.get().output)
    
    // Copy resources (like plugin.yml) into the JAR
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }
}

tasks.jar {
  manifest {
    attributes["paperweight-mappings-namespace"] = "mojang"
  }
}
// Configure shadowJar for distribution
// ShadowJar includes all dependencies (like CommandAPI) in the final JAR
tasks.shadowJar {
  archiveBaseName.set("Villages")
  archiveVersion.set(project.property("version") as String)
  manifest {
    attributes["paperweight-mappings-namespace"] = "mojang"
  }
  
  // Include runtime classpath dependencies (CommandAPI will be included)
  from(project.configurations.runtimeClasspath) {
    // Exclude paperweight dev bundle - it's only for development
    exclude("**/paper-dev-bundle/**")
    // Exclude plugin.yml from dependencies - we have our own
    exclude("plugin.yml")
  }
  
  // Exclude Maven metadata (not needed in final JAR)
  exclude("META-INF/maven/**")
  
  // Merge service files if any
  mergeServiceFiles()
  
  // Our plugin.yml from src/main/resources is included automatically
  // and will be in the JAR, but CommandAPI's plugin.yml might overwrite it
  // So we need to ensure ours is last by using duplicatesStrategy
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  
  // Don't relocate CommandAPI - it's already designed to be shaded
  // and relocation would break the imports
}

// Make shadowJar the default artifact for distribution (includes all dependencies)
// The regular jar task is kept for paperweight compatibility, but shadowJar is what should be deployed
tasks.assemble {
  dependsOn(tasks.shadowJar)
}