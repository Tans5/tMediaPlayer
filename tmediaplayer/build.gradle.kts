import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    id("maven-publish")
    id("signing")
}

android {
    namespace = "com.tans.tmediaplayer"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"))
        }
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    ndkVersion = "24.0.8215888"
}

dependencies {
    implementation(libs.androidx.appcompat)
}

val publishProperties = Properties()
publishProperties.load(File(projectDir, "publish.properties").inputStream())

publishing {
    repositories {
        maven {
            name = "MavenCentralRelease"
            credentials {
                username = publishProperties.getProperty("MAVEN_USERNAME")
                password = publishProperties.getProperty("MAVEN_PASSWORD")
            }
            url = uri(publishProperties.getProperty("RELEASE_REPOSITORY_URL"))
        }
        maven {
            name = "MavenCentralSnapshot"
            credentials {
                username = publishProperties.getProperty("MAVEN_USERNAME")
                password = publishProperties.getProperty("MAVEN_PASSWORD")
            }
            url = uri(publishProperties.getProperty("SNAPSHOT_REPOSITORY_URL"))
        }
        maven {
            name = "MavenLocal"
            url = uri(File(rootProject.projectDir, "maven"))
        }
    }

    publications {
        val defaultPublication = this.create("Default", MavenPublication::class.java)
        with(defaultPublication) {
            groupId = publishProperties.getProperty("GROUP_ID")
            artifactId = publishProperties.getProperty("ARTIFACT_ID")
            version = publishProperties.getProperty("VERSION_NAME")

            afterEvaluate {
                artifact(tasks.getByName("bundleReleaseAar"))
            }
            val sourceCode by tasks.creating(Jar::class.java) {
                archiveClassifier.convention("sources")
                archiveClassifier.set("sources")
                from(android.sourceSets.getByName("main").java.srcDirs)
            }
            artifact(sourceCode)
            pom {
                name = "tMediaPlayer"
                description = "Android media player libs."
                url = "https://github.com/tans5/tMediaPlayer.git"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "tanpengcheng"
                        name = "tans5"
                        email = "tans.tan096@gmail.com"
                    }
                }
                scm {
                    url.set("https://github.com/tans5/tMediaPlayer.git")
                }
            }

            pom.withXml {
                val dependencies = asNode().appendNode("dependencies")
                configurations.implementation.get().allDependencies.all {
                    val dependency = this
                    if (dependency.group == null || dependency.version == null || dependency.name == "unspecified") {
                        return@all
                    }
                    val dependencyNode = dependencies.appendNode("dependency")
                    dependencyNode.appendNode("groupId", dependency.group)
                    dependencyNode.appendNode("artifactId", dependency.name)
                    dependencyNode.appendNode("version", dependency.version)
                    dependencyNode.appendNode("scope", "implementation")
                }
            }
        }
    }
}


signing {
    sign(publishing.publications.getByName("Default"))
}