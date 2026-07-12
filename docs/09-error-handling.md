# 09. Обработка ошибок

Централизованный обработчик: `exceptions/GlobalExceptionsHandler.kt` (`@RestControllerAdvice`).

Формат ответа — всегда `ApiErrorResponse` (`dto/responses/ApiErrorResponse.kt`):

```json
{
  "timestamp": "2026-07-12T12:34:56Z",
  "status": 400,
  "error": "Bad Request",
  "message": "displayName: must not be blank",
  "path": "/api/v1/session"
}
```

## Таблица исключений

| Исключение | HTTP | Handler | Файл |
|-----------|------|---------|------|
| `MethodArgumentNotValidException` | 400 | field errors as `message` | `GlobalExceptionsHandler.kt:18-32` |
| `IllegalArgumentException` | 400 | message | `:107-117` |
| `InvalidUuidFormatException` | 400 | «Неверный формат UUID» | `:119-129` |
| `HttpMessageNotReadableException` | 400 | «Некорректное тело запроса» | `:131-141` |
| `ExpiredJwtException` | 401 | «JWT токен истёк» | `:157-169` |
| `AccessDeniedException` | 403 | «Доступ запрещён» | `:35-47` |
| `AuthorizationDeniedException` | 403 | «Доступ запрещён» | `:143-155` |
| `UserRoleNotAllowedException` | 403 | сообщение исключения | `:50-62` |
| `IdNotFoundException` | 404 | сообщение исключения | `:93-105` |
| `DuplicateIdException` | 409 | сообщение исключения | `:79-91` |
| `SessionJoinRejectedException` | 409 | сообщение исключения | `:171-183` |
| `Exception` (catch-all) | 500 | сообщение + stacktrace в логи | `:65-77` |

## Кастомные исключения

Пакет `exceptions/`.

| Класс | Когда бросается |
|-------|-----------------|
| `IdNotFoundException` | Сущность по ID не найдена в БД (`UserService.getUserById`, `SessionService.getSessionEntity`, поиск оффера в `PlayerGameplayService.makeDecision`) |
| `DuplicateIdException` | Дубликат ID при создании (`UserService.create`) или повторное действие в раунде (`PlayerGameplayService.sendOffer` / `makeDecision`) |
| `InvalidUuidFormatException` | Не удалось распарсить UUID из строки |
| `InvalidJwtException` | Невалидный JWT в STOMP `CONNECT` (`JwtStompChannelInterceptor`) |
| `InvalidOfferException` | Некорректный оффер (заготовка, используется в контроллерах WS) |
| `UserRoleNotAllowedException` | Попытка регистрации с `Role.NPC` (`AuthService.quickRegister`) |
| `SessionJoinRejectedException` | Джойн запрещён (закрыто / переполнено / user == admin) (`SessionService.joinSession`, `joinSessionAsObserver`) |
| `SessionStompRejectedException` | STOMP-сообщение отклонено интерцептором (`PlaySessionStompChannelInterceptor`) |

## Специальные обработчики

### `RestAccessDeniedHandler` — `exceptions/RestAccessDeniedHandler.kt`

Реализует `AccessDeniedHandler`. Возвращает `ApiErrorResponse` со статусом 403 в JSON вместо стандартной HTML-ошибки Spring Security. Подключён в `SecurityConfiguration`.

### STOMP-ошибки

`InvalidJwtException` и `SessionStompRejectedException` не проходят через `GlobalExceptionsHandler` (тот работает только для HTTP MVC). Они логируются и приводят к STOMP `ERROR`-фрейму.

## Валидация полей — краткая таблица

| DTO | Поле | Правило | Сообщение |
|-----|------|---------|-----------|
| `CreateUserRequest` | `nickname` | `@NotBlank`, `@Size(3..42)` | required, длина |
| `CreateSessionRequest` | `displayName` | `@NotBlank`, `@Size(3..100)` | required, длина |
| `CreateSessionRequest` | `config` | `@Valid @NotNull` | required |
| `SessionConfigDto` | `numRounds` | `@Min(1) @Max(10) @Positive` | 1..10 |
| `SessionConfigDto` | `numTeams` | `@Max(5) @PositiveOrZero` | 0..5 |
| `SessionConfigDto` | `numPlayers` | `@Range(2..120)` | 2..120 |
| `SessionConfigDto` | `roundSum` | `@Range(10..100000)` | 10..100000 |
| `SessionConfigDto` | `timeoutMoveSec` | `@Range(10..300)` | 10..300 |
| `CreateOfferCmd` | `amount` | `@PositiveOrZero` | ≥ 0 |
| `MakeDecisionCmd` | `offerId` | `@NotBlank` | required |
| `AuthenticateUserRequestDto` | `id` | `@NotBlank` | required |

При нарушении → `MethodArgumentNotValidException` → 400 с полным списком field errors в `message`.

## См. также

- `docs/08-security.md` — цепочка авторизации.
- `docs/05-rest-api.md` — ошибки в разрезе REST endpoints.
- `docs/06-websocket-api.md` — как ошибки обрабатываются в STOMP.
