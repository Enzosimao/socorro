plugins {
}

android {
    namespace = "com.example.trabalhosensores"

    defaultConfig {
        applicationId = "com.example.trabalhosensores"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
}

dependencies {
}