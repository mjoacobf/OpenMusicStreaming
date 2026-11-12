import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

internal const val COMPILE_SDK = 37
internal const val MIN_SDK = 24
internal const val TARGET_SDK = 37

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/**
 * Configuración compartida por todos los módulos Android (app y librerías).
 * Java 17 + desugaring (requerido por NewPipeExtractor en minSdk < 26).
 */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version = release(COMPILE_SDK)
        }
        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
            isCoreLibraryDesugaringEnabled = true
        }
    }
    dependencies {
        add("coreLibraryDesugaring", libs.lib("desugar-jdk-libs"))
    }
}
