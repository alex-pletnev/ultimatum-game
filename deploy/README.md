# Временное размещение на своём сервере

Compose поднимает отдельные PostgreSQL, API и web; данные сохраняются в volume `ultimatum-server_database`. Порты БД/API не публикуются. Внешний HTTPS proxy обращается к `ultimatum-web:80` в сети `PROXY_NETWORK`.

Подготовьте файл окружения вне Git с правами 0600: `DB_PASSWORD`, `JWT_SIGNING_KEY` (стандартный Base64 от случайного ключа минимум 64 байта; не URL-safe Base64), `APP_CORS_ORIGINS` (список разрешённых HTTPS origins), `PROXY_NETWORK` (существующая сеть внешнего proxy). Не используйте тестовые пароли.

1. Backend: JDK21, `./gradlew check bootJar`.
2. Frontend: `pnpm test && pnpm build`; для сайта в корне задайте `VITE_BASE_PATH=/`, `VITE_API_BASE_URL=https://<host>/api/v1`, `VITE_WS_URL=wss://<host>/api/v1/ws`.
3. `docker compose --env-file <private-env-file> -f deploy/compose.server.yml up -d --build --wait`.
4. Проверьте `https://<host>/api/v1/actuator/health`, вход и WebSocket-игру в браузере. Для внешнего Caddy добавьте отдельный host-блок `reverse_proxy ultimatum-web:80` и выполните validate/reload. Существующие сайты не заменяйте.

Обновление выполняется теми же командами сборки и `up`. Перед обновлением делайте backup: `docker compose --env-file <private-env-file> -f deploy/compose.server.yml exec -T db pg_dump -U ultimatum -d ultimatum > backup.sql`. Храните backup вне web-root с правами 0600. `down -v` уничтожает БД — для обычного обновления он не нужен.

Это отдельное окружение текущего сервера, без старых Yandex Container Registry/VM. Старый workflow Release не запускайте для этой установки.
