@echo off
title Push KistiGuard Client to GitHub
cd /d "C:\laragon\www\kistigurd\android-client-lock"
echo ========================================================
echo   Updating and Pushing KistiGuard Client to GitHub...
echo ========================================================
echo.
"C:\laragon\bin\git\bin\git.exe" branch -M main
"C:\laragon\bin\git\bin\git.exe" remote remove origin 2>nul
"C:\laragon\bin\git\bin\git.exe" remote add origin https://github.com/marufjahanbd/kistiguard-client-lock.git
"C:\laragon\bin\git\bin\git.exe" add -A
"C:\laragon\bin\git\bin\git.exe" commit -m "Fix Android build workflow and Gradle config" 2>nul
"C:\laragon\bin\git\bin\git.exe" push -u origin main --force

echo.
echo ========================================================
echo Pushed successfully! Now watch your APK build here:
echo https://github.com/marufjahanbd/kistiguard-client-lock/actions
echo ========================================================
pause
