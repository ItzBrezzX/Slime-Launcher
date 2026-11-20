plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vulkanShaderAssets = layout.buildDirectory.dir("generated/vulkanAssets")
val vertexShaderSource = layout.projectDirectory.file("src/main/shaders/bedrock.vert")
val fragmentShaderSource = layout.projectDirectory.file("src/main/shaders/bedrock.frag")
val vertexShaderOutput = vulkanShaderAssets.map { it.file("shaders/bedrock.vert.spv") }
val fragmentShaderOutput = vulkanShaderAssets.map { it.file("shaders/bedrock.frag.spv") }

val compileBedrockVertexShader by tasks.registering(Exec::class) {
    inputs.file(vertexShaderSource)
    outputs.file(vertexShaderOutput)
    doFirst { vertexShaderOutput.get().asFile.parentFile.mkdirs() }
    commandLine("glslangValidator", "-V", "--target-env", "vulkan1.0",
        "-o", vertexShaderOutput.get().asFile.absolutePath, vertexShaderSource.asFile.absolutePath)
}
val compileBedrockFragmentShader by tasks.registering(Exec::class) {
    inputs.file(fragmentShaderSource)
    outputs.file(fragmentShaderOutput)
    doFirst { fragmentShaderOutput.get().asFile.parentFile.mkdirs() }
    commandLine("glslangValidator", "-V", "--target-env", "vulkan1.0",
        "-o", fragmentShaderOutput.get().asFile.absolutePath, fragmentShaderSource.asFile.absolutePath)
}

android {
    namespace = "com.kingbrezz.slime"
    compileSdk = 36
    ndkVersion = "27.0.12077973"
    defaultConfig {
        applicationId = "com.kingbrezz.slime"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++20")
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
        }
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDir(rootProject.file("third_party/LeviLauncher-Android/app/src/main/jniLibs"))
            assets.srcDir(vulkanShaderAssets)
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { compose = true; buildConfig = true; prefab = true }
    packaging {
        jniLibs {
            excludes += listOf("**/libPlayFabMultiplayer.so", "**/libinbuiltmods.so", "**/libmaesdk.so")
        }
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES",
                "/META-INF/NOTICE", "/META-INF/NOTICE.txt", "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt", "/META-INF/license.txt"
            )
        }
    }
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" }
    }
}
tasks.named("preBuild").configure {
    dependsOn(compileBedrockVertexShader, compileBedrockFragmentShader)
}
dependencies {
    implementation(project(":minecraft"))
    implementation("androidx.games:games-activity:4.4.2")
    implementation("androidx.appcompat:appcompat:1.7.1")
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("com.bytedance.android:shadowhook:2.0.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) }
}
