import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.kotlin.jvm) }

// Pure Kotlin/JVM: no Android imports here. Build with JDK 21, emit Java 17 bytecode so :app can use it.
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies { testImplementation(libs.junit) }
tasks.test { useJUnit() }
