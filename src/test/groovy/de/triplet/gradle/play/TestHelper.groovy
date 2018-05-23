package de.triplet.gradle.play

import org.gradle.api.Project
import org.gradle.api.logging.Logging
import org.gradle.testfixtures.ProjectBuilder

class TestHelper {

    static final FIXTURE_WORKING_DIR = new File('src/test/fixtures/android_app')

    static Project fixtureProject() {
        return ProjectBuilder.builder().withProjectDir(FIXTURE_WORKING_DIR).build()
    }

    static Project evaluatableProject() {
        def project = fixtureProject()
        Logging.getLogger(TestHelper).info("Project Directory: ${project.projectDir.absolutePath}")
        project.apply plugin: 'com.android.application'
        project.apply plugin: 'com.github.triplet.play'
        project.android {
            compileSdkVersion 27
            buildToolsVersion '27.0.3'

            defaultConfig {
                versionCode 1
                versionName '1.0'
                minSdkVersion 27
                targetSdkVersion 27
            }

            buildTypes {
                release {
                    signingConfig signingConfigs.debug
                }
            }
        }

        return project
    }

    static Project noSigningConfigProject() {
        def project = fixtureProject()
        project.apply plugin: 'com.android.application'
        project.apply plugin: 'com.github.triplet.play'
        project.android {
            compileSdkVersion 27
            buildToolsVersion '27.0.3'

            defaultConfig {
                versionCode 1
                versionName '1.0'
                minSdkVersion 27
                targetSdkVersion 27
            }

            buildTypes {
                release {
                }
            }
        }

        return project
    }
}
