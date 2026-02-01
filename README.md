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
