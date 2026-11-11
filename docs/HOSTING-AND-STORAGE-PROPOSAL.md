# Hosting regions and storage: findings and a pricing/quota proposal (2026-11-11)

> **A proposal for the owner, not a decision.** Prices are the providers' public list prices read on 2026-11-11
> (sources at the end); check them again before committing. Nothing here changes production - every action below is
> the owner's, in the providers' dashboards.

## 1. Where Bookeyond Kid runs today - what could be verified

| Part | Provider (from the repository) | Region - evidence | Status |
|---|---|---|---|
| Web app | Vercel (`frontend/vercel.json`) | Static files served from Vercel's edge; requests from Cambodia were served by the Singapore edge (`X-Vercel-Id: sin1::...`). A static site has no single region. | Fine as is |
| Backend, PostgreSQL, media volume | Railway (`docs/DEPLOY-VERCEL-RAILWAY.md`) | Requests enter Railway's Singapore edge (`x-railway-edge: sin1`), but the backend's first byte arrives ~300 ms after the connection to that edge (5 samples: 0.32-0.36 s first byte vs 0.04 s connect, on a health check that answers in milliseconds). That gap means **the backend and database are very likely not in Singapore** - probably in the US. | **Owner to confirm**: Railway -> service -> Settings -> Region (and the same for PostgreSQL) |
| Email | Brevo SMTP (`smtp-relay.brevo.com`, deployment record) | Not measurable from here; Brevo publishes its processing locations in its DPA | **Owner to confirm** |

Why it matters:
- **Speed**: every API call from Cambodia currently waits ~0.3 s more than it would with the backend in Singapore.
  A lesson start makes several calls in a row.
- **Privacy notice**: children's learning data is then held outside Cambodia; the notice needs cross-border wording
  (`docs/legal/DATA-INVENTORY.md` question 14).

**Recommendation (owner action, with downtime):** move the Railway backend, PostgreSQL and the volume to **Southeast
Asia (Singapore), `asia-southeast1-eqsg3a`**. Railway allows changing a service's region at any time; a service with a
volume (the backend's `/data`, and PostgreSQL) must migrate its volume, which takes time and causes downtime. Do it in
a quiet window, after a backup, and re-run the post-deploy checks in `docs/DEPLOY-VERCEL-RAILWAY.md`.

## 2. A launch blocker found: Vercel's free plan

Vercel's Hobby plan "restricts users to non-commercial, personal use only". Bookeyond Kid charges families and schools,
so the web app must be on **Vercel Pro ($20 per developer seat per month)** - or moved to another static host - before
launch. (Owner decision and spend.)

## 3. What storage actually holds

| Data | Where | Size |
|---|---|---|
| Accounts, children, learning records, drawings (stroke coordinates, at most ~60 KB each), attendance | PostgreSQL | Small: kilobytes per child per day |
| Pictures and recordings uploaded by educators (pictures <= 2 MB, recordings <= 5 MB) | Railway volume `/data` (hosted) or a school's own S3 (M11) | The only part that grows quickly |
| Erasure ledger | Railway volume `/data` | Tiny |

Children and parents upload nothing. Growth comes from teachers' and administrators' lesson media.

## 4. What hosted media costs (list prices, 2026-11-11)

| Option | Storage | Serving to children | Notes |
|---|---|---|---|
| **Railway volume** (today) | $0.15 per GB-month | $0.05 per GB egress | Simple; tied to one region; one backend instance only |
| Cloudflare R2 (S3-compatible) | $0.015 per GB-month (10 GB free) | Egress free; $0.36 per million reads | ~10x cheaper storage; would need the hosted default switched to the S3 provider (M11 built the provider - a small, separate change, owner's call) |
| Backblaze B2 (S3-compatible) | ~$0.007 per GB-month ($6.95/TB; 10 GB free) | Free up to 3x stored per month | Cheapest storage |
| Amazon S3 | Regional prices not readable from the public page; use the AWS calculator | | Only if a school brings it |

Worked example on Railway: a school of 20 teachers each using their whole 1 GB allowance holds 20 GB = **$3.00 a
month**; 300 children each loading ~40 MB of lesson media a month is 12 GB of egress = **$0.60**. Media for a school
costs a few dollars a month at most; RAM and CPU for the backend ($10/GB RAM, $20/vCPU per month) and the plans'
base fees dominate hosting costs, not storage.

## 5. Proposal: storage in plans (for the owner to decide)

1. **Families**: no storage quota - families upload nothing. Keep plans about children and journeys.
2. **Schools - included storage**: keep the per-teacher allowance in each school plan (`TEACHER_STORAGE_MB`, today 1 GB
   on the default). Suggested tiers: **1 GB per teacher** on a basic school plan, **2 GB per teacher** on a larger one.
   At Railway prices even the larger tier costs well under $0.50 per teacher per month.
3. **No overage charges** (as decided): when a teacher's allowance is full, new uploads wait - what is kept stays
   usable (already built). An administrator can raise one teacher's allowance with a recorded note (already built).
4. **A school that wants more**: connect its own S3-compatible storage (M11, built) - outside Bookeyond's allowance,
   paid by the school to its provider.
5. **Later, if media grows**: move Bookeyond-hosted media from the Railway volume to R2 or B2 (about 10-20x cheaper and
   not tied to the backend's region). Engineering change, not needed for launch.

## 6. What the owner decides

- Confirm the Railway (backend + PostgreSQL) region; approve moving it to Singapore and choose the window.
- Move the web app to Vercel Pro (or another host) before charging anyone.
- Storage tiers per school plan (the numbers above, or others); keep "no overage".
- Whether and when to move hosted media to R2/B2.
- Confirm Brevo's processing locations for the privacy notice.

Sources: [Railway pricing](https://docs.railway.com/reference/pricing/plans),
[Railway regions](https://docs.railway.com/reference/deployment-regions),
[Vercel Hobby plan](https://vercel.com/docs/plans/hobby), [Cloudflare R2 pricing](https://developers.cloudflare.com/r2/pricing/),
[Backblaze B2 pricing](https://www.backblaze.com/cloud-storage/pricing), [Amazon S3 pricing](https://aws.amazon.com/s3/pricing/).
