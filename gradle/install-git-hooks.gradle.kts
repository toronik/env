gradle.projectsEvaluated {
    val installGitHooks = tasks.create("installGitHooks", Copy::class) {
        val rootDir = rootProject.rootDir.toPath()
        val os: OperatingSystem = org.gradle.nativeplatform.platform.internal.DefaultNativePlatform.getCurrentOperatingSystem()
        var ostype = "linux"
        if (os.isMacOsX) {
            ostype = "macos"
        } else if (os.isWindows) {
            ostype = "windows"
        }

        from(rootDir.resolve("gradle/git-hooks/$ostype"))
        include(
            "pre-commit",
            "pre-push"
        )
        into(rootDir.resolve(".git/hooks"))
        fileMode = 493  // 0755
    }
    allprojects {
        tasks.matching { it.name != "installGitHooks" }.forEach { task ->
            task.dependsOn(installGitHooks)
        }
    }
}
