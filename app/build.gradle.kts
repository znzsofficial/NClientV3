import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

// 声明插件，版本从 libs.versions.toml 文件中获取
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 从 keystore.properties 加载签名信息
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.maxwai.nclientv3"
    compileSdk = 36

    signingConfigs {
        // 仅当 keystore.properties 文件存在时才配置 release 签名
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.maxwai.nclientv3"
        // Format: MmPPbb
        // M: Major, m: minor, P: Patch, b: build
        versionCode = 400700
        versionName = "4.0.7"

        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true
        proguardFiles("proguard-rules.pro")
    }

    flavorDimensions += "sdk"
    productFlavors {
        create("post28") {
            dimension = "sdk"
            minSdk = 28
        }
        create("pre28") {
            dimension = "sdk"
            minSdk = 26
            targetSdk = 28
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
            versionNameSuffix = "-release"
            resValue("string", "app_name", "NClientV3")
            // 如果 keystore.properties 存在，则使用 release 签名配置
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "NClientV3 Debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable.add("RestrictedApi")
    }

    bundle {
        language {
            // 指定 App Bundle 不应支持语言资源的配置 APK。
            // 这些资源会打包到每个基本和动态功能 APK 中。
            enableSplit = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val suffix = if (flavorName == "pre28") "_pre28" else ""
            val version = versionName.substringBefore("-")
            (this as BaseVariantOutputImpl).outputFileName = "NClientV3_${version}${suffix}.apk"
        }
    }
}

dependencies {
    // AndroidX
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.swiperefreshlayout)

    // Other
    implementation(libs.okhttp.urlconnection)
    implementation(libs.persistentcookiejar)
    implementation(libs.jsoup)
    implementation(libs.acra.core)
    implementation(libs.google.guava)
    implementation(libs.ambilwarna)
    implementation(libs.fastscroll)
    implementation(libs.locale.helper)

    // Glide
    implementation(libs.glide) {
        // 在 KTS 中，exclude 的写法
        exclude(group = "com.android.support")
    }
    annotationProcessor(libs.glide.compiler)

    // AutoService
    compileOnly(libs.google.auto.service.annotations)
    annotationProcessor(libs.google.auto.service)
}
