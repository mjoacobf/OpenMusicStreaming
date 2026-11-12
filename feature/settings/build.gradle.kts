plugins {
    alias(libs.plugins.oms.android.feature)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.feature.settings"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:download"))
    implementation(project(":core:media"))
    implementation(libs.androidx.activity.compose)
}
