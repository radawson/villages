plugins {
    id("java")
    id("com.gradleup.shadow") version "9.5.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
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
  // WorldGuard and WorldEdit repositories
  maven {
    name = "enginehub"
    url = uri("https://maven.enginehub.org/repo/")
  }
  // BlueMap repository
  maven {
    name = "bluecolored"
    url = uri("https://repo.bluecolored.de/releases")
  }
  mavenCentral()
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")
  
  // Use CommandAPI 11.1.0 from local file (downloaded from Hangar)
  // Download URL: https://hangarcdn.papermc.io/plugins/Skepter/CommandAPI/versions/11.1.0/PAPER/CommandAPI-11.1.0-Paper.jar
  // This is needed because 11.1.0 isn't published to Maven repositories yet
  // Once 11.1.0 is available in Maven, this can be changed to:
  // implementation("dev.jorel:commandapi-bukkit-shade:11.1.0")
  implementation(files("libs/commandapi-bukkit-shade-11.1.0.jar"))
  
  // Shared Clockworx data layer. Its own classes are bundled; its heavy Maven deps
  // (Hibernate/Flyway/HikariCP/JDBC) are NOT shaded (isTransitive=false) -- they are loaded
  // at runtime by Paper's library-loader (see VillagesLoader). Removes the per-plugin
  // relocation + service-file merge and shrinks the jar.
  implementation("org.clockworx:clockworx-data:0.1.0-SNAPSHOT") { isTransitive = false }

  // DB stack -- compile-only: compiled against, but provided at runtime by the library-loader,
  // not bundled. Keep in sync with clockworx-data's api() deps and VillagesLoader.LIBRARIES.
  compileOnly("org.hibernate:hibernate-core:6.6.40.Final")
  compileOnly("org.hibernate:hibernate-community-dialects:6.6.40.Final")
  compileOnly("org.hibernate.orm:hibernate-hikaricp:6.6.40.Final")
  compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
  compileOnly("org.flywaydb:flyway-core:12.10.0")
  compileOnly("org.flywaydb:flyway-mysql:12.10.0")
  compileOnly("com.zaxxer:HikariCP:7.1.0")
  compileOnly("org.jboss.logging:jboss-logging:3.6.1.Final")
  compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")
  compileOnly("com.mysql:mysql-connector-j:9.1.0")
  compileOnly("org.postgresql:postgresql:42.7.11")
  
  // WorldGuard and WorldEdit - soft dependencies for region management
  compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.9")
  compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.0")
  
  // BlueMap integration uses reflection to access the API at runtime
  // No compile-time dependency needed - the integration will work if BlueMap is installed
  // and gracefully fail if it's not available
  
  paperweight.paperDevBundle("26.1.2.build.74-stable")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(25))
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
  
  // Process Java source files to replace version placeholder in @version annotation
  // This processes source files before compilation without modifying the original source files
  val processJavaSources by registering(Copy::class) {
    val version = project.version.toString()
    from(sourceSets.main.get().java.srcDirs)
    into("${buildDir}/processed-sources/java")
    
    // Replace version placeholder in Java source files
    filter { line ->
      line.replace("{\$version}", version)
    }
    
    // Include all Java files
    include("**/*.java")
    
    // Preserve directory structure
    includeEmptyDirs = false
  }
  
  // Make compileJava depend on processing Java sources and use processed sources
  compileJava {
    dependsOn(processJavaSources)
    
    // Configure to use processed sources
    doFirst {
      // Temporarily add processed sources to the source set for this compilation
      val processedSourceDir = file("${buildDir}/processed-sources/java")
      if (processedSourceDir.exists()) {
        sourceSets.main.get().java.srcDir(processedSourceDir)
        // Remove original source directories to avoid duplicates
        sourceSets.main.get().java.setSrcDirs(listOf(processedSourceDir))
      }
    }
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
  enableAutoRelocation = false
  archiveBaseName.set("Villages")
  archiveVersion.set(project.property("version") as String)
  manifest {
    attributes["paperweight-mappings-namespace"] = "mojang"
  }

  // The DB stack (Hibernate/Flyway/HikariCP/JDBC) is loaded at runtime via the
  // library-loader (VillagesLoader), so it is neither bundled nor relocated here.

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

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE

  // Don't relocate CommandAPI - it's already designed to be shaded
  // and relocation would break the imports
}

// Make shadowJar the default artifact for distribution (includes all dependencies)
// The regular jar task is kept for paperweight compatibility, but shadowJar is what should be deployed
tasks.assemble {
  dependsOn(tasks.shadowJar)
}