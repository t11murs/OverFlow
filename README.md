# OrderFlow

Учебно-портфолийная микросервисная backend-система маркетплейса на Java 21 и Spring Boot.

- разделена на шесть независимых сервисов: Identity, Catalog, Inventory, Order, Payment и Notification
- оформляет заказ через оркестрируемую Saga с командами и событиями Kafka
- использует Transactional Outbox/Inbox, идемпотентные consumers, retry, DLQ и восстановление зависших шагов
- конкурентно резервирует остатки в PostgreSQL с блокировками и безопасной компенсацией
- поддерживает JWT/RBAC, ротацию refresh-токенов и межсервисную авторизацию
- кэширует публичные данные каталога в Redis
- экспортирует health-checks и Prometheus-метрики

## Стек

Java 21, Spring Boot, Spring Web MVC, Spring Data JPA, PostgreSQL, Flyway, Kafka, Redis, Spring Security, JWT, Testcontainers, JUnit 5, Mockito, Docker Compose, Prometheus, Helm и GitHub Actions.

## Сервисы

| Сервис | API | Management | Назначение |
| --- | ---: | ---: | --- |
| Identity | `8081` | `9081` | пользователи, продавцы, JWT и refresh-токены |
| Catalog | `8082` | `9082` | категории, товары и предложения продавцов |
| Inventory | `8083` | `9083` | склады, остатки и резервирование |
| Order | `8084` | `9084` | заказы и оркестрация Saga |
| Payment | `8085` | `9085` | списания, возвраты и сверка с провайдером |
| Notification | `8086` | `9086` | очередь и доставка итоговых уведомлений |

Payment и Notification получают бизнес-команды через Kafka. Их HTTP-порты не являются публичным бизнес-API.

## Старт

Требования: JDK 21 и Docker с Docker Compose. Глобально устанавливать Gradle не требуется — используется Gradle Wrapper.

Поднять PostgreSQL для каждого сервиса, Redis и Kafka в KRaft-режиме:

```bash
docker compose up -d
```

Запустить сервисы в отдельных терминалах:

```bash
./gradlew :services:identity-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:catalog-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:inventory-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:order-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:payment-service:bootRun --args='--spring.profiles.active=local'
./gradlew :services:notification-service:bootRun --args='--spring.profiles.active=local'
```

В Windows вместо `./gradlew` используется `gradlew.bat`.

После запуска Swagger UI доступен по адресу:

```text
http://localhost:<порт-сервиса>/swagger-ui.html
```

Остановка инфраструктуры:

```bash
docker compose down
```

Удаление локальных volumes с данными:

```bash
docker compose down -v
```

## Тесты

Полная проверка сервисов, Kafka-контрактов и архитектурных ограничений:

```bash
./gradlew qualityCheck
```

Сборка всех приложений:

```bash
./gradlew build
```

Интеграционные тесты используют Testcontainers с PostgreSQL и Kafka.

## Наблюдаемость

Для каждого сервиса доступны:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/prometheus`

Management endpoints вынесены на отдельные порты `9081–9086`. Система публикует метрики HTTP, Inbox/Outbox, worker-задач, просроченных lease, резервов, платежей и восстановления Saga.

Пример Prometheus endpoint Order Service:

```text
http://localhost:9084/actuator/prometheus
```

## Основные API

- `POST /api/v1/auth/register` — регистрация покупателя
- `POST /api/v1/auth/login` — вход и получение пары токенов
- `POST /api/v1/auth/refresh` — ротация refresh-токена
- `POST /api/v1/sellers` — создание организации продавца
- `GET|POST|PUT|PATCH /api/v1/catalog/products` — управление товарами
- `GET|POST|PUT|PATCH /api/v1/catalog/offers` — управление предложениями
- `GET|POST|PUT|PATCH /api/v1/inventory/warehouses` — управление складами
- `POST /api/v1/inventory/stock-adjustments` — идемпотентная корректировка остатков
- `GET /api/v1/inventory/offers/{offerId}/availability` — доступный остаток предложения
- `POST /api/v1/orders` — идемпотентное оформление заказа
- `GET /api/v1/orders` — заказы текущего покупателя
- `POST /api/v1/orders/{id}/cancellation` — запуск отмены и компенсации

## Как проходит заказ

1. Order получает актуальные снимки предложений из Catalog и сохраняет заказ вместе с Outbox-командой.
2. Inventory атомарно резервирует остатки и публикует результат через Kafka.
3. Payment выполняет идемпотентное списание вне транзакции базы данных.
4. Order подтверждает резерв либо запускает возврат платежа и освобождение остатков.
5. Notification получает только итоговое событие заказа и выполняет доставку отдельным worker-процессом.

Сервисы не используют общую базу данных и не имеют прямых Java-зависимостей друг на друга.
