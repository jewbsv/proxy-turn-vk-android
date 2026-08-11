# AGENTS.md — правила проекта qWDTT

Обязательные правила для агента, работающего в репозитории `jewbsv/proxy-turn-vk-android`.

## 1. Управление версионированием

Перед **сборкой новых APK**, **коммитом масштабных изменений** или **созданием релиза** — спросить у пользователя номер версии (например: «Повышаем до 1.4.1?»). Только после получения конкретного номера:

- `versionCode` увеличить на 1;
- `versionName` установить равным значению пользователя;
- применить в `app/build.gradle.kts` до финальной сборки.

## 2. Безопасность секретов

- Не сохранять в git пароли и ключи: `local.properties`, `*.jks` — в `.gitignore`.
- В дневнике и отчётах маскировать секреты (не выводить значения паролей и ключей).

## 3. Ведение журнала разработки

После успешного выполнения любой значимой задачи, исправления бага или перед созданием коммита — ОБЯЗАТЕЛЬНО задокументировать проделанную работу: записать **дату**, **суть изменений** и **номер версии** в конец файла `ПРОЕКТ_ДНЕВНИК.md`, чтобы сохранялась история разработки проекта.

## 4. Автопроверка APK через VirusTotal API

При генерации релизных APK ты ОБЯЗАН автоматически прогнать их через скрипт `scripts/virustotal-check.py` (загружает файлы на VirusTotal через API, дожидается анализа, получает SHA-256). Ключ API читается скриптом из переменной окружения `VT_API_KEY` или из `local.properties` (строго локально, в git не попадает).

В чат выводить только финальный статус для каждого файла:

- **Имя APK:**
- **Статус в VirusTotal:** (число детекций / всего движков)
- **SHA-256:**

и шаблон для формы Google Play Protect (заполняется вручную):

- **Ссылка:** https://support.google.com/googleplay/android-developer/contact/protectappeals
- **Package name:** `net.qwdtt.client`
- **Additional information:** *"This is a personal open-source proxy and utility app built cleanly from source code. It contains no malware, uses standard network APIs, and is a false positive of Play Protect."*

После проверки хэшей на VirusTotal ты ОБЯЗАН автоматически запустить скрипт предзаполнения формы Google Play Protect в браузере пользователя: `scripts/google-play-appeal-prefill.py --sha256 <хэш основного APK arm64-v8a>` (заполняет Email, Package name, SHA-256, Additional information; Submit не нажимает — reCAPTCHA и отправку пользователь проходит вручную в открытом окне).
