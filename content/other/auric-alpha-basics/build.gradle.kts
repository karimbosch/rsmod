plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.db)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.shops)
    implementation(projects.content.areas.city.lumbridge)
    implementation(projects.content.generic.genericNpcs)
    implementation(libs.rsprot.api)
    testImplementation(libs.kotlin.coroutines.core)
    testRuntimeOnly(libs.sqlite.jdbc)
}
