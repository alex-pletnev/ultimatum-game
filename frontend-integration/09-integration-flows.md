# 09. Сквозные сценарии

Сложенные end-to-end flow'ы для типовых экранов фронта. Все примеры на TypeScript-псевдокоде с `fetch` и `@stomp/stompjs`.

Считается что накатана обёртка `api()` из [07-error-handling.md](07-error-handling.md).

---

## Сценарий 1: Регистрация → лобби → присоединение

**Экран регистрации:**

```typescript
async function register(nickname: string, role: 'PLAYER' | 'ADMIN' | 'OBSERVER') {
  const res = await api<JwtAuthenticationResponse>('/auth/quick-register', {
    method: 'POST',
    body: JSON.stringify({ nickname, role }),
  });
  saveTokens(res.accessToken, res.refreshToken);
  return res;
}
```

**Экран лобби (список сессий, куда можно присоединиться):**

```typescript
async function loadLobby(page = 0) {
  return api<Page<SessionResponse>>(
    `/session?state=CREATED&openToConnect=true&page=${page}&pageSize=20`
  );
}
```

**Присоединиться к FFA-сессии:**

```typescript
async function joinFfa(sessionId: string) {
  return api<SessionWithTeamsAndMembersResponse>(
    `/session/${sessionId}/join`,
    { method: 'POST' }
  );
}
```

**Присоединиться к TEAM_BATTLE (нужен teamId):**

```typescript
// Сначала показать список команд (из session.teams).
const session = await api<SessionWithTeamsAndMembersResponse>(
  `/session/${sessionId}/with-teams-and-members`
);
// user выбирает team из session.teams
async function joinTeam(sessionId: string, teamId: string) {
  return api<SessionWithTeamsAndMembersResponse>(
    `/session/${sessionId}/join?teamId=${teamId}`,
    { method: 'POST' }
  );
}
```

---

## Сценарий 2: Создание сессии (админ-панель)

```typescript
async function createSession(input: {
  displayName: string;
  sessionType: 'FREE_FOR_ALL' | 'TEAM_BATTLE';
  numRounds: number;
  numTeams: number;   // 0 для FFA
  numPlayers: number;
  roundSum: number;
  timeoutMoveSec: number;
}) {
  return api<SessionWithTeamsAndMembersResponse>('/session', {
    method: 'POST',
    body: JSON.stringify({
      displayName: input.displayName,
      openToConnect: true,
      config: {
        sessionType: input.sessionType,
        numRounds: input.numRounds,
        numTeams: input.numTeams,
        numPlayers: input.numPlayers,
        roundSum: input.roundSum,
        timeoutMoveSec: input.timeoutMoveSec,
      },
    }),
  });
}
```

После создания — редиректить админа в «комнату ожидания», подключить WS, показать список подключённых (`session.members`, обновляется через `/topic/session/{id}/sessionStatus`).

---

## Сценарий 3: Игровой экран (игрок в сессии)

```typescript
import { Client } from '@stomp/stompjs';

async function enterGameRoom(sessionId: string, myUserId: string) {
  // 1. Получить снапшот сессии
  const session = await api<SessionWithTeamsAndMembersResponse>(
    `/session/${sessionId}/with-teams-and-members`
  );

  // 2. Подключить WebSocket
  const client = new Client({
    brokerURL: 'ws://localhost:8080/api/v1/ws',
    connectHeaders: { Authorization: `Bearer ${accessToken}` },
    reconnectDelay: 5000,
    onConnect: () => {
      // Персональные ошибки — первая подписка
      client.subscribe('/user/queue/errors', (frame) => {
        const err = JSON.parse(frame.body) as ApiErrorResponse;
        showToast({ status: err.status, message: err.message });
      });

      // Broadcast'ы
      client.subscribe(`/topic/session/${sessionId}/sessionStatus`, (frame) => {
        const s = JSON.parse(frame.body) as SessionWithTeamsAndMembersResponse;
        store.setSession(s);
      });

      client.subscribe(`/topic/session/${sessionId}/roundStatus`, (frame) => {
        const r = JSON.parse(frame.body) as RoundResponse;
        store.setRound(r);
        // hints (myRole/myPendingActions) в broadcast'е дефолтные —
        // если нужны актуальные, догрузить:
        api<RoundResponse>(`/session/${sessionId}/current-round`)
          .then((full) => store.setRoundHints(full));
      });

      client.subscribe(`/topic/session/${sessionId}/offerCreated`, (frame) => {
        const o = JSON.parse(frame.body) as OfferCreatedResponse;
        store.addOffer(o);
      });

      client.subscribe(`/topic/session/${sessionId}/decisionMade`, (frame) => {
        const d = JSON.parse(frame.body) as DecisionMadeResponse;
        store.addDecision(d);
      });

      client.subscribe(`/topic/session/${sessionId}/offersShuffled`, (frame) => {
        const s = JSON.parse(frame.body) as OffersShuffledResponse;
        store.setShufflePairs(s.pairs);   // для UI «кто кому предложил»
      });

      client.subscribe(`/topic/session/${sessionId}/scoreUpdated`, (frame) => {
        const s = JSON.parse(frame.body) as SessionScoreDto;
        store.setScore(s);
      });

      // Персональные офферы после shuffle — приходят respondent'у
      client.subscribe(
        `/topic/session/${sessionId}/player/${myUserId}/offer`,
        (frame) => {
          const assigned = JSON.parse(frame.body) as AssignedOfferResponse;
          store.setMyIncomingOffer(assigned);
          // → показать UI «тебе предложили X от Y — accept/reject»
        }
      );
    },
  });

  client.activate();
  return client;
}
```

