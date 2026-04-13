@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "REPO_ROOT=%%~fI"

set "DATASET=wcukierski/enron-email-dataset"
if not "%KAGGLE_DATASET%"=="" set "DATASET=%KAGGLE_DATASET%"

set "OUT_DIR=%REPO_ROOT%\datasets\raw\enron"

echo Preparing output directory: "%OUT_DIR%"
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"

where kaggle >nul 2>&1
if errorlevel 1 (
  echo Error: Kaggle CLI is not installed or not available in PATH.
  echo Install options:
  echo   - py -m pip install kaggle
  echo   - pipx install kaggle
  echo If the command is still not found, ensure your Python Scripts directory is in PATH.
  echo After installation, ensure the "kaggle" command is available in your shell.
  exit /b 1
)

echo Downloading dataset "%DATASET%" into "%OUT_DIR%"
kaggle datasets download -d "%DATASET%" -p "%OUT_DIR%"
if errorlevel 1 (
  echo Error: Kaggle download failed.
  echo Check that your Kaggle authentication is configured.
  call :print_next_steps
  exit /b 1
)

set "ZIP_FILE="
for /f "delims=" %%F in ('dir /b /a:-d "%OUT_DIR%\*.zip" 2^>nul') do (
  set "ZIP_FILE=%OUT_DIR%\%%F"
  goto :zip_found
)

echo Error: No zip file was downloaded into "%OUT_DIR%".
exit /b 1

:zip_found
echo Extracting "%ZIP_FILE%"
powershell -NoProfile -Command "Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%OUT_DIR%' -Force"
if errorlevel 1 (
  echo Error: Failed to extract "%ZIP_FILE%".
  exit /b 1
)

echo Removing archive "%ZIP_FILE%"
del /f /q "%ZIP_FILE%" >nul 2>&1

if exist "%OUT_DIR%\emails.csv" (
  echo Dataset ready at: "%OUT_DIR%\emails.csv"
) else (
  echo Download finished, but emails.csv was not found in "%OUT_DIR%".
  echo Please inspect the extracted files manually.
)

exit /b 0

:print_next_steps
echo Kaggle CLI requires authentication.
echo.
echo 1. Create a Kaggle account or sign in.
echo 2. Open https://www.kaggle.com/settings
echo 3. In "API", click "Generate New Token"
echo 4. Save the token to:
echo    - Windows: %%USERPROFILE%%\.kaggle\access_token
echo    - macOS/Linux: ~/.kaggle/access_token
echo 5. On macOS/Linux, restrict permissions with:
echo    chmod 600 ~/.kaggle/access_token
echo.
echo Alternative options:
echo - set the KAGGLE_API_TOKEN environment variable
echo   Example in CMD: set KAGGLE_API_TOKEN=...
echo - use the legacy credentials file ~/.kaggle/kaggle.json
exit /b 0
