plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ameermuawiya.decompilers"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ameermuawiya.decompilers"

        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes +=
                setOf(
                    "META-INF/LICENSE",
                    "META-INF/LICENSE.txt",
                    "META-INF/NOTICE.txt",
                    "META-INF/MANIFEST.MF",
                    "META-INF/rxjava.properties",
                )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = false
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {

    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.14.0-alpha03")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.12.0-alpha04")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.12.0-alpha04")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.2")
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")

    implementation("io.github.skylot:jadx-core:1.5.6")
    implementation("io.github.skylot:jadx-dex-input:1.5.6")
    implementation("org.slf4j:slf4j-nop:2.0.20")

// implementation("org.benf:cfr:0.152")
implementation(files("libs/cfr-0.152.jar"))

// implementation("org.bitbucket.mstrobel:procyon-decompiler:0.5.30")
implementation(files("libs/procyon-decompiler-0.5.30.jar"))

    implementation(files("libs/fernflower-2.5.0.Final.jar"))

    implementation(files("libs/dex-ir-2.4.38.jar"))
    implementation(files("libs/dex-reader-api-2.4.38.jar"))
    implementation(files("libs/dex-reader-2.4.38.jar"))
    implementation(files("libs/dex-tools-2.4.38.jar"))
    implementation(files("libs/dex-translator-2.4.38.jar"))

    implementation("org.ow2.asm:asm-tree:9.11.1")
    implementation("org.ow2.asm:asm-util:9.11.1")
    implementation("org.ow2.asm:asm-commons:9.11.1")
    implementation("org.smali:dexlib2:2.5.2")

    implementation(files("libs/ARSCLib-1.4.0.jar"))

    implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")
    implementation("io.github.Rosemoe.sora-editor:language-java:0.23.6")

    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")
    implementation("commons-io:commons-io:2.22.0")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("pub.devrel:easypermissions:3.0.0")
    implementation("com.android.volley:volley:1.2.1")
}
