plugins {
    alias(libs.plugins.oms.android.feature)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.feature.bookmarks"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:media"))
}
