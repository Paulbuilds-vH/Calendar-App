# 📅 Android Calendar App

Dies ist eine Android-Kalender-App, die mit Kotlin und Jetpack Compose entwickelt wurde. Sie ermöglicht die übersichtliche Verwaltung von Terminen und bietet eine Kalenderansicht mit Feiertagsintegration für Nordrhein-Westfalen.

Das Projekt entstand als eines meiner ersten größeren Android-Entwicklungsprojekte und dient gleichzeitig dazu, praktische Erfahrungen mit moderner Android-Entwicklung zu sammeln.

---

## ✨ Features

- 📆 **Kalenderübersicht:** Übersichtliche Darstellung von Kalenderwochen und einzelnen Tagen.
- 🗓️ **Monatsnavigation:** Einfaches Navigieren zwischen verschiedenen Monaten.
- 📝 **Terminverwaltung:** Termine erstellen, bearbeiten und löschen.
- 📋 **Terminbeschreibungen:** Zusätzliche Informationen zu einzelnen Terminen speichern.
- 🏷️ **Terminarten:** Unterscheidung verschiedener Event-Typen.
- 🔍 **Tagesansicht:** Detaillierte Anzeige aller Termine eines ausgewählten Tages.
- 🎨 **Visuelle Terminindikatoren:** Farbige Markierungen zeigen Tage mit vorhandenen Terminen an.
- 🇩🇪 **Feiertage in NRW:** Integration deutscher Feiertage für Nordrhein-Westfalen.
- 🔔 **Benachrichtigungen:** Geplante Erinnerungen an bevorstehende Termine.
- 💾 **Lokale Datenspeicherung:** Termine bleiben auch nach dem Schließen der App erhalten.

---

## 🛠️ Technologien & Frameworks

Das Projekt verwendet verschiedene moderne Android-Technologien:

| Technologie | Verwendung |
|---|---|
| **Kotlin** | Hauptprogrammiersprache |
| **Jetpack Compose** | Deklaratives UI-Framework für die Benutzeroberfläche |
| **Material 3** | Gestaltung der Benutzeroberfläche |
| **Room Database** | Lokale Speicherung und Verwaltung von Terminen |
| **KSP** | Codegenerierung für Room |
| **ViewModel** | Verwaltung und Bereitstellung des UI-Zustands |
| **Kotlin Coroutines / Flow** | Asynchrone Datenverarbeitung und Datenbeobachtung |
| **WorkManager** | Planung und Ausführung von Hintergrundaufgaben |
| **Android Notifications** | Anzeige von Terminerinnerungen |
| **AndroidX Lifecycle** | Verwaltung des App-Lebenszyklus |

---

## 💾 Datenbank

Für die lokale Speicherung der Kalendertermine wird **Room** verwendet.

Room basiert auf SQLite und bietet eine strukturierte Möglichkeit, Daten dauerhaft auf dem Gerät zu speichern.

Die Datenbank besteht unter anderem aus:

- **AppDatabase:** Zentrale Datenbankdefinition.
- **CalendarEventDao:** Zugriff auf Kalendertermine über Datenbankoperationen.
- **CalendarEvent:** Datenmodell für einzelne Termine.
- **Converters:** Konvertierung von Datentypen für die Datenbankspeicherung.
- **CalendarPersistenceViewModel:** Verbindung zwischen Datenhaltung und Benutzeroberfläche.

Die Daten werden lokal auf dem Gerät gespeichert. Eine Cloud-Synchronisation ist aktuell nicht Bestandteil des Projekts.

---

## 🔔 Benachrichtigungssystem

Für Terminerinnerungen wird Androids WorkManager verwendet.

Dadurch können Hintergrundaufgaben geplant werden, ohne dass die App dauerhaft geöffnet sein muss.

Die Benachrichtigungen werden über einen eigenen `EventNotificationWorker` verarbeitet.

---

## 📂 Projektstruktur

Die aktuelle Projektstruktur ist bewusst noch relativ kompakt gehalten:

```text
com.example.jetztaber/
│
├── data/
│   ├── db/
│   │   ├── AppDatabase
│   │   └── CalendarEventDao
│   │
│   └── model/
│       └── CalendarEvent.kt
│
└── ui.theme/
    ├── CalendarPersistenceViewModel
    ├── Converters
    ├── EventNotificationWorker
    └── MainActivity.kt
```

---

## 🚀 Entwicklungsumgebung

- Android Studio
- Kotlin 1.9.22
- Android Gradle Plugin 8.6.1
- Jetpack Compose
- Minimum SDK: Android 7.0 (API 24)
- Target SDK: Android 14 (API 34)

---

## 🎯 Ziel des Projekts

Das Hauptziel dieses Projekts war es, praktische Erfahrungen in der Android-App-Entwicklung zu sammeln und verschiedene Technologien miteinander zu verbinden.

Dabei standen insbesondere folgende Themen im Vordergrund:

- Entwicklung einer Benutzeroberfläche mit Jetpack Compose
- Persistente Datenspeicherung mit Room
- Umgang mit ViewModels und reaktivem UI-State
- Hintergrundverarbeitung mit WorkManager
- Umgang mit Android-Berechtigungen und Benachrichtigungen
- Entwicklung eigener Kalender- und Datumslogik

Das Projekt wird kontinuierlich weiterentwickelt und dient als praktische Lern- und Entwicklungsgrundlage.

---

## 📌 Status

🚧 **Aktiv in Entwicklung**

Dies ist ein persönliches Lernprojekt. Funktionen, Design und Architektur können sich im Laufe der Entwicklung verändern.

---

## 👨‍💻 Entwickler

Entwickelt als persönliches Android-Projekt mit Kotlin und Jetpack Compose.
