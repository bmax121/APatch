import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.de.undercouch.gradle.tasks.download.Download

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.ksp)
    alias(libs.plugins.lsplugin.apksign)
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
//
//tasks.register<Download>("downloadKpuser") {
//    src("https://github.com/bmax121/KernelPatch/releases/download/${kernelPatchVersion}/kpuser-src-${kpVersion}.zip")
//    dest(file("${project.projectDir}/build/tmp/download/kpuser.zip"))
//    onlyIfNewer(true)
//    overwrite(true)
//}
//
//tasks.register<Copy>("unzipKpuser") {
//    dependsOn("downloadKpuser")
//    from(zipTree(file("${project.projectDir}/build/tmp/download/kpuser.zip")))
//    into(file("${project.projectDir}/src/main/cpp/"))
//}
//
//tasks.register("redirectKpuser") {
//    val cmake = file("${project.projectDir}/src/main/cpp/user/CMakeLists.txt")
//    val con = """
//        set_target_properties(kpatch PROPERTIES OUTPUT_NAME "kpatch.exe.so")
//        set_target_properties(kpatch PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${'$'}{PROJECT_SOURCE_DIR}/../../jniLibs/arm64-v8a/)
//        # redirect_build_flag
//        """.trimIndent()
//    doLast {
//        if (!cmake.readText().contains("# redirect_build_flag")) {
//            cmake.appendText("\n")
//            cmake.appendText(con)
//            cmake.appendText("\n")
//        }
//    }
//}
//
tasks.getByName("preBuild").dependsOn(
    "downloadKpimg",
    "downloadKpatch",
    "downloadKptools",
    "downloadApjni",
)


dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.com.google.accompanist.drawablepainter)
    implementation(libs.com.google.accompanist.navigation.animation)
    implementation(libs.com.google.accompanist.systemuicontroller)

    implementation(libs.compose.destinations.animations.core)
    ksp(libs.compose.destinations.ksp)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.nio)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.io.coil.kt.coil.compose)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.me.zhanghai.android.appiconloader.coil)

    implementation(libs.sheet.compose.dialogs.core)
    implementation(libs.sheet.compose.dialogs.list)
    implementation(libs.sheet.compose.dialogs.input)

    implementation(libs.markdown)
}
