# BookPing

**Never miss a library book return date again.**

BookPing is an Android application designed to help students keep track of their library books and return deadlines. It synchronizes book details from the library OPAC and provides configurable reminders so users can return books on time.

## Features

- **OPAC Synchronization** – Fetch library book details from the OPAC system.
- **Book Tracking** – View currently issued books and their due dates.
- **Customizable Reminders** – Choose how many days before the due date to receive a reminder. Default: 2 days.
- **Repeat Notifications** – Configure how frequently reminders repeat.
- **Persistent Notifications** – Ongoing notifications remain visible until the book is returned, subject to Android system restrictions.
- **Local Database** – Store and manage synchronized book information using Room.
- **Background Tasks** – Schedule reminders and background operations using WorkManager.
- **Preferences Storage** – Save user settings using DataStore.

## Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Primary programming language |
| Jetpack Compose | Modern Android UI |
| Material 3 | UI components and design |
| Room Database | Local data storage |
| DataStore | User preferences |
| WorkManager | Background tasks and reminders |
| Jsoup | HTML parsing for OPAC data |
| Android Notifications | Book return reminders |

## Application Workflow

1. Enter your library member ID.
2. Synchronize your issued books from the OPAC.
3. BookPing stores the retrieved information locally.
4. Configure your reminder preferences.
5. Receive notifications before the due date.
6. Repeat reminders continue while the book remains issued.
7. After the book is returned and the OPAC sync reflects the return, the corresponding reminders are cancelled.


## Getting Started

### Requirements

- Android Studio
- Android SDK
- Git
- A compatible JDK for the project's Gradle configuration
- Android device or emulator

### Clone the Repository

```bash
git clone https://github.com/JAGGANRAJ27/BookPing.git
```

### Open the Project

1. Open Android Studio.
2. Select **Open**.
3. Choose the cloned `BookPing` folder.
4. Wait for Gradle Sync to complete.
5. Connect an Android device or start an emulator.
6. Click **Run** to launch the application.

## Build APK

To generate an APK:

1. Open the project in Android Studio.
2. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. Wait for the build to finish.

The debug APK is usually generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notification Configuration

BookPing provides configurable reminder preferences:

| Setting | Default |
|---|---:|
| Reminder before due date | 2 days |
| Repeat reminder interval | 24 hours |
| Notification status | Ongoing until return is detected |

**Note:** Android battery optimization, background execution limits and device manufacturer settings can affect notification timing and visibility. WorkManager schedules background tasks approximately, not at guaranteed exact times.

## Project Structure

```text
BookPing/
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/njwebdesigning/bookping/
│           │       ├── data/
│           │       │   ├── local/
│           │       │   ├── preferences/
│           │       │   ├── remote/
│           │       │   ├── repository/
│           │       │   └── worker/
│           │       ├── notifications/
│           │       └── MainActivity.kt
│           └── res/
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Security and Privacy

- Do not commit library passwords, personal credentials, API keys or other sensitive information.
- Keep local configuration files out of version control.
- Use the application only with authorized library accounts and OPAC access.

## Future Improvements

- Improve OPAC synchronization and error handling.
- Add book return history.
- Improve notification customization.
- Add a more detailed book management dashboard.
- Enhance the user interface and accessibility.

## Developer

**Jagganraj**

Freelance Web Developer | UI/UX Designer | Cybersecurity Enthusiast

- GitHub: [@JAGGANRAJ27](https://github.com/JAGGANRAJ27)
- Project: [BookPing](https://github.com/JAGGANRAJ27/BookPing)

---

**BookPing – Your library companion for smarter book tracking.**
