@echo off
cd /d "D:\DoAnLapTrinhMang"
set "JAVAC_EXE=D:\Apache NetBeans\jdk\bin\javac.exe"
if not exist "%JAVAC_EXE%" set "JAVAC_EXE=javac"
if not exist "bin" mkdir "bin"

echo Dang bien dich toan bo du an...
"%JAVAC_EXE%" -encoding UTF-8 -d "bin" -sourcepath "src" "src\p2p\FileHashUtil.java" "src\server\DirectoryServer.java" "src\server\ServerGUI.java" "src\server\AdminGUI.java" "src\p2p\P2PFileNode.java"

if %errorlevel% equ 0 (
    echo [THANH CONG] Da bien dich tat ca cac goi server, admin va p2p!
) else (
    echo [THAT BAI] Co loi trong qua trinh bien dich!
)
pause
