plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:latest.release")
    implementation("org.apache.commons:commons-rng-simple:latest.release")

    implementation(project(":base"))
}