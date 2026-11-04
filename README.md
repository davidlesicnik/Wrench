# Wrench

> [!IMPORTANT]
> **Project Context & Disclaimer**
> 
> I am not a professional programmer. This project was born out of two goals:
> 1. **Personal Utility:** I wanted a dedicated Android companion for LubeLogger.
> 2. **Learning CI/CD:** I wanted to gain hands-on experience setting up CI/CD pipelines for a mobile environment.
>
> Because of this, the codebase was **primarily generated using AI tools**. While the app is functional, the internal logic may not follow standard programming patterns.

Wrench is an Android companion app for [LubeLogger](https://github.com/hargata/lubelog).

## Features

* **API Authentication:** Connect using the server URL and API key.
* **Vehicle Overview:** View all of your vehicles.
* **Expense Management:**
  * List existing expenses.
  * Add new entries.
  * Edit existing expenses
  * Delete records.

## Installation & Build

### Prerequisites

* Android device running API 24 or higher.
* A LubeLogger instance with API access enabled.

### Building from Source

1. Clone the repo:
```bash
git clone https://github.com/davidlesicnik/Wrench
cd wrench

```


2. Build the APK:
**macOS / Linux:**
```bash
./gradlew assembleDebug

```


**Windows:**
```powershell
.\gradlew.bat assembleDebug

```

The .apk will be located in `app/build/outputs/apk/debug/`.

## CI and releases

Pull requests and pushes to `master`/`main` run compilation and JVM unit tests.
These checks do not assemble APKs or require signing secrets. CodeQL runs separately.
Dependabot batches weekly Gradle minor/patch updates and GitHub Actions updates;
major Gradle upgrades stay separate. Security updates have their own groups.
Automatic merging is disabled.

To release:

1. Increase both `versionName` and `versionCode` in `app/build.gradle.kts`, then
   merge the change into the default branch. Versions only need bumping for releases.
2. Open **Actions → Release → Run workflow**, selecting the default branch.
3. Review and publish the resulting draft release with its signed APK.

The release workflow checks that both versions exceed those in published `v*`
releases (including prereleases), builds using the existing `RELEASE_KEYSTORE_*`
secrets, and creates a draft targeting the selected commit. An unpublished draft
can be rebuilt at the same version. Only manual runs from the default branch are allowed.

For merge enforcement, make **Compile and unit tests** a required status check in
GitHub branch protection or rulesets after the workflow has run once.

## Contributing

There are two ways to contribute:

### Report Issues

If you find a bug or have a feature request, open an issue in the [issue tracker](https://github.com/davidlesicnik/Wrench/issues).

### Submit Code Changes

1. Fork the repo.
2. Create a new branch.
3. Commit your changes and push to your fork.
4. Open a pull request.

## License

This project is licensed under the MIT License.
