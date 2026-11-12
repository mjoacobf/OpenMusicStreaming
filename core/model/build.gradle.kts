plugins {
    alias(libs.plugins.oms.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.model"
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
