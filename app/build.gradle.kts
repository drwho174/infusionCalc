plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.drwho174.infusioncalc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.drwho174.infusioncalc"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

val detektCli = configurations.create("detektCli")

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.room3.runtime)
    implementation(libs.sqlite.bundled)
    implementation(libs.datastore.preferences)
    implementation(libs.markwon.core)
    implementation(libs.markwon.ext.latex)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.html)
    implementation(libs.markwon.inline.parser)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    ksp(libs.room3.compiler)
    implementation(project(":core-engine"))
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.framework)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    debugImplementation(libs.compose.ui.test.manifest)
    detektCli(libs.detekt.cli)
}

ktlint {
    android.set(true)
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Runs detekt over main and test sources."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    workingDir = projectDir
    args =
        listOf(
            "--input",
            "src/main/java,src/test/java",
            "--jvm-target",
            "17",
            "--build-upon-default-config",
            "--config",
            "$rootDir/config/detekt.yml",
            "--baseline",
            "$rootDir/config/detekt-baseline.xml",
        )
}

tasks.register<JavaExec>("detektBaseline") {
    group = "verification"
    description = "Creates a detekt baseline."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    workingDir = projectDir
    args =
        listOf(
            "--input",
            "src/main/java,src/test/java",
            "--jvm-target",
            "17",
            "--build-upon-default-config",
            "--config",
            "$rootDir/config/detekt.yml",
            "--baseline",
            "$rootDir/config/detekt-baseline.xml",
            "--create-baseline",
        )
}

tasks.named("check") {
    dependsOn("detekt")
}
