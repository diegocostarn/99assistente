plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.assistente99"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.assistente99"
        minSdk = 26
        targetSdk = 34
        // No GitHub Actions o número da execução sobe a cada build, então cada APK novo é uma atualização.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0"
    }

    // Assinatura fixa: todo APK gerado tem a mesma assinatura e instala POR CIMA do anterior,
    // sem desinstalar (mantém a acessibilidade ligada e a liberação de "configurações restritas").
    // É uma chave só para uso pessoal; não use este projeto para publicar na Play Store.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Reconhecimento de texto em imagem, roda no aparelho (modelo incluído no app).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
}
