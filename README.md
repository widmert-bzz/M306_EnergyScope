# M306_EnergyScope - Electricity Data Tool

## Beschreibung
EnergyScope ist ein Tool zur Analyse und Visualisierung von Stromverbrauchsdaten. Die Anwendung ermöglicht das Hochladen, Verarbeiten und Visualisieren von Energiedaten aus Stromzählern. Mit EnergyScope können Sie:

- Energiedaten im XML-Format hochladen
- Verbrauchsdaten in interaktiven Diagrammen visualisieren
- Daten exportieren und herunterladen
- Historische Energiedaten analysieren

## Systemanforderungen
- Windows Betriebssystem
- Java 17 oder höher
- Node.js und npm
- Maven

## Installation
1. Laden Sie das Projekt herunter oder klonen Sie es aus dem Repository
2. Stellen Sie sicher, dass Java 17 oder höher installiert ist
3. Stellen Sie sicher, dass Node.js und npm installiert sind
4. Stellen Sie sicher, dass Maven installiert ist

## Anwendung starten
Um die Anwendung zu starten, führen Sie einfach die Datei `start_application.bat` aus:

1. Navigieren Sie zum Projektverzeichnis
2. Doppelklicken Sie auf die Datei `start_application.bat`
3. Alternativ können Sie die Datei über die Kommandozeile ausführen:
   ```
   start_application.bat
   ```

Die Batch-Datei startet automatisch:
- Den Backend-Server (Spring Boot) auf Port 8080
- Die Frontend-Anwendung (Angular) auf Port 4200

## Verwendung
Nach dem Start der Anwendung:

1. Öffnen Sie einen Webbrowser und navigieren Sie zu `http://localhost:4200`
2. Verwenden Sie die XML-Upload-Funktion, um Energiedaten hochzuladen
3. Visualisieren und analysieren Sie die Daten mit den interaktiven Diagrammen
4. Exportieren Sie die Daten bei Bedarf

## Datenformat
Die Anwendung verarbeitet Energiedaten im XML-Format. Die Daten werden in JSON-Format konvertiert und gespeichert. Die Dateinamen folgen dem Muster:
`ID[Zählernummer]_[Datum]_[Zeit].json`

## Fehlerbehebung
- Falls die Anwendung nicht startet, stellen Sie sicher, dass die Ports 8080 und 4200 nicht von anderen Anwendungen verwendet werden
- Überprüfen Sie, ob Java, Node.js und Maven korrekt installiert sind
- Prüfen Sie die Konsolenausgabe auf Fehlermeldungen
