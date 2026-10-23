---
id: T-107
title: Надёжное подключение и обновление состояния игры
status: done
priority: high
created: 2026-10-23
updated: 2026-10-23
related_code: [src/main/kotlin/edu/itmo/ultimatumgame/services/SessionService.kt, src/main/kotlin/edu/itmo/ultimatumgame/repositories/SessionRepository.kt]
related_docs: [docs/04-services.md]
tags: [backend, concurrency, ux]
---

## Контекст
При UX-рефакторинге frontend T-025 воспроизведён тупик: два одновременных приглашённых занимают последнее место одной команды. Состав 3/1 невозможно запустить. Клиентская проверка недостаточна.

## Acceptance criteria
- [x] Вход людей, отдельных ботов и bulk не переполняет команду.
- [x] Изменения состава используют общую транзакционную блокировку сессии.
- [x] Конкурентный вход возвращает один успех и один 409; другой игрок может выбрать свободную команду.
- [x] WebSocket-события отправляются после commit и не отправляются при rollback.
- [x] Unit, backend check и браузерная регрессия на PostgreSQL проходят.

## План
Проверять максимальную долю команды (не больше половины мест) до мутации; сериализовать изменения состава блокировкой строки session. Схема и формат DTO остаются совместимыми.

## Лог
- 2026-10-23: RED на настоящем PostgreSQL: конкурентные join возвращают [200, 200] вместо [200, 409]. Добавлены проверки людей, одиночного бота и bulk до создания пользователей.

- 2026-10-23: конкурентный E2E GREEN [200, 409], затем 2/2. Проверка production-игры выявила REST 404 до commit после раннего WS. Две регрессии EventPublisher были RED; отправка переносится в afterCommit.

## Проверка

- `./gradlew check bootJar --no-daemon` с JDK 21: успешно; 292 теста, Detekt main/test, JaCoCo threshold.
- Playwright frontend: 18/18 на отдельной PostgreSQL, без повторов. Конкурентные запросы дают [200, 409]; второй игрок затем входит в другую команду, итог 2/2.
- Production smoke frontend: регистрация, создание, боты, старт и счёт без 404 и ошибок JavaScript после переноса уведомлений.
- Независимое read-only ревью не выявило блокеров. Миграции и новые зависимости не понадобились.
- Существующая ошибка Springwolf при сканировании команд без @Payload остаётся в T-099; здоровье сервера и игра проверены.

## Публикация и блокер инфраструктуры

- Изменения кода: `f5a0f70`; [GitHub CI](https://github.com/alex-pletnev/ultimatum-game/actions/runs/39995760508) — success.
- Новый frontend `966a70c` опубликован на [GitHub Pages](https://alex-pletnev.github.io/ultimatum-game-ui/). Проверены мобильный учебный раунд и HashRouter на опубликованном сайте.
- [Release](https://github.com/alex-pletnev/ultimatum-game/actions/runs/39996302346) остановлен на Login to Yandex Container Registry: `UNAUTHENTICATED: Subject … was not found`. Секрет `YC_SA_JSON_CREDENTIALS` ссылается на недоступный сервисный аккаунт. Этап deploy пропущен, сервер не обновлён.
- Публичный backend не ответил на health-check ещё до попытки релиза (таймаут 15 секунд). Это отдельное от изменений кода состояние инфраструктуры.
- Следующий шаг: восстановить сервисный аккаунт/доступ к существующему реестру и обновить `YC_SA_JSON_CREDENTIALS`, затем повторить Release для `f5a0f70b851c5fa043bfd353523e55713de4e86b`. После выкладки проверить health и доступность совместной игры. Новые облачные ресурсы не создавались.
