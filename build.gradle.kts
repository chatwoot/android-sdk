plugins {
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

subprojects {
    group = "com.chatwoot.android"

    // vanniktech maven-publish + Maven Central publish flow isn't config-cache compatible yet:
    // https://github.com/gradle/gradle/issues/22779. Mark publish tasks as opt-out so the rest
    // of the build still benefits from the config cache.
    tasks.withType(org.gradle.api.publish.maven.tasks.PublishToMavenRepository::class.java).configureEach {
        notCompatibleWithConfigurationCache(
            "Maven Central publishing isn't config-cache compatible yet — gradle/gradle#22779."
        )
    }

    // Centralised publishing config — each library only needs its own `pom { }` block.
    // automaticRelease=true flips Sonatype Central Portal deployments to "released" once
    // validation passes, removing the manual click-to-publish step. Local
    // `publishToMavenLocal` is unaffected (no Central Portal involvement).
    plugins.withId("com.vanniktech.maven.publish") {
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(automaticRelease = true)
            if (System.getenv("ORG_GRADLE_PROJECT_signingInMemoryKey") != null) {
                signAllPublications()
            }
        }
    }
}
