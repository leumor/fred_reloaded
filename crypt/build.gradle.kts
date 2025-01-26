plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:latest.release")

    implementation(project(":base"))
}