# Bewerbung AI

**English** | [Русский](#русский)

---

## English

### Overview

Bewerbung AI is a Spring Boot application that uses OpenAI's GPT models to automatically generate German job application documents (Bewerbung). The application analyzes job postings and candidate biographies to create personalized cover letters (Anschreiben) and CVs (Lebenslauf) following German DIN 5008 standards.

### Features

- **Job Posting Analysis**: Automatically extracts requirements and key information from job postings
- **Cover Letter Generation**: Creates personalized cover letters (Anschreiben) tailored to specific job postings
- **CV Generation**: Generates professional CVs (Lebenslauf) from candidate biographies
- **Change Detection**: Optimizes API usage by detecting changes in input data and skipping unnecessary processing
- **Biography Parsing**: Supports both structured JSON and free-form text biography parsing using AI
- **REST API**: Provides multiple endpoints for document generation

### Technology Stack

- **Java 21**
- **Spring Boot 3.2.0**
- **OpenAI API** (GPT-4o, GPT-4o-mini)
- **Maven**
- **Spring WebFlux** (for async API calls)

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- OpenAI API key

### Installation

1. Clone the repository:
```bash
git clone <repository-url>
cd BewerbungAi
```

2. Set up environment variables:
   - Create a `.env` file or set the `GPT_API_KEY` environment variable:
```bash
export GPT_API_KEY=your-openai-api-key-here
```

   - Alternatively, you can use the `variables.env` file (make sure to keep it secure and never commit it to version control)

3. Build the project:
```bash
mvn clean install
```

4. Run the application:
```bash
mvn spring-boot:run
```

The application will start on port 8080 by default (configurable via `PORT` environment variable).

### API Endpoints

#### Health Check
- **GET** `/api/bewerbung/health`
  - Returns service status

#### Generate Documents
- **POST** `/api/generate`
  - Generates both cover letter and CV
  - Request body:
```json
{
  "biography": {
    "name": "John Doe",
    "email": "john@example.com",
    "workExperience": [...],
    "education": [...],
    "skills": [...]
  },
  "jobPosting": "Job posting text here..."
}
```

- **POST** `/api/generate/cover-letter`
  - Generates only the cover letter (Anschreiben)

- **POST** `/api/generate/cv`
  - Generates only the CV (Lebenslauf)

- **POST** `/api/generate/from-file`
  - Generates documents from uploaded biography file
  - Parameters:
    - `biographyFile`: Multipart file containing biography
    - `jobPosting`: Job posting text

### Configuration

The application can be configured via `application.properties`:

- `openai.api.key`: OpenAI API key (defaults to `GPT_API_KEY` environment variable)
- `openai.api.url`: OpenAI API endpoint
- `openai.model.light`: Model for analysis tasks (default: `gpt-4o-mini`)
- `openai.model.heavy`: Model for document generation (default: `gpt-4o`)
- `server.port`: Server port (default: 8080)

### Project Structure

```
src/main/java/com/bewerbung/
├── controller/          # REST controllers
├── service/            # Business logic services
├── model/              # Data models
├── dto/                # Data transfer objects
├── exception/          # Exception handling
└── runner/             # Application runners
```

### Output

Generated documents are saved to the `output/` directory:
- `analysis.txt`: Job requirements analysis
- `anschreiben.txt`: Generated cover letter
- `notes.txt`: Processing notes and change detection information

### Development

To run tests:
```bash
mvn test
```

### Security Notes

- Never commit API keys or sensitive information to version control
- Use environment variables or secure configuration management
- The `variables.env` file should be added to `.gitignore`

### License

[Add your license here]

---

## Русский

### Обзор

Bewerbung AI — это Spring Boot приложение, которое использует модели GPT от OpenAI для автоматической генерации немецких документов для трудоустройства (Bewerbung). Приложение анализирует вакансии и биографии кандидатов для создания персонализированных сопроводительных писем (Anschreiben) и резюме (Lebenslauf) в соответствии с немецкими стандартами DIN 5008.

### Возможности

- **Анализ вакансий**: Автоматически извлекает требования и ключевую информацию из объявлений о вакансиях
- **Генерация сопроводительных писем**: Создает персонализированные сопроводительные письма (Anschreiben), адаптированные под конкретные вакансии
- **Генерация резюме**: Создает профессиональные резюме (Lebenslauf) из биографий кандидатов
- **Обнаружение изменений**: Оптимизирует использование API, обнаруживая изменения во входных данных и пропуская ненужную обработку
- **Парсинг биографии**: Поддерживает как структурированный JSON, так и свободный текстовый формат биографии с использованием AI
- **REST API**: Предоставляет несколько эндпоинтов для генерации документов

### Технологический стек

- **Java 21**
- **Spring Boot 3.2.0**
- **OpenAI API** (GPT-4o, GPT-4o-mini)
- **Maven**
- **Spring WebFlux** (для асинхронных вызовов API)

### Требования

- Java 21 или выше
- Maven 3.6+
- Ключ API OpenAI

### Установка

1. Клонируйте репозиторий:
```bash
git clone <repository-url>
cd BewerbungAi
```

2. Настройте переменные окружения:
   - Создайте файл `.env` или установите переменную окружения `GPT_API_KEY`:
```bash
export GPT_API_KEY=ваш-ключ-openai-api
```

   - Альтернативно, вы можете использовать файл `variables.env` (убедитесь, что он защищен и никогда не коммитится в систему контроля версий)

3. Соберите проект:
```bash
mvn clean install
```

4. Запустите приложение:
```bash
mvn spring-boot:run
```

Приложение запустится на порту 8080 по умолчанию (настраивается через переменную окружения `PORT`).

### API Эндпоинты

#### Проверка работоспособности
- **GET** `/api/bewerbung/health`
  - Возвращает статус сервиса

#### Генерация документов
- **POST** `/api/generate`
  - Генерирует и сопроводительное письмо, и резюме
  - Тело запроса:
```json
{
  "biography": {
    "name": "Иван Иванов",
    "email": "ivan@example.com",
    "workExperience": [...],
    "education": [...],
    "skills": [...]
  },
  "jobPosting": "Текст вакансии здесь..."
}
```

- **POST** `/api/generate/cover-letter`
  - Генерирует только сопроводительное письмо (Anschreiben)

- **POST** `/api/generate/cv`
  - Генерирует только резюме (Lebenslauf)

- **POST** `/api/generate/from-file`
  - Генерирует документы из загруженного файла биографии
  - Параметры:
    - `biographyFile`: Multipart файл с биографией
    - `jobPosting`: Текст вакансии

### Конфигурация

Приложение можно настроить через `application.properties`:

- `openai.api.key`: Ключ API OpenAI (по умолчанию берется из переменной окружения `GPT_API_KEY`)
- `openai.api.url`: Эндпоинт API OpenAI
- `openai.model.light`: Модель для задач анализа (по умолчанию: `gpt-4o-mini`)
- `openai.model.heavy`: Модель для генерации документов (по умолчанию: `gpt-4o`)
- `server.port`: Порт сервера (по умолчанию: 8080)

### Структура проекта

```
src/main/java/com/bewerbung/
├── controller/          # REST контроллеры
├── service/            # Сервисы бизнес-логики
├── model/              # Модели данных
├── dto/                # Объекты передачи данных
├── exception/          # Обработка исключений
└── runner/             # Запускающие классы приложения
```

### Вывод

Сгенерированные документы сохраняются в директорию `output/`:
- `analysis.txt`: Анализ требований вакансии
- `anschreiben.txt`: Сгенерированное сопроводительное письмо
- `notes.txt`: Заметки обработки и информация об обнаружении изменений

### Разработка

Для запуска тестов:
```bash
mvn test
```

### Примечания по безопасности

- Никогда не коммитьте API ключи или конфиденциальную информацию в систему контроля версий
- Используйте переменные окружения или безопасное управление конфигурацией
- Файл `variables.env` должен быть добавлен в `.gitignore`

### Лицензия

[Добавьте вашу лицензию здесь]

