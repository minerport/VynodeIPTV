# API v1 — implemented foundation contract

All routes below are under `/api/v1`, except `/healthz` and `/media`. JSON request/response bodies. Authentication is `Authorization: Bearer <token>`. Administrator tokens are bootstrapped to the config file; device tokens come only from approved pairing. Do not put tokens in query strings.

| Method / path | Role | Contract |
|---|---|---|
| GET `/healthz` | Public | `{status,version}`; process health only |
| GET `/capabilities` | Device/admin | API version, profiles and development limitations |
| POST `/providers` | Admin | `{name,url,username,password,format,maxConnections,allowHttp}` → `{id,channels}`; validates before encrypted save |
| GET `/providers` | Admin | Redacted provider summaries; no secrets or URLs |
| GET `/channels` | Device/admin | Array of `{id,providerId,streamId,name,category,epgId}` |
| POST `/guide/refresh` | Admin | Per-provider result strings; failed import retains previous data |
| GET `/guide?channelId=...` | Device/admin | `{status,programmes:[{channelId,title,start,end}]}`; UTC times, bounded 5000 entries |
| POST `/pairing/requests` | Public | `{name}` → `{id,code,deviceSecret,expires}`; 5-minute expiry, creation rate limit |
| GET `/pairing/requests` | Admin | Pending request IDs, names, codes and expiry; never device secrets |
| POST `/pairing/approve` | Admin | `{id,code}` → `{approved:true}` |
| POST `/pairing/poll` | Public + device secret | `{id,deviceSecret}` → 202 pending or 200 `{state:"approved",token}` |
| GET `/devices` | Admin | Paired device IDs/names |
| DELETE `/devices/{id}` | Admin | Revoke access and stop owned sessions |
| POST `/playback/sessions` | Device/admin | `{channelId}` → session; 409 if provider/server limit reached |
| GET `/playback/sessions` | Device/admin | Own sessions, or all for administrator |
| GET `/playback/sessions/{id}` | Owner/admin | Session state without renewing lease |
| POST `/playback/sessions/{id}/heartbeat` | Owner/admin | Renews 90-second lease and returns state |
| DELETE `/playback/sessions/{id}` | Owner/admin | Cancels worker and releases slot after worker exit |
| POST `/playback/sessions/{id}/interrupt` | Admin, demo only | Fault-injection hook; unavailable for real providers |
| GET `/media/{id}/{generation}/{name}` | Owner/admin | HLS/TS; same authentication required on every resource |

Session: `{id,channelId,state,reason,generation,attempts,mediaUrl}`. Possible states: `starting`, `playing`, `recovering`, `failed`, `stopped`. `mediaUrl` is a server-relative authenticated route. Wait for `playing` before loading a new generation. Client errors never open another upstream session automatically. Keep heartbeats running while actively viewing and DELETE on stop. Unrenewed sessions expire and are cleaned up.

Errors: `{error:"redacted message"}` with 400 validation, 401 missing/invalid token, 403 role restriction, 404 missing/not-owned resource, 409 capacity/in-progress conflict, 422 provider validation failure or 429 pairing throttling. HTTP 200 from guide refresh can contain individual provider failures; inspect its result map.

## Guide preferences (0.3)

`GET /api/v1/guide/preferences` returns `{favorites: string[], groupOrder: string[], hiddenGroups: string[]}` for the authenticated administrator or paired device. `PUT` accepts any subset of those fields, leaving omitted fields intact. Lists are deduplicated, limited to 500 entries and persisted in encrypted state. The reserved `favorites` group cannot be included in `groupOrder` or `hiddenGroups`.

Group IDs use `all`, `favorites`, `custom:<group-id>`, or `provider:<provider-id>:<category-id>`. Favorites contains channel IDs. Clients pin Favorites, sort unranked groups by display name, and filter hidden groups only out of normal navigation; the group manager continues showing them for restoration.
