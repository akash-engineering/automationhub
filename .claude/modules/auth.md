# Module: `auth`

Owns users, registration, login, and JWT issuance. The **only** module that knows about the `User` entity. Other modules reference users by `UUID` (the `BaseEntity.id`).

## Layout

```
auth/
├── controller/   AuthController        (/auth/register, /auth/login)
├── service/      AuthService
├── repository/   UserRepository        (JpaRepository<User, UUID>)
├── entity/       User, Role
└── dto/          RegisterRequest, LoginRequest, AuthResponse
```

## Entities

- **`User`** extends `BaseEntity`: `email` (unique, not null), `passwordHash` (not null), `role : Role` (`@Enumerated(STRING)`). Table: `users`.
- **`Role`**: `USER`, `ADMIN`. Add roles by adding enum constants — don't introduce a separate `roles` table unless multi-role support becomes a real requirement.

## DTOs (all records)

- **`RegisterRequest(String email, String password)`** — validate `@Email`, `@NotBlank`, `@Size(min=8)` when filling in.
- **`LoginRequest(String email, String password)`**
- **`AuthResponse(String token, String tokenType)`** with `static AuthResponse bearer(String token)`.

## Service contract

`AuthService`:

- `register(RegisterRequest)` → hash password with `PasswordEncoder`, save `User`, issue JWT via `JwtService.generateToken(user.getId().toString())`, return `AuthResponse.bearer(...)`. Reject duplicate emails with a 409-mapped exception (add it + handler entry when implementing).
- `login(LoginRequest)` → fetch by email, verify with `PasswordEncoder.matches`, issue JWT. Failure → 401 (add a mapped exception).

Token subject is the user UUID, serialized as a string. `CurrentUser` parses it back.

## Endpoints

- `POST /auth/register` — public, returns `AuthResponse`.
- `POST /auth/login` — public, returns `AuthResponse`.

Both paths are in `SecurityConfig`'s `permitAll` allowlist.

## What does **not** belong here

- Token validation logic — that lives in `infrastructure.security.JwtAuthFilter`.
- Authorization rules for other modules — those modules define their own access checks (using `CurrentUser`).
- Any reference to `Workflow`, notifications, etc.
