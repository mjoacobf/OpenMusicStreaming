plugins {
    alias(libs.plugins.oms.android.library)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.media"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:extractor"))
    api(libs.androidx.media3.exoplayer)
    api(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.koin.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}
