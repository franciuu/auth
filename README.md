# Production-Ready Auth System

A full-stack authentication & authorization application:

- **Backend** — Spring Boot 3 (Java 17), Spring Security, JWT, JPA/MySQL, Redis.
- **Frontend** — React 18 + Vite, minimal inline styling, two pages.

It implements JWT auth with refresh-token rotation, BCrypt hashing, Redis rate
limiting, token blacklisting on logout, role-based access control (RBAC),
audit logging, CSRF protection, CORS whitelisting, and security headers.

---

## Architecture

```
frontend/   React SPA (login/register + role-aware dashboard)
backend/    Spring Boot REST API
docker-compose.yml   MySQL 8 + Redis 7 for local dev
```

### Backend layout (`backend/src/main/java/com/example/auth`)

| Package      | Responsibility                                                        |
|--------------|-----------------------------------------------------------------------|
| `entity`     | `User`, `AuditLog`, `Role`, `AuditEventType`, `Severity`              |
| `repository` | Spring Data JPA repositories (indexed on `email`, `user_id`)         |
| `security`   | `JwtTokenProvider`, `JwtAuthenticationFilter`, entry-point/denied handlers |
| `service`    | `AuthService`, `UserService`, `RateLimitService`, `TokenBlacklistService`, `EmailVerificationService`, `AuditService` |
| `controller` | `AuthController`, `UserController`, `AdminController`                 |
| `config`     | `SecurityConfig`, `OpenApiConfig`, `DataInitializer`                  |
| `dto`        | Request/response records with bean-validation constraints            |
| `exception`  | `AuthException`, `GlobalExceptionHandler` (generic, no enumeration)  |

---

## Prerequisites

- Java 17+
- Maven 3.9+
- Node 18+
- Docker (optional, for MySQL + Redis) — or your own MySQL 8 and Redis 7

---

## Running

### 1. Start dependencies

```bash
docker compose up -d        # MySQL on :3306, Redis on :6379
```

> No MySQL? Run the backend with the `dev` profile to use in-memory H2
> (Redis is still required):
> `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`

### 2. Start the backend (port 8080)

```bash
cd backend
mvn spring-boot:run
```

A seed **admin** account is created on first start:

