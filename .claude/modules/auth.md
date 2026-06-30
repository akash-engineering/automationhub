# Module: `auth`

Owns users, registration, login, JWT issuance. Only module that knows the `User` entity.

## Key classes

- `AuthController` — `/auth/register`, `/auth/login`, `/auth/me`.
- `AuthService` — register / login / me; BCrypt + `JwtService`.
- `User`, `Role` (`USER`, `ADMIN`).
- `UserRepository` — `findByEmail`.

## DTOs (records)

- `RegisterRequest` (`@Email`, `@Size(min=8)` password).
- `LoginRequest`.
- `AuthResponse` — `static bearer(String token)`.
- `MeResponse` — `static from(User)`.

## Exceptions

- `EmailAlreadyExistsException` → 409.
- `InvalidCredentialsException` → 401, generic `"Invalid credentials"` (doesn't leak which field was wrong).

## JWT shape

Subject = user UUID; claims = `email`, `role`. Signed with `automationhub.jwt.secret` (≥32 bytes UTF-8). See `infrastructure.security.JwtService`.

## Endpoints

`POST /auth/register` + `POST /auth/login` are public via `SecurityConfig`'s `permitAll`. `GET /auth/me` requires bearer.
