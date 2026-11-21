@echo off
title Push KistiGuard Apps to GitHub
cd /d "C:\laragon\www\kistigurd\android-client-lock"
echo ========================================================
echo   Updating and Pushing Both Apps to GitHub...
echo   1. Customer Lock App (Remote Lock / Anti-Tamper)
echo   2. Dealer Hub Native App (Full-Screen, No Browser Bar)
echo ========================================================
echo.
"C:\laragon\bin\git\bin\git.exe" branch -M main
"C:\laragon\bin\git\bin\git.exe" remote remove origin 2>nul
"C:\laragon\bin\git\bin\git.exe" remote add origin https://github.com/marufjahanbd/kistiguard-client-lock.git
"C:\laragon\bin\git\bin\git.exe" add -A
"C:\laragon\bin\git\bin\git.exe" commit -m "Add Native Dealer Android App and update dual APK build workflow" 2>nul
"C:\laragon\bin\git\bin\git.exe" push -u origin main --force

echo.
echo ========================================================
echo Pushed successfully! Now watch both APKs build here:
echo https://github.com/marufjahanbd/kistiguard-client-lock/actions
echo ========================================================
pause
