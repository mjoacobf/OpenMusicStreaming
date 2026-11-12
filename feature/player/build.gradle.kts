plugins {
    alias(libs.plugins.oms.android.feature)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.feature.player"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(libs.androidx.media3.ui.compose)
}