```
email:    admin@test.com
password: AdminPass123!
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

### 3. Start the frontend (port 3000)

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:3000>.

---

## Configuration

All values are overridable via environment variables (see
`backend/src/main/resources/application.properties`). Key ones:

| Variable                    | Default                         | Notes                                  |
|-----------------------------|---------------------------------|----------------------------------------|
| `JWT_SECRET`                | dev-only value                  | **Override in prod** (min 32 bytes)    |
| `JWT_ACCESS_TTL`            | `86400` (24h)                   | Access token lifetime (seconds)        |
| `JWT_REFRESH_TTL`           | `604800` (7d)                   | Refresh token lifetime (seconds)       |
| `RATE_LIMIT_MAX_ATTEMPTS`   | `5`                             | Failed logins per window               |
| `RATE_LIMIT_WINDOW_MINUTES` | `15`                            | Rate-limit window                      |
| `CORS_ALLOWED_ORIGINS`      | `localhost:3000, yourdomain.com`| Comma-separated whitelist              |
| `EMAIL_VERIFICATION_REQUIRED` | `false`                       | Block login until verified when `true` |
| `COOKIE_SECURE`             | `false`                         | Set `true` behind HTTPS                |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | seed admin creds           | Disable via `ADMIN_SEED_ENABLED=false` |

---

## API

| Method | Path                              | Auth        | Description                       |
|--------|-----------------------------------|-------------|-----------------------------------|
| POST   | `/api/auth/register`              | public      | `{email, fullName, password}`     |
| POST   | `/api/auth/login`                 | public      | `{email, password}` → tokens      |
| POST   | `/api/auth/refresh`               | public      | `{refreshToken}` (or cookie)      |
| POST   | `/api/auth/logout`                | Bearer      | Blacklists token, clears refresh  |
| POST   | `/api/auth/verify-email?token=`   | public      | Marks email verified              |
| GET    | `/api/user/profile`               | USER/ADMIN  | Current user's profile            |
| GET    | `/api/admin/users`                | ADMIN       | List all users                    |
| POST   | `/api/admin/disable-user/{id}`    | ADMIN       | Disable an account                |

Login/refresh return `{ success, accessToken, refreshToken, expiresIn }` and
also set the refresh token as an httpOnly, SameSite=Strict cookie.

---

## Security features

- **Passwords**: BCrypt cost factor 12; never stored or logged in plaintext.
- **Password policy**: 8+ chars incl. upper, lower, digit, special (`@$!%*?&`),
  enforced server-side via bean validation.
- **JWT**: HMAC-SHA256, signature + expiration + type-claim validation.
- **Refresh rotation**: each refresh issues a new token and invalidates the old
  one (one-time use). Reuse of a stale token revokes the chain.
- **Token blacklist**: access-token `jti` blacklisted in Redis on logout until
  its natural expiry.
- **Rate limiting**: max 5 failed logins per email+IP per 15 min (Redis).
- **RBAC**: `@PreAuthorize` + URL rules; `ROLE_ADMIN` vs `ROLE_USER`.
- **Generic errors**: identical responses prevent user enumeration.
- **Headers**: HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
  CSP, Referrer-Policy.
- **CSRF**: cookie-based tokens (`XSRF-TOKEN` / `X-XSRF-TOKEN`); public
  token-issuing endpoints exempt (Bearer APIs are not CSRF-exploitable).
- **CORS**: explicit origin whitelist with credentials.
- **Audit logging**: persisted to `audit_logs` — `USER_REGISTERED`,
  `LOGIN_SUCCESS`, `FAILED_LOGIN`, `UNAUTHORIZED_ACCESS`,
  `AUTHORIZATION_FAILURE`, `ADMIN_DISABLED_USER`, `TOKEN_REFRESHED`, `LOGOUT`.

---

## Verifying RBAC (manual test scenario)

```bash
BASE=http://localhost:8080

# 1. Register two users
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"user1@test.com","fullName":"User One","password":"SecurePass123!"}'
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"user2@test.com","fullName":"User Two","password":"SecurePass123!"}'

# 2. Login as user1 and capture the access token
U1=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"user1@test.com","password":"SecurePass123!"}' | jq -r .accessToken)

# 3. user1 CAN see own profile (200)
curl -s -o /dev/null -w "profile: %{http_code}\n" \
  $BASE/api/user/profile -H "Authorization: Bearer $U1"

# 4. user1 CANNOT list users (403)
curl -s -o /dev/null -w "admin/users as user: %{http_code}\n" \
  $BASE/api/admin/users -H "Authorization: Bearer $U1"

# 5. Login as the seed admin
ADMIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@test.com","password":"AdminPass123!"}' | jq -r .accessToken)

# 6. admin CAN list users (200) and disable user2
curl -s $BASE/api/admin/users -H "Authorization: Bearer $ADMIN" | jq '.[].email'
U2_ID=$(curl -s $BASE/api/admin/users -H "Authorization: Bearer $ADMIN" \
  | jq -r '.[] | select(.email=="user2@test.com") | .id')
curl -s -X POST $BASE/api/admin/disable-user/$U2_ID -H "Authorization: Bearer $ADMIN"

# 7. user2 can no longer log in (401)
curl -s -o /dev/null -w "disabled login: %{http_code}\n" \
  -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"user2@test.com","password":"SecurePass123!"}'
```

Audit events for all of the above are persisted in the `audit_logs` table.

---

## Tests

```bash
cd backend
mvn test
```

Includes unit tests for `JwtTokenProvider` (token issuance, type checks,
signature/tamper rejection, secret-length validation).
