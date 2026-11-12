plugins {
    alias(libs.plugins.oms.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "me.mjoacobf.openmusicstreaming.core.database"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:model"))
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
