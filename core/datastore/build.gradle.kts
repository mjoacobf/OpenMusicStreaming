plugins {
    alias(libs.plugins.oms.android.library)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.datastore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
}
