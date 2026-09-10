plugins { kotlin("jvm") version "2.2.21"; kotlin("plugin.serialization") version "2.2.21"; application }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
application { mainClass.set("edu.practice.MainKt") }
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"); implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1"); testImplementation(kotlin("test"));
implementation("io.ktor:ktor-server-core-jvm:3.1.3")
implementation("io.ktor:ktor-server-netty-jvm:3.1.3")
implementation("io.ktor:ktor-server-content-negotiation-jvm:3.1.3")
implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.1.3")
implementation("io.ktor:ktor-server-auth-jvm:3.1.3")
implementation("io.ktor:ktor-server-auth-jwt-jvm:3.1.3")
implementation("io.ktor:ktor-server-call-logging-jvm:3.1.3")
implementation("io.ktor:ktor-server-status-pages-jvm:3.1.3")
implementation("ch.qos.logback:logback-classic:1.5.18")
implementation("org.postgresql:postgresql:42.7.5")
testImplementation("io.ktor:ktor-server-test-host-jvm:3.1.3")
}
