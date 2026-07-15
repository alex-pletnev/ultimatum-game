# 07. Обработка ошибок

Единый формат ошибок и для REST, и для STOMP:

```json
{
  "timestamp": "2026-07-15T09:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "displayName: must not be blank",
  "path": "/api/v1/session"     // "stomp" для WS-ошибок
}
```

## REST-ошибки

| HTTP | Когда | Что делать на фронте |
|------|-------|----------------------|
| **400** Bad Request | Валидация не прошла (nickname пустой, amount > roundSum, etc.); некорректный UUID; невалидный JSON | Показать пользователю `message` (обычно приемлемый для показа); повторный запрос без изменений бесполезен |
| **401** Unauthorized | JWT истёк / подделан / некорректная подпись / некорректная структура / access-токен передан вместо refresh для `/auth/refresh` | Попробовать refresh; если refresh тоже 401 — вылогинить |
| **403** Forbidden | Роль не подходит; попытка регистрации с `role: NPC`; access denied в filter chain | Не повторять; проверить что user делает то что ему положено |
| **404** Not Found | Сущность не найдена (`sessionId`, `offerId`, ...) | Пользователь ошибся URL'ом или объект удалён; показать 404-page |
| **409** Conflict | Дублирующее действие (уже отправил offer/decision); нарушение бизнес-инварианта (join в переполненную сессию, admin в join и т.п.); неправильная фаза | Показать `message`; часто фронт может защитить от этого upfront (грейанутая кнопка) |
| **500** Internal Server Error | Непредвиденная ошибка; message всегда generic ("Внутренняя ошибка сервера") — детали в логах сервера, не утекают клиенту | Показать общий toast; retry опционально |

**Не пробуй парсить `message`** — он на русском, свободного формата. Полагайся на `status`.

## Детальная таблица исключений → HTTP

| Исключение (сервер) | HTTP | Типичный `message` |
|---------------------|------|--------------------|
| `MethodArgumentNotValidException` | 400 | Список field errors: `"nickname: must not be blank; role: must not be null"` |
| `IllegalArgumentException` | 400 | Сообщение исключения (напр. `"offerValue должен быть в диапазоне [0, 100], получено 150"`) |
| `InvalidUuidFormatException` | 400 | `"Неверный формат UUID"` |
| `HttpMessageNotReadableException` | 400 | `"Некорректное тело запроса"` (плохой JSON) |
| `ExpiredJwtException` | 401 | `"JWT токен истёк"` |
| `InvalidJwtException` | 401 | `"Невалидный JWT"` / `"Ожидался refresh-токен"` / `"Refresh-токен невалиден..."` |
| `SignatureException` (jjwt) | 401 | `"Некорректная подпись JWT"` |
| `MalformedJwtException` (jjwt) | 401 | `"Некорректный JWT"` |
| `AccessDeniedException` | 403 | `"Доступ запрещён"` |
| `AuthorizationDeniedException` | 403 | `"Доступ запрещён"` |
| `UserRoleNotAllowedException` | 403 | `"Роль 'NPC' недоступна к созданию таким образом"` |
| `IdNotFoundException` | 404 | Сообщение с id |
| `DuplicateIdException` | 409 | `"Вы уже отправили offer для этого раунда"` / т.п. |
| `IllegalStateException` | 409 | Нарушение фазы, например `"Оффер нельзя отправить в фазе ABORTED, требуется WAIT_OFFERS"` |
| `SessionJoinRejectedException` | 409 | Причина отказа: закрыта / переполнена / admin |
| `Exception` (catch-all) | 500 | `"Внутренняя ошибка сервера"` (stack-trace **не** отдаётся клиенту) |

## STOMP-ошибки

Ошибки из `SEND`-команд доставляются в персональную очередь **`/user/queue/errors`** как тот же `ApiErrorResponse`.

**Обязательно подписаться сразу после CONNECT**, иначе пользователь не увидит причины отказов.

```typescript
client.subscribe('/user/queue/errors', (frame) => {
  const err = JSON.parse(frame.body) as ApiErrorResponse;
  // Например:
  showToast({
    variant: err.status >= 500 ? 'error' : 'warning',
    title: `${err.status} ${err.error}`,
    message: err.message,
  });
});
```

Матрица маппинга — та же что и у REST (см. таблицу выше). `path: "stomp"` вместо REST-path.

Типичные ошибки в WS-контексте:
- `409 IllegalStateException` — попытка отправить оффер в фазе `ABORTED` / `OFFERS_SENT` / etc.
- `409 DuplicateIdException` — повторный `offer.create` от того же игрока в раунде.
- `404 IdNotFoundException` — `make.decision` с несуществующим `offerId`.
- `400 IllegalArgumentException` — `amount > roundSum`.

## Обработка на клиенте

### REST — базовый HTTP-клиент

```typescript
async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`http://localhost:8080/api/v1${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken && { Authorization: `Bearer ${accessToken}` }),
      ...init?.headers,
    },
  });
  if (!res.ok) {
    const err = (await res.json()) as ApiErrorResponse;
    throw new ApiError(err);
  }
  return res.json();
}
```

### 401-interceptor с refresh

```typescript
async function apiWithRefresh<T>(path: string, init?: RequestInit): Promise<T> {
  try {
    return await api<T>(path, init);
  } catch (e) {
    if (e instanceof ApiError && e.status === 401 && !path.startsWith('/auth/')) {
      await refreshAccessToken();
      return await api<T>(path, init);          // retry с новым токеном
    }
    throw e;
  }
}

async function refreshAccessToken() {
  const res = await fetch('http://localhost:8080/api/v1/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) {
    logout();
    throw new Error('refresh failed');
  }
  const data = (await res.json()) as JwtAuthenticationResponse;
  accessToken = data.accessToken;
  // refreshToken прежний — сервер прислал null (rotation off)
}
```

### WS — соединение и переподключение при истечении токена

```typescript
function connectStomp() {
  const client = new Client({
    brokerURL: 'ws://localhost:8080/api/v1/ws',
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe('/user/queue/errors', handleWsError);
      // остальные subscribe
    },
    onStompError: async (frame) => {
      // Например «Authentication required» при истёкшем JWT.
      if (frame.headers['message']?.includes('Auth')) {
        await refreshAccessToken();
        client.deactivate();
        connectStomp();       // с новым токеном
      }
    },
  });
  client.activate();
}
```
