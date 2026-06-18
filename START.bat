@echo off

REM  Research Summarizer Agent — Windows Run Script
REM  Double-click this file to build and start the server


REM  Check Java
echo [1/4] Checking Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found!
    echo Please install Java 21 from https://adoptium.net
    pause
    exit /b 1
)
echo       Java found ✓

REM  Check Maven
echo [2/4] Checking Maven...
mvn -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven not found!
    echo Please install Maven from https://maven.apache.org/download.cgi
    echo Then add C:\maven\bin to your PATH environment variable.
    pause
    exit /b 1
)
echo       Maven found ✓

REM  Check API Keys
echo [3/4] Checking API Keys...
if "%ANTHROPIC_API_KEY%"=="" (
    echo.
    echo WARNING: ANTHROPIC_API_KEY is not set.
    echo The app will run in DEMO MODE with mock data.
    echo.
    echo To use real AI, set your key:
    echo   set ANTHROPIC_API_KEY=sk-ant-api03-your-key-here
    echo.
    set ANTHROPIC_API_KEY=demo-mode
) else (
    echo       ANTHROPIC_API_KEY found ✓
)

if "%TAVILY_API_KEY%"=="" (
    echo WARNING: TAVILY_API_KEY is not set. Running in DEMO MODE.
    set TAVILY_API_KEY=demo-mode
) else (
    echo       TAVILY_API_KEY found ✓
)

REM  Build
echo [4/4] Building project...
echo       (First time takes 2-3 minutes to download dependencies)
echo.
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo.
    echo ERROR: Build failed!
    echo Make sure you have internet connection for the first build.
    pause
    exit /b 1
)
echo       Build successful ✓

REM  Start Server
echo.

echo   Server starting at http://localhost:8080
echo.
echo   Swagger UI  →  http://localhost:8080/swagger-ui.html
echo   Health      →  http://localhost:8080/api/research/health
echo   API         →  POST http://localhost:8080/api/research/summarize
echo.
echo   Press Ctrl+C to stop the server

echo.

call mvn spring-boot:run

pause
