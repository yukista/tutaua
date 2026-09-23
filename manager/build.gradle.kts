import java.util.Properties

plugins { id("com.android.application") }

val releasePropertiesFile = rootProject.file("keystore.properties")
val releaseProperties = Properties().apply {
    if (releasePropertiesFile.exists()) releasePropertiesFile.inputStream().use(::load)
}

android {
    namespace = "com.yukista.tutaua.manager"
    compileSdk = 36
    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "com.yukista.tutaua.manager"
        minSdk = 24
        targetSdk = 36
        versionCode = 19
        versionName = "0.3.7"
        buildConfigField("String", "RELEASE_MANIFEST_PUBLIC_KEY", "\"/sory4AAVN8vWFR3zt/N6MK3ear5hgA7ud4GrBfFszw=\"")
    }
    signingConfigs {
        if (releasePropertiesFile.exists()) create("release") {
            storeFile = rootProject.file(releaseProperties.getProperty("storeFile"))
            storePassword = releaseProperties.getProperty("storePassword")
            keyAlias = releaseProperties.getProperty("keyAlias")
            keyPassword = releaseProperties.getProperty("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("net.i2p.crypto:eddsa:0.3.0")
    testImplementation("junit:junit:4.13.2")
}
