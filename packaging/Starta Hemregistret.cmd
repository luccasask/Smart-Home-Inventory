@echo off
setlocal
if not exist "%~dp0runtime\bin\javaw.exe" (
  echo Java saknas i programmappen. Packa upp hela ZIP-filen och forsok igen.
  pause
  exit /b 1
)
start "" "%~dp0runtime\bin\javaw.exe" --enable-native-access=ALL-UNNAMED -cp "%~dp0app\*" se.smarthome.inventory.ui.Launcher
