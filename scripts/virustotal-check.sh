#!/usr/bin/env bash
# ==============================================================================
#  Проверка APK на VirusTotal (VirusTotal API v3)
#  Требуется ключ API: https://www.virustotal.com/gui/join  (free-аккаунт)
#
#  Использование:
#    VT_API_KEY=<ключ> scripts/virustotal-check.sh [файлы.apk ...]
#    VT_API_KEY=<ключ> scripts/virustotal-check.sh app/build/outputs/apk/debug/*.apk
#
#  Если отчёт по SHA-256 уже есть — печатает его.
#  Если нет — загружает файл и опрашивает анализ до готовности.
# ==============================================================================
set -uo pipefail

KEY="${VT_API_KEY:-}"
API="https://www.virustotal.com/api/v3"
GUI="https://www.virustotal.com/gui/file"

if [ -z "$KEY" ]; then
    echo "Ошибка: не задан VT_API_KEY (переменная окружения)."
    echo "Пример: VT_API_KEY=ваш_ключ scripts/virustotal-check.sh app/build/outputs/apk/debug/*.apk"
    exit 1
fi

command -v curl >/dev/null 2>&1 || { echo "Ошибка: curl не найден"; exit 1; }

sha256_of() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | awk '{print $1}'
    else
        # Windows fallback (certutil)
        certutil -hashfile "$1" SHA256 2>/dev/null | grep -E '^[0-9a-fA-F]{64}$' | tr 'A-F' 'a-f'
    fi
}

files=("$@")
if [ ${#files[@]} -eq 0 ]; then
    files=(app/build/outputs/apk/debug/*.apk)
fi

failed=0
for f in "${files[@]}"; do
    [ -f "$f" ] || { echo "Файл не найден: $f"; failed=1; continue; }
    echo "========================================================"
    echo "Файл : $f"
    hash=$(sha256_of "$f")
    [ -z "$hash" ] && { echo "Не удалось посчитать SHA-256"; failed=1; continue; }
    echo "SHA-256: $hash"

    # 1) Есть ли уже отчёт по хэшу
    resp=$(curl -sS -H "x-apikey: $KEY" "$API/files/$hash")

    if echo "$resp" | grep -q '"data"'; then
        stats=$(echo "$resp" | grep -o '"stats":{"[^}]*}' | sed 's/"stats":{//')
        names=$(echo "$resp" | grep -o '"meaningful_name":"[^"]*"' | head -1)
        echo "  Статус: отчёт уже есть"
        echo "  $names"
        echo "  Детекты: $stats"
        echo "  Ссылка: $GUI/$hash"
        continue
    fi

    # 2) Загрузка файла
    echo "  Отчёта нет — загружаю на VirusTotal..."
    up=$(curl -sS -H "x-apikey: $KEY" -F "file=@$f" "$API/files")
    if ! echo "$up" | grep -q '"analysis"'; then
        echo "  Ошибка загрузки: $(echo "$up" | grep -o '"error":{[^}]*}' | head -1)"
        failed=1
        continue
    fi
    aid=$(echo "$up" | grep -o '"analysis":{"id":"[^"]*"' | sed 's/.*"id":"//;s/"$//')
    echo "  Analysis id: $aid"

    # 3) Ожидание завершения анализа
    for i in $(seq 1 60); do
        sleep 5
        ar=$(curl -sS -H "x-apikey: $KEY" "$API/analyses/$aid")
        status=$(echo "$ar" | grep -o '"status":"[^"]*"' | sed 's/"status":"//;s/"$//')
        [ "$status" = "completed" ] && break
        [ "$status" = "queued" ] || [ "$status" = "running" ] || break
    done

    if [ "$status" = "completed" ]; then
        stats=$(echo "$ar" | grep -o '"stats":{"[^}]*}' | sed 's/"stats":{//')
        echo "  Статус: completed"
        echo "  Детекты: $stats"
        echo "  Ссылка: $GUI/$hash"
    else
        echo "  Анализ не завершился (статус: ${status:-unknown}). Проверьте позже: $GUI/$hash"
    fi
done

echo "========================================================"
[ "$failed" -ne 0 ] && exit 1
exit 0
