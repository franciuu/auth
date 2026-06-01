# Auth System (simplified)

A full-stack authentication & authorization app, kept intentionally simple for learning:

- **Backend** — Spring Boot 3 (Java 17), Spring Security, JWT, JPA. Database
  defaults to in-memory **H2** (no external services needed); MySQL is one
  config block away.
- **Frontend** — React 18 + Vite, minimal inline styling, two pages.

Implements JWT auth with refresh-token rotation, BCrypt hashing, role-based
access control (RBAC), audit logging, CSRF protection, CORS whitelisting, and
security headers.

> Removed for simplicity: Redis, rate limiting, Docker, and email verification.
> Logout uses a simple in-memory token blacklist instead of Redis.

---

## Project layout

```
frontend/   React SPA (login/register + role-aware dashboard)
backend/    Spring Boot REST API
```

### Backend layout (`backend/src/main/java/com/example/auth`)

| Package      | Responsibility                                                        |
|--------------|-----------------------------------------------------------------------|
| `model`      | **Database table models** — `User`, `AuditLog` + enums (`Role`, `AuditEventType`, `Severity`) |
| `dto`        | **Request bodies only** — `RegisterRequest`, `LoginRequest`, `RefreshRequest` |
| `repository` | Spring Data JPA repositories (indexed on `email`, `user_id`)         |
| `security`   | `JwtTokenProvider`, `JwtAuthenticationFilter`, entry-point/denied handlers |
| `service`    | `AuthService`, `UserService`, `TokenBlacklistService`, `AuditService` |
| `controller` | `AuthController`, `UserController`, `AdminController`                 |
| `config`     | `SecurityConfig`, `OpenApiConfig`, `DataInitializer`                  |
| `exception`  | `AuthException`, `GlobalExceptionHandler`                            |

**Models vs DTOs:** `model` classes map to the database tables and are returned
directly in responses (sensitive fields like `passwordHash` and `refreshToken`
are hidden with `@JsonIgnore`). `dto` classes are used **only for incoming
requests**. Token responses (login/refresh) are returned as a simple map.

---

## Prerequisites

- Java 17+
- Maven 3.9+
- Node 18+

That's it — no database or Redis to install (H2 runs in memory).

---

## Running

### Backend (port 8080)

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

### Frontend (port 3000)

```bash
cd frontend
npm install
npm run dev
```

Open <http://localhost:3000>.

### Using MySQL instead of H2 (optional)

Open `backend/src/main/resources/application.properties`, comment out the H2
block and uncomment the MySQL block. No other changes needed.

---

## API

| Method | Path                              | Auth        | Description                       |
|--------|-----------------------------------|-------------|-----------------------------------|
| POST   | `/api/auth/register`              | public      | `{email, fullName, password}`     |
| POST   | `/api/auth/login`                 | public      | `{email, password}` → tokens      |
| POST   | `/api/auth/refresh`               | public      | `{refreshToken}` (or cookie)      |
| POST   | `/api/auth/logout`                | Bearer      | Revokes token, clears refresh     |
| GET    | `/api/user/profile`               | USER/ADMIN  | Current user (User model)         |
| GET    | `/api/admin/users`                | ADMIN       | List all users (User models)      |
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
- **Logout**: access-token id (`jti`) added to an in-memory blacklist; the
  refresh token is cleared so it can't be rotated.
- **RBAC**: `@PreAuthorize` + URL rules; `ROLE_ADMIN` vs `ROLE_USER`.
- **Generic errors**: identical responses prevent user enumeration.
- **Headers**: HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
  CSP, Referrer-Policy.
- **CSRF**: cookie-based tokens (`XSRF-TOKEN` / `X-XSRF-TOKEN`); public
  token-issuing endpoints exempt.
- **CORS**: explicit origin whitelist with credentials.
- **Audit logging**: persisted to `audit_logs` — `USER_REGISTERED`,
  `LOGIN_SUCCESS`, `FAILED_LOGIN`, `UNAUTHORIZED_ACCESS`,
  `AUTHORIZATION_FAILURE`, `ADMIN_DISABLED_USER`, `TOKEN_REFRESHED`, `LOGOUT`.

---

## Verifying RBAC (manual test scenario)

```bash
BASE=http://localhost:8080

# Register two users
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"user1@test.com","fullName":"User One","password":"SecurePass123!"}'
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"user2@test.com","fullName":"User Two","password":"SecurePass123!"}'

# Login as user1
U1=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"user1@test.com","password":"SecurePass123!"}' | jq -r .accessToken)

curl -s -o /dev/null -w "profile: %{http_code}\n"            $BASE/api/user/profile -H "Authorization: Bearer $U1"  # 200
curl -s -o /dev/null -w "admin/users as user: %{http_code}\n" $BASE/api/admin/users  -H "Authorization: Bearer $U1"  # 403

# Login as admin (CSRF: grab the cookie, echo the header on the POST)
JAR=$(mktemp)
ADMIN=$(curl -s -c $JAR -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@test.com","password":"AdminPass123!"}' | jq -r .accessToken)
curl -s -c $JAR $BASE/api/admin/users -H "Authorization: Bearer $ADMIN" | jq '.[].email'   # 200
XSRF=$(awk '/XSRF-TOKEN/{print $NF}' $JAR)
curl -s -b $JAR -X POST $BASE/api/admin/disable-user/3 \
  -H "Authorization: Bearer $ADMIN" -H "X-XSRF-TOKEN: $XSRF"                                # disable user2

# user2 can no longer log in (401)
curl -s -o /dev/null -w "disabled login: %{http_code}\n" \
  -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"user2@test.com","password":"SecurePass123!"}'
```

---

## Tests

```bash
cd backend
mvn test
```

Unit tests for `JwtTokenProvider` (token issuance, type checks,
signature/tamper rejection, secret-length validation).
