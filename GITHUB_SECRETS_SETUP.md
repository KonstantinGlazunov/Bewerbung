# Настройка GitHub Secrets для автодеплоя

## Проблема: Permission denied (publickey)

Если вы видите ошибку `Permission denied (publickey)`, это означает, что SSH ключ неправильно настроен в GitHub Secrets.

## Решение

### Шаг 1: Получите приватный SSH ключ

```bash
# Показать содержимое приватного ключа
cat ~/.ssh/ocivm_key
```

**Важно:** Скопируйте ВЕСЬ ключ, включая строки:
```
-----BEGIN OPENSSH PRIVATE KEY-----
...
-----END OPENSSH PRIVATE KEY-----
```

### Шаг 2: Добавьте секреты в GitHub

1. Перейдите в ваш репозиторий на GitHub
2. **Settings** → **Secrets and variables** → **Actions**
3. Нажмите **New repository secret**

#### Секрет 1: `VM_SSH_KEY`
- **Name:** `VM_SSH_KEY`
- **Secret:** Вставьте ВЕСЬ приватный ключ (включая BEGIN и END строки)
- **Важно:** 
  - Не добавляйте лишних пробелов в начале/конце
  - Сохраните все переносы строк
  - Ключ должен начинаться с `-----BEGIN` и заканчиваться `-----END`

#### Секрет 2: `VM_USER`
- **Name:** `VM_USER`
- **Secret:** `opc`

#### Секрет 3: `VM_HOST`
- **Name:** `VM_HOST`
- **Secret:** `130.162.224.203`

### Шаг 3: Проверка формата ключа

Правильный формат приватного ключа:
```
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
... (много строк) ...
-----END OPENSSH PRIVATE KEY-----
```

### Шаг 4: Альтернативный способ (если ключ в другом формате)

Если ваш ключ в формате RSA или другом, убедитесь что он начинается с:
- `-----BEGIN RSA PRIVATE KEY-----` или
- `-----BEGIN OPENSSH PRIVATE KEY-----` или
- `-----BEGIN PRIVATE KEY-----`

### Шаг 5: Проверка после настройки

После добавления секретов:
1. Перейдите в **Actions**
2. Запустите workflow вручную или сделайте push
3. Проверьте шаг "Test SSH connection" - он должен пройти успешно

## Troubleshooting

### Ошибка: "Permission denied (publickey)"

**Причины:**
1. Ключ скопирован не полностью
2. Лишние пробелы в начале/конце ключа
3. Неправильный формат ключа
4. Публичный ключ не добавлен на сервер

**Решение:**
1. Удалите секрет `VM_SSH_KEY` в GitHub
2. Скопируйте ключ заново: `cat ~/.ssh/ocivm_key`
3. Убедитесь, что скопировали ВСЕ строки
4. Создайте секрет заново

### Проверка публичного ключа на сервере

```bash
# Показать публичный ключ
ssh-keygen -y -f ~/.ssh/ocivm_key

# Проверить, что он есть на сервере
ssh -i ~/.ssh/ocivm_key opc@130.162.224.203 "cat ~/.ssh/authorized_keys"
```

Если публичного ключа нет на сервере, добавьте его:
```bash
ssh-copy-id -i ~/.ssh/ocivm_key.pub opc@130.162.224.203
```

### Проверка локального подключения

```bash
# Должно работать без пароля
ssh -i ~/.ssh/ocivm_key opc@130.162.224.203 "echo 'SSH OK'"
```

Если локально не работает, GitHub Actions тоже не будет работать.

## Быстрая проверка

После настройки секретов, проверьте в GitHub Actions:
1. Откройте последний workflow run
2. Найдите шаг "Test SSH connection"
3. Если видите "SSH OK" - все настроено правильно
4. Если видите "Permission denied" - проверьте секрет `VM_SSH_KEY`

