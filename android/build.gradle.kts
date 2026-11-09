allprojects {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.Flyfish233")
                includeGroup("com.github.mik3y")
            }
        }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
val generateUiStrings by tasks.registering(Exec::class) {
    group = "localization"
    description = "Generates the shared Flutter, desktop, Parking and Driving catalogs"
    workingDir(rootProject.file(".."))
    val python = if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
    commandLine(python, "tool/generate_inline_strings.py")
    inputs.dir(rootProject.file("../lib/l10n/inline"))
    inputs.file(rootProject.file("../tool/generate_inline_strings.py"))
    inputs.file(rootProject.file("../tool/companion_resource_keys.json"))
    inputs.dir(rootProject.file("cardex/src/main/kotlin"))
    outputs.file(rootProject.file("../lib/l10n/inline_strings.g.dart"))
    outputs.file(rootProject.file("app/src/main/resources/moe/n4tsu/dextop/linux/dextop-ui.json"))
    for (module in listOf("app", "cardex")) {
        outputs.dir(rootProject.file("$module/src/main/resources/moe/n4tsu/dextop/l10n"))
    }
    outputs.files(fileTree(rootProject.file("cardex/src/main/res")) { include("values*/strings.xml") })
}

subprojects {
    project.evaluationDependsOn(":app")
    if (name in listOf("app", "cardex", "cardexdriving")) {
        tasks.configureEach {
            if (name == "preBuild" || name.startsWith("compileFlutterBuild")) dependsOn(generateUiStrings)
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
