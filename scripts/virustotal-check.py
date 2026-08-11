#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Автоматическая проверка релизных APK на VirusTotal (API v3).

Ключ API читается строго локально:
  1) переменная окружения VT_API_KEY, либо
  2) строка "VT_API_KEY=..." в local.properties корня проекта (в git не попадает).

Использование:
    python scripts/virustotal-check.py [файлы.apk ...]
    python scripts/virustotal-check.py app/build/outputs/apk/debug/qWDTT-*.apk

Для каждого файла: считается локальный SHA-256 -> если отчёт уже есть, печатается он;
иначе получается upload URL (нужен для файлов > 32 МБ), файл загружается,
и анализ опрашивается до завершения. В конце печатается шаблон для Google Play Protect.
"""
import glob
import hashlib
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request

API = "https://www.virustotal.com/api/v3"
USER_AGENT = "qWDTT-release-checker"


def read_api_key():
    key = os.environ.get("VT_API_KEY", "").strip()
    if key:
        return key
    here = os.path.dirname(os.path.abspath(__file__))
    props = os.path.normpath(os.path.join(here, "..", "local.properties"))
    if os.path.isfile(props):
        with open(props, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("VT_API_KEY="):
                    return line.split("=", 1)[1].strip()
    return ""


def api_request(method, url, api_key, data=None, headers=None):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("x-apikey", api_key)
    req.add_header("User-Agent", USER_AGENT)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read().decode("utf-8"))
        except Exception:
            body = {}
        return e.code, body


def get_upload_url(api_key):
    status, resp = api_request("GET", API + "/files/upload_url", api_key)
    if status == 200 and resp.get("data"):
        return resp["data"]
    return API + "/files"


def upload_file(api_key, path):
    with open(path, "rb") as f:
        content = f.read()
    name = os.path.basename(path)
    boundary = "----qWDTT" + secrets.token_hex(12)
    body = (
        ("--" + boundary + "\r\n"
         'Content-Disposition: form-data; name="file"; filename="' + name + '"\r\n'
         "Content-Type: application/octet-stream\r\n\r\n").encode("utf-8")
        + content
        + ("\r\n--" + boundary + "--\r\n").encode("utf-8")
    )
    headers = {"Content-Type": "multipart/form-data; boundary=" + boundary}
    return api_request("POST", get_upload_url(api_key), api_key, data=body, headers=headers)


def wait_analysis(api_key, analysis_id, timeout_sec=600):
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        status, resp = api_request("GET", API + "/analyses/" + analysis_id, api_key)
        if status == 200:
            attrs = resp.get("data", {}).get("attributes", {})
            if attrs.get("status") == "completed":
                return attrs.get("stats", {})
        time.sleep(10)
    return None


def check_file(api_key, path):
    with open(path, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()
    status, resp = api_request("GET", API + "/files/" + sha256, api_key)
    if status == 200:
        stats = resp["data"]["attributes"].get("last_analysis_stats", {})
        return sha256, stats
    if status == 404:
        _, up = upload_file(api_key, path)
        analysis_id = up.get("data", {}).get("id", "")
        if not analysis_id:
            return sha256, None
        return sha256, wait_analysis(api_key, analysis_id)
    return sha256, None


def format_stats(stats):
    if not stats:
        return "нет данных"
    total = sum(stats.values())
    return (
        f"детекций {stats.get('malicious', 0)} / движков {total} "
        f"(suspicious={stats.get('suspicious', 0)}, "
        f"harmless={stats.get('harmless', 0)}, undetected={stats.get('undetected', 0)})"
    )


def main(argv):
    api_key = read_api_key()
    if not api_key:
        print("Ошибка: ключ VirusTotal не найден. Задайте VT_API_KEY в env или в local.properties.", file=sys.stderr)
        return 2

    files = [os.path.normpath(p) for p in argv]
    if not files:
        files = sorted(glob.glob(os.path.normpath("app/build/outputs/apk/debug/qWDTT-*.apk")))
    if not files:
        print("Ошибка: не найдены APK. Передайте пути к файлам.", file=sys.stderr)
        return 2

    for p in files:
        if not os.path.isfile(p):
            print("Пропущен: нет файла " + p, file=sys.stderr)
            continue
        sha, stats = check_file(api_key, p)
        print(f"Имя файла: {os.path.basename(p)}")
        print(f"Статус в VirusTotal: {format_stats(stats)}")
        print(f"SHA-256: {sha}")
        print()

    print("=" * 70)
    print("Шаблон для Google Play Protect (форма заполняется вручную):")
    print("  Ссылка: https://support.google.com/googleplay/android-developer/contact/protectappeals")
    print("  Package name: net.qwdtt.client")
    print("  Additional information: \"This is a personal open-source proxy and utility app built cleanly")
    print("                           from source code. It contains no malware, uses standard network APIs,")
    print("                           and is a false positive of Play Protect.\"")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