**Отправка оффера (в фазе WAIT_OFFERS):**

```typescript
function sendOffer(client: Client, sessionId: string, amount: number) {
  client.publish({
    destination: `/app/session/${sessionId}/offer.create`,
    body: JSON.stringify({ amount }),
  });
}
```

**Принять/отклонить оффер (в фазе OFFERS_SENT):**

```typescript
function makeDecision(
  client: Client,
  sessionId: string,
  offerId: string,
  accept: boolean
) {
  client.publish({
    destination: `/app/session/${sessionId}/make.decision`,
    body: JSON.stringify({ offerId, decision: accept }),
  });
}
```

---

## Сценарий 4: Админ-панель во время игры

Админ подключается тем же WS-клиентом (те же подписки), плюс имеет право отправлять управляющие команды.

```typescript
function startSession(client: Client, sessionId: string) {
  client.publish({ destination: `/app/session/${sessionId}/start`, body: '{}' });
}

function startNextRound(client: Client, sessionId: string) {
  client.publish({ destination: `/app/session/${sessionId}/round.start`, body: '{}' });
}

function abortCurrentRound(client: Client, sessionId: string) {
  client.publish({ destination: `/app/session/${sessionId}/round.abort`, body: '{}' });
}

function closeConnections(client: Client, sessionId: string) {
  client.publish({ destination: `/app/session/${sessionId}/close`, body: '{}' });
}

function openConnections(client: Client, sessionId: string) {
  client.publish({ destination: `/app/session/${sessionId}/open`, body: '{}' });
}
```

**Кнопки на UI:**
- «Начать игру» — `start` (доступна если session.state == CREATED и members.size ≥ 2).
- «Следующий раунд» — `round.start` (доступна в фазе ALL_DECISIONS_RECEIVED или ABORTED).
- «Прервать раунд» — `round.abort` (доступна в фазах WAIT_OFFERS / ALL_OFFERS_RECEIVED / OFFERS_SENT / ALL_DECISIONS_RECEIVED).
- «Закрыть / открыть для новых» — `close` / `open`.

---

## Сценарий 5: Наблюдатель

Присоединение через REST:
```typescript
async function joinAsObserver(sessionId: string) {
  return api<SessionWithTeamsAndMembersResponse>(
    `/session/${sessionId}/join/observer`,
    { method: 'POST' }
  );
}
```

WS-подключение — то же что и у игрока, кроме:
- НЕ отправляет `/app/.../offer.create` или `/app/.../make.decision` (сервер вернёт 403 в `/user/queue/errors`).
- НЕ нужно подписываться на `/topic/.../player/{userId}/offer` (для наблюдателя нет персональных офферов).

Всё остальное (broadcast'ы sessionStatus / roundStatus / offerCreated / decisionMade / scoreUpdated / offersShuffled) — открыто.

---

## Сценарий 6: Экран статистики после игры

**История раундов:**
```typescript
const rounds = await api<RoundResponse[]>(`/session/${sessionId}/rounds`);
```

**CSV-выгрузка (для админа-исследователя):**
```typescript
async function downloadCsv(sessionId: string) {
  const res = await fetch(
    `http://localhost:8080/api/v1/statistics/${sessionId}/csv`,
    { headers: { Authorization: `Bearer ${accessToken}` } }
  );
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `session-${sessionId}-stats.csv`;
  a.click();
  URL.revokeObjectURL(url);
}
```

---

## Гочи и советы

1. **Порядок событий per-destination гарантирован**, между разными — нет. UI должен реагировать на state, а не на порядок сообщений.

2. **`myRole`/`myPendingActions`** — актуальны только в REST-снапшотах (`/current-round`, `/rounds`). В WS-broadcast'ах они дефолтные. Стратегия: слушать WS для триггера «что-то изменилось», подтягивать актуальный state из REST для hints.

3. **AssignedOfferResponse НЕ содержит `responder`** — семантически это «этот оффер тебе». Не нужно проверять `responder.id == myUserId`.

4. **`/topic/.../offerCreated` (broadcast) содержит `responder: null` до shuffle** — заполнится только после shuffle. Не полагайся что respondent известен в момент отправки оффера.

5. **`offersShuffled`** — визуализация pairing (кто кому). Приходит один раз за раунд, при переходе `ALL_OFFERS_RECEIVED → OFFERS_SENT`.

6. **Скоринг кумулятивный.** `scoreUpdated` после каждого раунда содержит **суммарный** score, не delta.

7. **admin ≠ member.** Admin создаёт сессию но не может присоединиться в качестве игрока (`POST /session/{id}/join` вернёт 409). Admin присутствует в `session.admin`, не в `members`.

8. **Retry на 401 для REST** — стандартная практика (см. `apiWithRefresh` в [07-error-handling.md](07-error-handling.md)). Для WS — переустановить соединение с новым токеном.

9. **Backend не enforce'ит таймауты.** Если хочешь показать таймер отсчёта — считай на клиенте. Когда 0 — либо ждать других игроков, либо просить админа `round.abort`.
