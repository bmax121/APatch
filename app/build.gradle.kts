import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.de.undercouch.gradle.tasks.download.Download

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.lsplugin.resopt)
    id("kotlin-parcelize")
}

val managerVersionCode: Int by rootProject.extra
val managerVersionName: String by rootProject.extra
val kernelPatchVersion: String by rootProject.extra

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

android {
    namespace = "me.bmax.apatch"

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            multiDexEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    dependenciesInfo.includeInApk = false

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    defaultConfig {
        buildConfigField("String", "buildKPV", "\"$kernelPatchVersion\"")
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.androidx.compose.compiler.get().version
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/**.version"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "/org/bouncycastle/**"
            excludes += "org/**"
            excludes += "**.properties"
            excludes += "**.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    sourceSets["main"].jniLibs.srcDir("libs")

    applicationVariants.all {
        outputs.forEach {
            val output = it as BaseVariantOutputImpl
            output.outputFileName = "APatch_${managerVersionName}_${managerVersionCode}-$name.apk"
        }

        kotlin.sourceSets {
            getByName(name) {
                kotlin.srcDir("build/generated/ksp/$name/kotlin")
            }
        }
    }
}

tasks.register<Download>("downloadKpimg") {
    src("https://github.com/bmax121/KernelPatch/releases/download/${kernelPatchVersion}/kpimg-android")
    dest(file("${project.projectDir}/src/main/assets/kpimg"))
    onlyIfNewer(true)
    overwrite(true)
}

tasks.register<Download>("downloadKpatch") {
    src("https://github.com/bmax121/KernelPatch/releases/download/${kernelPatchVersion}/kpatch-android")
    dest(file("${project.projectDir}/libs/arm64-v8a/libkpatch.so"))
    onlyIfNewer(true)
    overwrite(true)
}

tasks.register<Download>("downloadKptools") {
    src("https://github.com/bmax121/KernelPatch/releases/download/${kernelPatchVersion}/kptools-android")
    dest(file("${project.projectDir}/libs/arm64-v8a/libkptools.so"))
    onlyIfNewer(true)
    overwrite(true)
}

tasks.register<Download>("downloadApjni") {
    src("https://github.com/bmax121/KernelPatch/releases/download/${kernelPatchVersion}/libapjni.so")
    dest(file("${project.projectDir}/libs/arm64-v8a/libapjni.so"))
    onlyIfNewer(true)
    overwrite(true)
}

tasks.register<Copy>("mergeFlashableScript") {
    into("${project.projectDir}/src/main/resources/META-INF/com/google/android")
    from(rootProject.file("${project.rootDir}/scripts/update_binary.sh")) {
         rename { "update-binary" }
    }
    from(rootProject.file("${project.rootDir}/scripts/update_binary.sh")) {
         rename { "updater-script" }
    }
}

tasks.getByName("preBuild").dependsOn(
    "downloadKpimg",
    "downloadKpatch",
    "downloadKptools",
    "downloadApjni",
    "mergeFlashableScript",
)

// https://github.com/bbqsrc/cargo-ndk
// cargo ndk -t arm64-v8a build --release
tasks.register<Exec>("cargoBuild") {
    executable("cargo")
    args("ndk", "-t", "arm64-v8a", "build", "--release")
    workingDir("${project.rootDir}/apd")
}

tasks.register<Copy>("buildApd") {
    dependsOn("cargoBuild")
    from("${project.rootDir}/apd/target/aarch64-linux-android/release/apd")
    into("${project.projectDir}/libs/arm64-v8a")
    rename("apd", "libapd.so")
}

tasks.configureEach {
    if (name == "mergeDebugJniLibFolders" || name == "mergeReleaseJniLibFolders") {
        dependsOn("buildApd")
    }
}

tasks.register<Exec>("cargoClean") {
    executable("cargo")
    args("clean")
    workingDir("${project.rootDir}/apd")
}

tasks.register<Delete>("apdClean") {
    dependsOn("cargoClean")
    delete(file("${project.projectDir}/libs/arm64-v8a/libapd.so"))
}

tasks.clean {
    dependsOn("apdClean")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.androidx.webkit)
    implementation(libs.timber)
    implementation(libs.devappx)
    implementation(libs.ini4j)
    implementation(libs.bcpkix)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.com.google.accompanist.drawablepainter)
    implementation(libs.com.google.accompanist.navigation.animation)

    implementation(libs.compose.destinations.animations.core)
    ksp(libs.compose.destinations.ksp)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.nio)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.io.coil.kt.coil.compose)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.me.zhanghai.android.appiconloader.coil)

    implementation(libs.sheet.compose.dialogs.core)
    implementation(libs.sheet.compose.dialogs.list)
    implementation(libs.sheet.compose.dialogs.input)

    implementation(libs.markdown)
    implementation(libs.com.google.accompanist.webview)
}
