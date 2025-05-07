plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:latest.release")
    implementation("org.apache.commons:commons-rng-simple:latest.release")
    implementation("org.apache.commons:commons-lang3:latest.release")
    implementation("net.java.dev.jna:jna-jpms:latest.release")
    implementation("net.java.dev.jna:jna-platform-jpms:latest.release")
    implementation("org.apache.commons:commons-compress:latest.release")

    implementation(project(":base"))
    implementation(project(":crypt"))
}
