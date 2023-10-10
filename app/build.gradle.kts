import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.gradle.api.internal.file.archive.ZipFileTree
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

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

android {
    namespace = "me.bmax.akpatch"

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

        }
    }

    defaultConfig {
        ndk {
            abiFilters.add("arm64-v8a")
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
            sourceSets {
                file("src/main/jniLibs")
            }
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    externalNativeBuild {
        cmake {
            path("./src/main/cpp/CMakeLists.txt")
        }
    }

    applicationVariants.all {
        outputs.forEach {
            val output = it as BaseVariantOutputImpl
            output.outputFileName = "AKPatch_${managerVersionName}_${managerVersionCode}-$name.apk"
        }

        kotlin.sourceSets {
            getByName(name) {
                kotlin.srcDir("build/generated/ksp/$name/kotlin")
            }
        }
    }
}

tasks.register<Download>("downloadKpimg") {
    src("https://github.com/bmax121/KernelPatch/releases/download/latest/kpimg")
    dest(file("${project.projectDir}/src/main/assets/kpimg"))
    overwrite(false)
}

tasks.register<Download>("downloadKptools") {
    src("https://github.com/bmax121/KernelPatch/releases/download/latest/kptools.zip")
    dest(file("${project.projectDir}/build/tmp/download/kptools.zip"))
    overwrite(false)
}

tasks.register<Copy>("unzipKptools") {
    dependsOn("downloadKptools")
    from(zipTree(file("${project.projectDir}/build/tmp/download/kptools.zip")))
    into(file("${project.projectDir}/src/main/cpp/"))
}

tasks.register("redirectKptools") {
    val cmake = file("${project.projectDir}/src/main/cpp/tools/CMakeLists.txt")
    val con = """
        set_target_properties(kptools PROPERTIES OUTPUT_NAME "kptools.exe.so")
        set_target_properties(kptools PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${'$'}{PROJECT_SOURCE_DIR}/../../jniLibs/arm64-v8a/)
        # redirect_build_flag
        """.trimIndent()
    doLast {
        if (!cmake.readText().contains("# redirect_build_flag")) {
            cmake.appendText("\n")
            cmake.appendText(con)
            cmake.appendText("\n")
        }
    }
}

tasks.register<Download>("downloadKpuser") {
    src("https://github.com/bmax121/KernelPatch/releases/download/latest/kpuser.zip")
    dest(file("${project.projectDir}/build/tmp/download/kpuser.zip"))
    overwrite(false)
}

tasks.register<Copy>("unzipKpuser") {
    dependsOn("downloadKpuser")
    from(zipTree(file("${project.projectDir}/build/tmp/download/kpuser.zip")))
    into(file("${project.projectDir}/src/main/cpp/"))
}

tasks.register("redirectKpuser") {
    val cmake = file("${project.projectDir}/src/main/cpp/user/CMakeLists.txt")
    val con = """
        set_target_properties(kpatch PROPERTIES OUTPUT_NAME "kpatch.exe.so")
        set_target_properties(kpatch PROPERTIES RUNTIME_OUTPUT_DIRECTORY ${'$'}{PROJECT_SOURCE_DIR}/../../jniLibs/arm64-v8a/)
        # redirect_build_flag
        """.trimIndent()
    doLast {
        if (!cmake.readText().contains("# redirect_build_flag")) {
            cmake.appendText("\n")
            cmake.appendText(con)
            cmake.appendText("\n")
        }
    }
}

tasks.getByName("preBuild").dependsOn(
    "downloadKpimg",
//    "unzipKptools",
//    "unzipKpuser",
    "redirectKptools",
    "redirectKpuser"
)

dependencies {
    implementation(libs.com.blankj.utilcodex)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.datastore.preferences)

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

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.io.coil.kt.coil.compose)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.me.zhanghai.android.appiconloader.coil)

    implementation(libs.sheet.compose.dialogs.core)
    implementation(libs.sheet.compose.dialogs.list)
    implementation(libs.sheet.compose.dialogs.input)
}
