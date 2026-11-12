plugins {
    alias(libs.plugins.oms.android.library)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.extractor"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }
}

dependencies {
    implementation(project(":core:model"))
    api(libs.newpipe.extractor)
    implementation(libs.newpipe.nanojson)
    api(libs.okhttp)
    implementation(libs.okhttp.brotli)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.ktx)
    // DataSource de Media3 compartido por el reproductor y el gestor de descargas
    api(libs.androidx.media3.datasource.okhttp)
}
