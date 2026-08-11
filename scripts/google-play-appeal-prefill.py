#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Предзаполнение формы Google Play Protect в браузере (Selenium).

Форма: https://support.google.com/googleplay/android-developer/contact/protectappeals
Заполняются: Email address, Application package name, SHA-256 hash, Additional information.
Submit НЕ нажимается: окно остаётся открытым, чтобы пользователь вручную прошёл
reCAPTCHA и отправил форму.

Запуск:
    python scripts/google-play-appeal-prefill.py --sha256 <hash>
    python scripts/google-play-appeal-prefill.py --apk app/build/outputs/apk/debug/qWDTT-arm64-v8a.apk

Требования:  pip install selenium   (драйвер подхватится автоматически через Selenium Manager;
Chrome/Edge устанавливать не нужно, если есть Chrome или Edge).
"""
import argparse
import hashlib
import os
import re
import sys

from selenium import webdriver
from selenium.webdriver.common.by import By

URL = "https://support.google.com/googleplay/android-developer/contact/protectappeals"

EMAIL = "jewbsv@gmail.com"
PACKAGE = "net.qwdtt.client"
INFO = (
    "This is a personal open-source proxy and utility app built cleanly from source code. "
    "It contains no malware, uses standard network APIs, and is a false positive of Play Protect."
)


def sha256_of(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def make_driver():
    errors = []
    # Chrome
    try:
        options = webdriver.ChromeOptions()
        options.add_experimental_option("detach", True)
        options.add_argument("--start-maximized")
        return webdriver.Chrome(options=options)
    except Exception as e:
        errors.append("Chrome: %s" % e)
    # Edge (Chromium)
    try:
        options = webdriver.EdgeOptions()
        options.add_experimental_option("detach", True)
        options.add_argument("--start-maximized")
        return webdriver.Edge(options=options)
    except Exception as e:
        errors.append("Edge: %s" % e)
    raise RuntimeError("Не удалось запустить браузер: " + " | ".join(errors))


def all_fillables(driver):
    """Все видимые поля ввода в документе и во вложенных iframe."""
    result = []

    def scan(ctx):
        try:
            for e in ctx.find_elements(By.XPATH, "//input | //textarea"):
                try:
                    if e.is_displayed() and e.is_enabled():
                        result.append(e)
                except Exception:
                    pass
        except Exception:
            pass

    scan(driver)
    for frame in driver.find_elements(By.XPATH, "//iframe"):
        try:
            driver.switch_to.frame(frame)
            scan(driver)
            driver.switch_to.parent_frame()
        except Exception:
            try:
                driver.switch_to.parent_frame()
            except Exception:
                pass
    return result


def haystack(driver, el):
    parts = [
        el.get_attribute("id") or "",
        el.get_attribute("name") or "",
        el.get_attribute("placeholder") or "",
        el.get_attribute("aria-label") or "",
        el.get_attribute("title") or "",
    ]
    eid = el.get_attribute("id")
    if eid:
        try:
            parts.append(driver.find_element(By.CSS_SELECTOR, 'label[for="%s"]' % eid).text)
        except Exception:
            pass
    try:
        parts.append(el.find_element(By.XPATH, "ancestor::label").text)
    except Exception:
        pass
    return " ".join(parts).lower()


def fill(driver, fields):
    fillables = all_fillables(driver)
    if not fillables:
        print("Не найдены поля ввода на странице. Возможно, изменилась структура формы.", file=sys.stderr)
        return

    matched = {}
    used = set()
    for el in fillables:
        if el in used:
            continue
        hs = haystack(driver, el)
        for idx, f in enumerate(fields):
            if idx in matched:
                continue
            if any(re.search(p, hs) for p in f["patterns"]):
                matched[idx] = el
                used.add(el)
                break

    ordered = [e for e in fillables if e not in used]
    for idx, f in enumerate(fields):
        if idx in matched:
            continue
        if ordered:
            matched[idx] = ordered.pop(0)

    for idx, el in matched.items():
        f = fields[idx]
        if not f["value"]:
            continue
        try:
            el.clear()
            el.send_keys(f["value"])
            print("Заполнено поле: %s" % f["name"])
        except Exception as e:
            print("Не удалось заполнить %s: %s" % (f["name"], e), file=sys.stderr)


def main(argv):
    parser = argparse.ArgumentParser(description="Предзаполнение формы Google Play Protect")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--sha256", dest="sha256", help="SHA-256 хэш APK (hex, нижний регистр)")
    group.add_argument("--apk", dest="apk", help="путь к APK — хэш вычислится автоматически")
    args = parser.parse_args(argv)

    sha = None
    if args.apk:
        if not os.path.isfile(args.apk):
            print("Ошибка: файл не найден: %s" % args.apk, file=sys.stderr)
            return 2
        sha = sha256_of(args.apk)
        print("Вычислен SHA-256: %s" % sha)
    elif args.sha256:
        sha = args.sha256.strip().lower()
    else:
        print("Внимание: не задан хэш (--sha256 или --apk) — поле hash останется пустым.", file=sys.stderr)

    print("Запуск браузера и открытие формы…")
    driver = make_driver()
    try:
        driver.get(URL)
        print("Форма загружается… дождитесь появления полей (до 30 c).")
        import time
        deadline = time.time() + 30
        while time.time() < deadline and len(all_fillables(driver)) < 2:
            time.sleep(1)

        fields = [
            {"name": "Email address", "patterns": [r"email"], "value": EMAIL},
            {"name": "Application package name", "patterns": [r"package"], "value": PACKAGE},
            {"name": "SHA-256 hash", "patterns": [r"sha.?256", r"hash"], "value": sha or ""},
            {"name": "Additional information", "patterns": [r"additional", r"information"], "value": INFO},
        ]
        fill(driver, fields)
        print()
        print("=" * 70)
        print("Форма предзаполнена. Пожалуйста:")
        print("  1) проверьте данные;")
        print("  2) пройдите reCAPTCHA;")
        print("  3) нажмите Отправить (Submit) вручную.")
        print("Submit скриптом не нажимается, окно останется открытым.")
    finally:
        # Держим процесс живым, пока пользователь не завершит; с detach=True
        # браузер не закроется даже после выхода из скрипта.
        try:
            input("Закрыть скрипт? Нажмите Enter (браузер останется открытым)…")
        except EOFError:
            time.sleep(3600)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
