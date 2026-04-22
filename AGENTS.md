# AGENTS.md

## Открытие сайта через Chrome CDP (рабочая инструкция)

Использовать эту схему всегда, когда пользователь просит "открыть сайт".

1. Проверить CDP:
   - `curl -s http://127.0.0.1:9222/json/version`
   - если в ответе есть `webSocketDebuggerUrl` и `User-Agent` НЕ содержит `HeadlessChrome`, сразу подключаться через Playwright CDP.

2. Если порт не отвечает ИЛИ активен headless-процесс:
   - убить только CDP-инстанс:
     - `pkill -f "remote-debugging-port=9222.*chrome-debug|chrome-debug.*remote-debugging-port=9222" || true`
   - запустить пользовательский Chrome (НЕ headless):
     - `nohup google-chrome --remote-debugging-port=9222 --user-data-dir=/home/kosta/chrome-debug --no-first-run --no-default-browser-check >/tmp/chrome-cdp.log 2>&1 &`
   - дождаться готовности CDP (цикл до 30 сек):
     - `curl -s http://127.0.0.1:9222/json/version`
   - если после ожидания порт нестабилен: запустить Chrome в отдельной PTY-сессии той же командой (без `nohup`), чтобы процесс не завершился.

3. Подключаться через Playwright по CDP:
   - `chromium.connectOverCDP('http://127.0.0.1:9222')`
   - `const context = browser.contexts()[0] || await browser.newContext()`
   - `const page = context.pages()[0] || await context.newPage()`
   - `await page.goto(URL)`

4. Важно по инструментам:
   - приоритет: Playwright через MCP/доступный агентный инструмент;
   - не рассчитывать на локальный `require('playwright')` без проверки, он может отсутствовать в текущем Node окружении.

5. Никогда не закрывать пользовательский Chrome:
   - можно только `browser.disconnect()` (если метод доступен);
   - никогда не вызывать `browser.close()` для пользовательской сессии.

6. После каждого действия по открытию сайта обязательно сообщать пользователю:
   - открытый `URL`;
   - `title` страницы.
