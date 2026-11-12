import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Módulo de funcionalidad (pantalla): librería Android + Compose + dependencias
 * comunes de UI (ViewModel, Navigation 3, Koin, Coil) y módulos core básicos.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("oms.android.library")
        pluginManager.apply("oms.android.compose")
        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:navigation"))
            add("implementation", project(":core:designsystem"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation3-runtime"))
            add("implementation", libs.lib("koin-androidx-compose"))
            add("implementation", libs.lib("coil-compose"))
        }
    }
}
