import java.util.Properties

plugins {
    alias(libs.plugins.oms.android.application)
    alias(libs.plugins.oms.android.compose)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming"

    defaultConfig {
        applicationId = "me.mjoacobf.openmusicstreaming"
        versionCode = 4
        versionName = "1.021"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Firma de release: los datos se leen de keystore.properties (excluido de git)
    val keystoreFile = rootProject.file("keystore.properties")
    val releaseSigning = if (keystoreFile.exists()) {
        val props = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = rootProject.file(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    } else {
        null
    }

    buildTypes {
        release {
            signingConfig = releaseSigning
            // R8: reduce el tamaño del APK y mejora el rendimiento en equipos de gama baja
            optimization {
                enable = true
            }
        }
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:navigation"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:extractor"))
    implementation(project(":core:media"))
    implementation(project(":core:download"))
    implementation(project(":feature:library"))
    implementation(project(":feature:streaming"))
    implementation(project(":feature:player"))
    implementation(project(":feature:bookmarks"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
