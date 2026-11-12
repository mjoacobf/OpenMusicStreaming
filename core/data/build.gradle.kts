plugins {
    alias(libs.plugins.oms.android.library)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:extractor"))
    implementation(libs.androidx.documentfile)
    implementation(libs.koin.android)
}
