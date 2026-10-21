# Coworker preview (free, on this computer)

For internal testing by coworkers before any hosting exists. It costs nothing: the app runs in Docker on the owner's
computer, and a **Cloudflare Quick Tunnel** (no account, no domain, no payment) gives it a temporary
`https://….trycloudflare.com` address that only the listed email addresses can open. Not for families or customers.

## What is reachable

- **Through the tunnel:** only the web entry point (Caddy, serving the built app with the headers from
  `frontend/vercel.json` and passing `/api/*` to the backend). Bookeyond's own sign-in and role permissions apply as
  usual.
- **Before the app:** Cloudflare Access asks for an email address and sends a one-time code. Only addresses given
  with `--allowed-mail` get in. Cloudflare says "a code has been emailed" for any address, so the list can't be
  probed, but an unlisted address never receives a working code. Requests without a passed check never reach the
  computer.
- **Not reachable from outside:** the backend, PostgreSQL and Mailpit have no public route. Caddy (port 5190) and
  Mailpit (port 8036) listen on 127.0.0.1 only. Actuator and API docs answer 401.
- The computer must stay on and awake. Everything stops when the tunnel or Docker stops. Each tunnel start gives a
  new address.

## The data

A separate, throwaway stack (Docker project `bk-rr`, production settings, its own database). It is not the owner's
`bookeyond-kid` stack and holds no real family's data. `e2e/preview-seed.spec.ts` makes one account per role
through the product's own flows, and writes the addresses and one-off passwords to a file outside the repository:

- an administrator;
- a principal whose school is paid (a test invoice confirmed against a made-up receipt), with class Grade 1 A, a
  teacher, a curriculum and a class code;
- a teacher;
- a principal whose school is unpaid (the payment panel, school services waiting);
- a parent with two children: Sophea learns Maths at home and has answered an activity, Rithy is in Grade 1 A.
  The child's space is opened from the parent's account.

All addresses end in `@bk-preview.test`. Emails the app sends (confirmation, password reset, invitations) land in
Mailpit at http://127.0.0.1:8036, on the owner's computer only. Payments are test invoices; no money moves, and the
production profile refuses the payment simulator.

## Running it (on the owner's computer)

The scripts live outside the repository in `D:\bk-work\preview\` (`preview.sh`, with `cloudflared.exe` 2026.10.1 in
`bin\`, checksum and Cloudflare signature verified). From Git Bash:

```bash
bash /d/bk-work/preview/preview.sh status
```

| Command | What it does |
|---|---|
| `preview.sh start` | starts the stack again after a stop (same data); no tunnel |
| `preview.sh tunnel a@x.com b@y.com '*@company.com'` | starts the tunnel for those addresses only (it refuses to start without any), points the app's links at the new address, and prints it |
| `preview.sh stop` | ends the tunnel and stops the stack; data kept |
| `preview.sh reset` | **deletes** the preview data and starts a fresh stack |
| `preview.sh seed` | makes a new set of role accounts (after a reset) |

Changing who may enter means starting the tunnel again with the new list, which also gives a new address.

## Limits

Quick Tunnels are for testing: no uptime guarantee, at most 200 requests in flight, and no server-sent events (the
app doesn't use them). Coworkers on the same network share sign-in limits, which are per address.
