@echo off
REM Local development server startup script
REM This script loads environment variables from .env and runs the Spring Boot application in dev mode

setlocal enabledelayedexpansion

if not exist .env (
    echo Error: .env file not found. Please copy .env.sample to .env and configure it.
    exit /b 1
)

echo Loading environment variables from .env...
for /f "usebackq tokens=* delims=" %%a in (.env) do (
    if not "%%a"=="" if not "%%a:~0,1%"=="#" (
        set "%%a"
    )
)

echo Starting Expense Tracker API in development mode...
echo API will be available at http://localhost:8081
echo.

call mvnw.cmd spring-boot:run ^
    -Dspring-boot.run.profiles=dev ^
    -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=dev"

endlocal
