plugins {
    alias(libs.plugins.oms.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.navigation"
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.navigation3.runtime)
}
