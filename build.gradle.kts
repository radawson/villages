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
  // Task to replace version placeholders in source and resource files
  val processVersion by registering {
    group = "build"
    description = "Replaces version placeholders in source and resource files"
    
    val version = project.property("version") as String
    
    doLast {
      // Process resource files
      val pluginYml = file("src/main/resources/plugin.yml")
      val paperPluginYml = file("src/main/resources/paper-plugin.yml")
      
      if (pluginYml.exists()) {
        pluginYml.writeText(pluginYml.readText().replace("\${version}", version))
      }
      
      if (paperPluginYml.exists()) {
        paperPluginYml.writeText(paperPluginYml.readText().replace("\${version}", version))
      }
      
      // Process Java source file
      val pluginJava = file("src/main/java/org/clockworx/villages/VillagesPlugin.java")
      if (pluginJava.exists()) {
        pluginJava.writeText(pluginJava.readText().replace("{\$version}", version))
      }
    }
  }
  
  // Make processResources and compileJava depend on processVersion
  processResources {
    dependsOn("processVersion")
  }
  
  compileJava {
    dependsOn("processVersion")
  }
  
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
  }
  
  // Merge service files if any
  mergeServiceFiles()
  
  // Don't relocate CommandAPI - it's already designed to be shaded
  // and relocation would break the imports
}

// Make shadowJar the default artifact for distribution (includes all dependencies)
// The regular jar task is kept for paperweight compatibility, but shadowJar is what should be deployed
tasks.assemble {
  dependsOn(tasks.shadowJar)
}