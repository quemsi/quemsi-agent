# Pre-publish test checklist for quemsi-agent Docker image

## 1. Rebuild native binary AND Docker image (both steps required)

```bash
cd quemsi-agent
mvn -Pnative native:compile          # Linux ELF → target/quemsi-agent
docker build -t quemsi/quemsi-agent:2.4.22 .
```

**Important:** `docker build` alone reuses an old `target/quemsi-agent` if you skip Maven.
The binary must be built on **Linux** (not Windows `.exe`).

## 2. Test normal usage (no corporate CA)

```yaml
# docker-compose.test.yml
version: '3.9'
services:
  agent:
    image: quemsi/quemsi-agent:2.4.22
    environment:
      - CLIENT_ID=your-agent-id
      - CLIENT_SECRET=your-client-secret
    restart: unless-stopped
```

```bash
docker compose -f docker-compose.test.yml up
docker compose -f docker-compose.test.yml logs -f agent
```

### Expected success signs
- No `Could not resolve placeholder 'CLIENT_ID'` error
- No `SSLHandshakeException` on a personal/home network
- Logs show agent polling / connecting
- Agent appears **online** in Quemsi console within ~1 minute

### Quick smoke test (single command)
```bash
docker run --rm \
  -e CLIENT_ID=your-agent-id \
  -e CLIENT_SECRET=your-client-secret \
  quemsi/quemsi-agent:2.4.22
```

## 3. Test corporate CA path (optional before publish)

```bash
docker run --rm \
  -e CLIENT_ID=your-agent-id \
  -e CLIENT_SECRET=your-client-secret \
  -e TRUSTSTORE_PATH=/certs/corp-truststore.jks \
  -e TRUSTSTORE_PASSWORD=changeit \
  -v /path/to/corp-truststore.jks:/certs/corp-truststore.jks:ro \
  quemsi/quemsi-agent:2.4.22
```

## 4. Publish

```bash
docker push quemsi/quemsi-agent:2.4.22
```

## Common failures

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Could not resolve placeholder 'CLIENT_ID'` | Missing credentials or stale binary | Set `CLIENT_ID` / `CLIENT_SECRET`; rerun `mvn -Pnative native:compile` |
| `CLIENT_ID and CLIENT_SECRET must be set` | Env vars not passed to container | Use `CLIENT_ID` and `CLIENT_SECRET` in docker-compose |
| `exec format error` | Windows `.exe` copied instead of Linux binary | Build native on Linux |
| `SSLHandshakeException` on home network | Wrong/old image or bad truststore mount | Test without `TRUSTSTORE_PATH` first |
| `/bin/sh^M: bad interpreter` | CRLF line endings in entrypoint | `sed -i 's/\r$//' docker-entrypoint.sh` before build |
