plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 31
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidCompileSdkVersionMinor"] = 0
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21
extra["managerVersionCode"] = getVersionCode()
extra["managerVersionName"] = getVersionName()

// Fixed semantic version for 3.5.0 (35000). Two reasons for a fixed code:
// 1) It must never be derived from the git commit count, which drifts with every
//    unrelated commit and makes version comparisons unreliable.
// 2) It must stay ABOVE the historical 30000 + commit-count train, whose last
//    released value was 32760. A lower code (e.g. the previous 30400) makes the
//    system treat an update as a downgrade and blocks installation.
fun getVersionCode(): Int = 35000

fun getVersionName(): String = "3.5.0"
