import java.util.Properties

plugins { id("com.android.application") }

val releasePropertiesFile = rootProject.file("keystore.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.exists()) releasePropertiesFile.inputStream().use(::load)
}

android {
    namespace = "com.yukista.tutaua.box"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yukista.tutaua.box"
        minSdk = 23
        targetSdk = 36
        versionCode = 3
        versionName = "0.2.1"
    }

    signingConfigs {
        if (releasePropertiesFile.exists()) create("release") {
            storeFile = rootProject.file(releaseProperties.getProperty("storeFile"))
            storePassword = releaseProperties.getProperty("storePassword")
            keyAlias = releaseProperties.getProperty("keyAlias")
            keyPassword = releaseProperties.getProperty("keyPassword")
        }
    }
    buildTypes { release { signingConfigs.findByName("release")?.let { signingConfig = it } } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
