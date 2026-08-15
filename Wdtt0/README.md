# Wdtt0 для StatusOpenVPN

Скрипты, чтобы [StatusOpenVPN](https://github.com/TheMurmabis/StatusOpenVPN) показывал читаемые имена клиентов интерфейса `wdtt0` из qWDTT/WDTT-сервера.

## Зачем

qWDTT-сервер создаёт WireGuard-интерфейс `wdtt0` в userspace через `wireguard-go`. StatusOpenVPN ищет конфиг `/etc/wireguard/wdtt0.conf`, но такого файла нет, поэтому в столбце **Name** отображается `N/A`.

Эти скрипты:

- собирают имена клиентов из `/etc/wdtt/passwords.json` (метки Telegram-бота);
- генерируют `/etc/wireguard/wdtt0.conf` в нужном StatusOpenVPN формате;
- пропатчивают StatusOpenVPN, чтобы он читал этот конфиг;
- обновляют конфиг каждую минуту по cron.

## Требования

- Установленный [StatusOpenVPN](https://github.com/TheMurmabis/StatusOpenVPN) в `/root/web`.
- qWDTT/WDTT-сервер с Telegram-ботом и файлом `/etc/wdtt/passwords.json`.
- root-права на VPS.
- `python3`, `wg`.

## Быстрая установка

```bash
bash -c "$(curl -sL https://raw.githubusercontent.com/jewbsv/proxy-turn-vk-android/master/Wdtt0/setup.sh)"
```

Или вручную:

```bash
cp install_Wdtt0.sh uninstall_Wdtt0.sh /root/
chmod +x /root/install_Wdtt0.sh /root/uninstall_Wdtt0.sh
/root/install_Wdtt0.sh
```

После установки подождите до минуты и обновите страницу StatusOpenVPN — имена клиентов появятся вместо `N/A`.

## Как работает

1. `wdtt0-sync-names.py` читает `passwords.json` и вывод `wg show wdtt0`.
2. Для каждого пира подбирается метка (`label`) клиента. Если метки нет — используется пароль. Осиротевшие пиры игнорируются.
3. Результат пишется в `/etc/wireguard/wdtt0.conf` с комментариями `# Client = <имя>` перед каждым `[Peer]`.
4. Cron каждую минуту перезапускает синхронизацию.
5. StatusOpenVPN патчится так, чтобы для `wdtt0` читать имена из этого конфига.

## Удаление

```bash
bash -c "$(curl -sL https://raw.githubusercontent.com/jewbsv/proxy-turn-vk-android/master/Wdtt0/setup.sh)" -- --uninstall
```

Или, если скрипты уже на сервере:

```bash
/root/uninstall_Wdtt0.sh
```

Скрипт восстановит оригинальные файлы StatusOpenVPN и удалит `/usr/local/bin/wdtt0-sync-names.py`, `/etc/wireguard/wdtt0.conf` и cron-задание.

## Обновление StatusOpenVPN

После обновления дашборда патч может слететь. Просто повторно запустите:

```bash
bash -c "$(curl -sL https://raw.githubusercontent.com/jewbsv/proxy-turn-vk-android/master/Wdtt0/setup.sh)"
```

## Ограничения

- Имена берутся только из базы Telegram-бота (`/etc/wdtt/passwords.json`).
- Пир, не привязанный ни к одному паролю, не отображается.
- Это workaround, а не встроенная функция StatusOpenVPN.
