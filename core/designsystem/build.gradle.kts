plugins {
    alias(libs.plugins.oms.android.library)
    alias(libs.plugins.oms.android.compose)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose)
}
