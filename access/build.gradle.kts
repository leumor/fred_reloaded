plugins {
    id("buildlogic.java-library-conventions")
}

dependencies {
    implementation(project(":base"))
    implementation(project(":crypt"))
    implementation(project(":support"))
}