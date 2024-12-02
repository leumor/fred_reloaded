plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    implementation(project(":support"))
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")
}