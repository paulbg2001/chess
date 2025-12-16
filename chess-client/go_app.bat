@echo off
setlocal

REM Setează calea către directorul proiectului
set PROJECT_DIR=%~dp0

REM Setează calea către JAR-ul compilat (ajustează după numele proiectului tău)
set JAR_FILE=%PROJECT_DIR%target\chess-client-1.0-SNAPSHOT.jar

REM Verifică dacă JAR-ul există
if not exist "%JAR_FILE%" (
    echo JAR-ul nu a fost gasit la: %JAR_FILE%
    echo Ruleaza mai intai: mvn clean package
    pause
    exit /b 1
)

REM Pornește aplicația
echo Pornesc aplicatia chess-client...
java -jar "%JAR_FILE%"

pause
