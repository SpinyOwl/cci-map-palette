# Publishing

## CurseForge

This project includes Gradle-based CurseForge publishing for both loader artifacts.

### Required setup

Set the following before publishing:

- `curseforge_project_id` in `gradle.properties` or `~/.gradle/gradle.properties`
- `CURSEFORGE_API_TOKEN` environment variable, or `curseforgeApiToken` in `~/.gradle/gradle.properties`

Optional overrides:

- `curseforge_release_type=alpha|beta|release`
- `mod_homepage_url`
- `mod_sources_url`
- `mod_issues_url`

### Publish command

```powershell
.\gradlew.bat curseforgePublish
```

This task builds first, then uploads:

- the Fabric jar from `fabric/build/libs`
- the Forge jar from `forge/build/libs`

using the metadata, changelog, dependencies, and game version declared in the repository.
The project icon source is stored at `branding/logo-512.png` and embedded into both mod jars as `assets/cci_map_palette/icon.png`.

### Dry run

```powershell
.\gradlew.bat curseforgePublish --dry-run
```

Use this to verify task wiring locally before a real upload.

### Recommended local Gradle properties

```properties
curseforge_project_id=123456
curseforgeApiToken=your-api-token
```
