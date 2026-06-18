# Module: `auth`

Owns users, registration, login, JWT issuance, and the "who am I" endpoint. The **only** module that knows about the `User` entity. Other modules reference users by `UUID` (the `BaseEntity.id`).

## Layout

```
auth/
├── controller/   AuthController    (/auth/register, /auth/login, /auth/me)
├── service/      AuthService
├── repository/   UserRepository    (JpaRepository<User, UUID>)
├── entity/       User, Role
└── dto/          RegisterRequest, LoginRequest, AuthResponse, MeResponse
```

## Entities

- **`User`** extends `BaseEntity`: `email` (unique, not null), `passwordHash` (not null), `role : Role` (`@Enumerated(STRING)`). Table: `users`.
- **`Role`**: `USER`, `ADMIN`. Add roles by adding enum constants — don't introduce a separate `roles` table unless multi-role support becomes a real requirement.

## DTOs (all records)

- **`RegisterRequest(@Email @NotBlank String email, @NotBlank @Size(min=8) String password)`**
- **`LoginRequest(@Email @NotBlank String email, @NotBlank String password)`**
- **`AuthResponse(String token, String tokenType)`** with `static bearer(String token)`.
- **`MeResponse(UUID id, String email, Role role)`** with `static from(User user)`.

## Service contract — `AuthService`

- **`register(RegisterRequest)`** — lowercase email, reject duplicates with `EmailAlreadyExistsException` (→ 409), BCrypt-hash the password, save `User(role=USER)`, issue JWT via `JwtService.generateToken(user.getId(), user.getEmail(), user.getRole())`, return `AuthResponse.bearer(...)`.
- **`login(LoginRequest)`** — lowercase email, lookup, `PasswordEncoder.matches`. Either missing user or wrong password → `InvalidCredentialsException` (→ 401) with a generic "Invalid credentials" message — **does not leak which field was wrong**.
- **`me(UUID userId)`** — fetch user, return `MeResponse.from(...)`. Throws `ResourceNotFoundException` if the JWT subject doesn't resolve (shouldn't normally happen for a valid token).

## JWT shape

- **Subject** = user UUID (string).
- **Claims** = `email` (string), `role` (`Role.name()`).
- Signed HS-family via `JwtService` (HMAC key from `automationhub.jwt.secret`, ≥32 UTF-8 bytes).
- Expiry = `automationhub.jwt.expiration` ms from issue time.

## Endpoints

| Method | Path             | Auth   | Status | Body / Returns                |
|--------|------------------|--------|--------|-------------------------------|
| POST   | `/auth/register` | public | 201    | `AuthResponse`                |
| POST   | `/auth/login`    | public | 200    | `AuthResponse`                |
| GET    | `/auth/me`       | bearer | 200    | `MeResponse`                  |

Only `POST /auth/register` and `POST /auth/login` are on `SecurityConfig`'s `permitAll` list — `/auth/me` requires a valid token. Missing/invalid token → 401 (via `HttpStatusEntryPoint(UNAUTHORIZED)`).

## Exception → status mapping

Added to the shared `GlobalExceptionHandler`:

| Exception                          | HTTP |
|------------------------------------|------|
| `EmailAlreadyExistsException`      | 409  |
| `InvalidCredentialsException`      | 401  |

## What does **not** belong here

- Token validation / filter wiring — lives in `infrastructure.security.JwtAuthFilter`.
- Authorization rules for other modules — those modules define their own access checks (using `CurrentUser`).
- Any reference to `Workflow`, notifications, etc.
