plugins {
    id("java")
    application
}

group = "pro.toparvion.util.heaptalk"
version = "1.0-SNAPSHOT"
val langchain4jVersion = "0.35.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.gridkit.jvmtool:heaplib:0.2")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("info.picocli:picocli:4.7.6")

    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")
    implementation("dev.langchain4j:langchain4j-open-ai:$langchain4jVersion")
    
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    mainClass.set("pro.toparvion.util.heaptalk.HeapTalk")
}

tasks.test {
    useJUnitPlatform()
}