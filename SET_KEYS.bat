@echo off


REM Step 1: Replace with your Anthropic API key

set ANTHROPIC_API_KEY=YOUR_ANTHROPIC_API_KEY

REM  Step 2: Replace with your Tavily API key

set TAVILY_API_KEY=YOUR_TAVILY_API_KEY

echo.
echo  API Keys have been set for this session:
echo    ANTHROPIC_API_KEY = %ANTHROPIC_API_KEY:~0,20%...
echo    TAVILY_API_KEY    = %TAVILY_API_KEY:~0,15%...
echo.
echo Now run START.bat to build and start the server.
echo.
pause
