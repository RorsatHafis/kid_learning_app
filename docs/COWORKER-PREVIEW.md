# Coworker preview (free, on this computer)

For internal testing by coworkers before any hosting exists. It costs nothing: the app runs in Docker on the owner's
computer, and a **Cloudflare Quick Tunnel** (no account, no domain, no payment) gives it a temporary
`https://….trycloudflare.com` address. Not for families or customers.

## Two stages to get in

1. **The preview gate**: the browser asks for a username and password before anything of the site loads. They are
   the same for everyone and are sent to coworkers privately (never in a repository, a chat channel's public history,
   the URL, or this file).
2. **Bookeyond Kid's own sign-in**: each coworker uses the test account they were given (parent, teacher, principal or
   administrator). Bookeyond's roles, school payment rules and other permissions apply as usual.

## What the gate covers

The gate sits in the preview's web server (Caddy) in front of every page, file, API route and method. Requests
without it get `401` with `WWW-Authenticate: Basic`, and never reach the app.
- Once the browser passes the gate, Caddy also gives it a gate cookie (random, HttpOnly, Secure, SameSite=Lax,
  7 days). The app's own API calls carry `Authorization: Bearer`, which replaces the browser's Basic header, so the
  cookie is what lets them through.
- The Basic header is removed before a request reaches the backend.
- Behind the gate no response is publicly cacheable, so nothing can be served to someone who hasn't passed it.

The gate exists only in the preview's own Caddyfile (outside the repository). The rehearsal and production
configurations don't have it.

Not reachable from outside: the backend, PostgreSQL and Mailpit have no public route. Caddy (port 5190) and Mailpit
(port 8036) listen on 127.0.0.1 only.

Cloudflare's `--allowed-mail` email gate is **not** used: its session currently expires seconds after sign-in, so
the app's requests are refused
([cloudflare/cloudflared#1752](https://github.com/cloudflare/cloudflared/issues/1752)). Revisit it once a release
fixes that.

## The data

A separate, throwaway stack (Docker project `bk-rr`, production settings, its own database). It is not the owner's
`bookeyond-kid` stack and holds no real family's data. `e2e/preview-seed.spec.ts` makes one account per role through
the product's own flows:

- an administrator;
- a principal whose school is paid (a test invoice confirmed against a made-up receipt), with class Grade 1 A, a
  teacher, a curriculum and a class code;
- a teacher;
- a principal whose school is unpaid (the payment panel, school services waiting);
- a parent with two children: Sophea learns Maths at home and has answered an activity, Rithy is in Grade 1 A.

The child's space is opened from the parent's account. Addresses end in `@bk-preview.test`. Emails the app sends land
in Mailpit at http://127.0.0.1:8036, on the owner's computer only. Payments are test invoices; no money moves, and the
production profile refuses the payment simulator.

## Running it (on the owner's computer)

Everything lives outside the repository in `D:\bk-work\preview\`:
- `preview.sh`, the control script;
- `bin\cloudflared.exe` 2026.10.1 (checksum and Cloudflare signature verified);
- private files: `gateway.txt` (the gate's username and password), `gate.env` (its hash and the cookie secret) and
  `accounts.txt` (the test accounts).

From Git Bash:

```bash
bash /d/bk-work/preview/preview.sh status
```

| Command | What it does |
|---|---|
| `preview.sh tunnel` | Checks that the gate refuses every anonymous probe locally, starts the tunnel, checks again through the public URL (and stops the tunnel at once if anything gets through), then prints the URL. Each start gives a new URL. |
| `preview.sh rotate-gate` | Makes a new gate password and cookie and reloads the web server. Everyone must pass the gate again; the URL stays the same. Use it when someone should no longer have access. |
| `preview.sh stop` | Ends the tunnel and stops the stack. Data is kept. |
| `preview.sh start` | Starts the stack again after a stop (same data), without the tunnel. |
| `preview.sh reset` | **Deletes** the preview data, makes fresh role accounts and puts the gate back. |

Adding a coworker needs no new tunnel: send them the URL, the gate password and a test account privately.

## Limits

Quick Tunnels are for testing: no uptime guarantee, at most 200 requests in flight. The computer must stay on and
awake. Coworkers on the same network share sign-in limits, which are per address. One shared gate password means the
gate can't tell coworkers apart (Bookeyond's own accounts can); rotate it when someone leaves the test.
