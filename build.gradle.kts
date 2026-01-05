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
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
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
// if you have shadowJar configured
tasks.shadowJar {
  manifest {
    attributes["paperweight-mappings-namespace"] = "mojang"
  }
}