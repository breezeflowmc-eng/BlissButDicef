@echo off
echo Building DiceGems...
where mvn >nul 2>nul
if errorlevel 1 (
  echo.
  echo Maven is not installed. Easiest fix: use the GitHub method in BUILD.md
  echo or install JDK 21 + Maven, then run this again.
  pause
  exit /b 1
)
call mvn -q clean package
if errorlevel 1 (
  echo.
  echo BUILD FAILED - copy the error above and send it back.
  pause
  exit /b 1
)
echo.
echo DONE. Your plugin is: target\DiceGems-1.0.0.jar
pause
