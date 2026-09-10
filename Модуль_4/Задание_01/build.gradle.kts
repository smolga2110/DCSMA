plugins { kotlin("jvm") version "2.2.21"; kotlin("plugin.serialization") version "2.2.21"; application }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
application { mainClass.set("edu.practice.MainKt") }
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1"); testImplementation(kotlin("test"));

}
