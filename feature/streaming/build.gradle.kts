plugins {
    alias(libs.plugins.oms.android.feature)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.feature.streaming"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:download"))
}
