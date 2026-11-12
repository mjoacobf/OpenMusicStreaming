plugins {
    alias(libs.plugins.oms.android.library)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.download"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:extractor"))
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
}
