# Loom script — AutomationHub 2-min walkthrough

**Audience:** Upwork client — small-SaaS founder, ops manager, tech lead. Not engineers.
**Goal:** convince a buyer to send an invite. Not to teach architecture.
**Length:** 2:00 exactly (~290 words at conversational pace ≈ 145 wpm).
**Tone:** confident, friendly, no filler.

Read it through aloud once before recording. If you're over 2:00, cut from the architecture section first, demo last.

---

## Script

### [0:00 – 0:12] Hook

> *[SCREEN: GitHub repo homepage]*
>
> "Hi, I'm Akash. This is AutomationHub — a Java backend I built to show what I'll ship for your project. The pitch in one line: an external system — your payment processor, your CRM, anything that fires webhooks — hits one secure endpoint, and a workflow runs on the server."

---

### [0:12 – 0:32] Architecture, fast

> *[SCREEN: `docs/architecture/modules.md` — Mermaid module graph]*
>
> "Five modules — auth, workflow, notifications, document generation, infrastructure. They talk through events, so each piece swaps without touching the others. Stack: Spring Boot 3, Java 21, Postgres, JWT, Docker — what production Java projects actually use."

---

### [0:32 – 1:20] Live demo — the money shot

> *[SCREEN: terminal on the left, Swagger UI or browser on the right]*
>
> "Now the live system. I register a user, log in, and create a workflow with one HTTP action — that's where you'd hit your CRM, your billing API, anywhere."
>
> *[PASTE: register + login + create-workflow curls. Show 200/201.]*
>
> "The webhook is the important part. I turn it on — the server returns a one-time secret."
>
> *[PASTE: `POST /workflows/{id}/webhook` — show the secret + URL JSON.]*
>
> "I sign a payload with that secret and fire it from outside, like a real provider would."
>
> *[PASTE: signed curl. Show 202.]*
>
> "Workflow ran — here's the execution status."
>
> *[PASTE: GET execution — show `COMPLETED`.]*
>
> "What if the same webhook arrives twice — say the network retries? Watch: same Idempotency-Key, same execution returned. No double-charge, no duplicate work."
>
> *[PASTE: replay the signed curl. Same execution ID.]*
>
> "Tampered signature? Generic 401, no leak about which check failed."
>
> *[PASTE: curl with one byte changed. 401 Unauthorized.]*

---

### [1:20 – 1:50] Production signals

> *[SCREEN: scroll through `git log --oneline` on GitHub, then click into one of the test files briefly]*
>
> "This isn't a toy. Owner-scoped access on every endpoint, async execution off the request thread, concurrent-request safety verified by an integration test against a real Postgres, PDF generation, an S3-ready storage layer, OpenAPI docs — all in. The git history reads as coherent units of work, not a heap."

---

### [1:50 – 2:00] CTA

> *[SCREEN: README / your Upwork profile / your contact card]*
>
> **⚠ Customize before recording.** Replace the call-to-action with the specific channel you want a client to use: a direct Upwork invite URL, your profile URL, a Calendly link, or your email. Specific CTAs convert ~2× better than vague ones.
>
> Default text (swap in your real channel):
>
> "If you've got a Spring Boot, REST API, or webhook integration project — message me. This is the quality I bring to client work. Thanks for watching."

---

## Cheatsheet — pre-typed curls

Have these in a second terminal so you can `↑↑↑` through them during recording. **Do not type live** — the viewer's attention budget is small and every key you press costs them.

```bash
# 1. Register + login
EMAIL="demo-$(date +%s)@example.com"
curl -s -X POST localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"correcthorse\"}"

TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"correcthorse\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin)['token'])")

# 2. Create a workflow with an HTTP action (httpbin = visible "remote API")
WID=$(curl -s -X POST localhost:8080/workflows -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"demo","actions":[{"type":"HTTP","order":1,"config":"{\"url\":\"https://httpbin.org/post\",\"method\":\"POST\",\"body\":{\"hello\":\"world\"}}"}]}' \
  | python -c "import sys,json;print(json.load(sys.stdin)['id'])")

# 3. Enable the webhook — capture the one-time secret
SECRET=$(curl -s -X POST "localhost:8080/workflows/$WID/webhook" \
  -H "Authorization: Bearer $TOKEN" \
  | python -c "import sys,json;print(json.load(sys.stdin)['secret'])")

# 4. Sign a payload + fire it (the "external system" call)
TS=$(date +%s); BODY='{"event":"invoice.paid","customer":"acme"}'
SIG=$(printf "%s.%s" "$TS" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -r | cut -d' ' -f1)
curl -s -X POST "localhost:8080/webhooks/workflows/$WID" \
  -H "X-Webhook-Timestamp: $TS" -H "X-Webhook-Signature: sha256=$SIG" \
  -H "Idempotency-Key: demo-001" -H 'Content-Type: application/json' \
  -d "$BODY"

# 5. Confirm it ran
curl -s "localhost:8080/workflows/$WID/executions" -H "Authorization: Bearer $TOKEN" | python -m json.tool

# 6. Replay — same key, same execution returned (idempotency!)
TS2=$(date +%s); SIG2=$(printf "%s.%s" "$TS2" "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -r | cut -d' ' -f1)
curl -s -X POST "localhost:8080/webhooks/workflows/$WID" \
  -H "X-Webhook-Timestamp: $TS2" -H "X-Webhook-Signature: sha256=$SIG2" \
  -H "Idempotency-Key: demo-001" -H 'Content-Type: application/json' -d "$BODY"

# 7. Tamper one byte → 401
curl -s -w "HTTP=%{http_code}\n" -X POST "localhost:8080/webhooks/workflows/$WID" \
  -H "X-Webhook-Timestamp: $TS2" -H "X-Webhook-Signature: sha256=$SIG2" \
  -H 'Content-Type: application/json' -d '{"event":"invoice.paiD","customer":"acme"}'
```

---

## Recording checklist

- **Resolution:** Loom at 1080p. Full-screen browser. Terminal at ≥18pt font — viewers will be on phones.
- **Two-monitor setup:** cheatsheet on a hidden monitor; demo + Loom on the captured screen.
- **Take order:** record sections **out of order**. Do the live demo (0:32 – 1:20) first while you're warm; record intro + outro last. Most freezes happen on the intro.
- **One-retake rule:** if you fumble, restart that section — don't keep going. Loom's split-and-stitch is fine but the energy dies.
- **Trim ruthlessly:** target 1:50, aim to under-deliver on time. A 1:45 Loom that says "watch this" beats a 2:30 Loom that says "let me explain everything."
- **Thumbnail:** the Mermaid architecture diagram. Strongest visual hook before the click.
- **Pre-flight:** wipe the DB and confirm the docker stack is healthy before recording — `docker compose down -v && docker compose up -d --build && curl -fs localhost:8080/actuator/health`. The first request to a cold app is slow; do one throwaway curl off-camera.

---

## Things to record once, reuse forever

- **B-roll on the architecture diagram:** Loom-record yourself slowly scrolling the Mermaid module graph and the runtime event flow, no audio, 10 seconds each. Stitch it into the architecture section instead of clicking around live — it looks more polished.
- **Multiple CTA endings:** record three 10-second outro variants (Upwork-specific, Calendly-specific, generic email). You can swap the outro per audience without re-recording the whole demo.
