# YIMO Graphwar hosting and scaling plan

Status: proposed for the first tournament deployment

## Executive recommendation

Use **vertical scaling first**, with a discounted custom plan if Cloudzy
confirms the custom shape is PAYG-resizable:

1. Keep the current 1 vCPU / 1 GB server only for short bootstrap, restore,
   and smoke tests.
2. Move serious staging to **4 vCPU / 8 GB / 25 GB / 1 TB custom** at about
   **$29.58/month** using the discounted rates supplied by the organizer.
3. If Cloudzy cannot resize into that custom shape, use the fixed **4 vCPU /
   8 GB** plan at $34.42/month.
4. Add room nodes horizontally only after the 4/8 load test proves one host
   cannot carry the room pool or that room isolation is operationally needed.

The custom plan is now cheaper than the equivalent fixed tier, but vertical
scaling still wins architecturally: one control plane, one SQLite writer, one
firewall policy, one backup target, and fewer failure modes.

Cloudzy documents direct resizing for hourly PAYG VPS instances, including an
unchanged dedicated IP and immediate billing adjustment. Verify that the
custom shape appears in the resize selector before relying on that workflow.
[Cloudzy resize guide](https://cloudzy.com/kb/resize-cloud-vps/)

## Inputs and current measurements

Tournament target:

- 5,000 stored participants.
- 100 concurrent players.
- 20 active rooms.
- 10 players maximum per room.

Measured 1 vCPU / 1 GB staging state:

- 961 MiB visible RAM; roughly 396 MiB used and 564 MiB available during the
  final check.
- About 2.6 GB used on the 24 GB filesystem after cleanup.
- YIMO server files were about 5.8 MB plus a 106 MB Java 8 runtime.
- Global lobby and tournament services were active; the optional practice
  room was disabled.
- No 100-player/20-room capacity claim has been established yet.

This points to CPU and concurrent room workload as the likely bottlenecks,
not disk. The smallest storage options are already much larger than the
current application footprint; 25 GB is a reasonable event minimum with room
for logs, the SQLite database, and backups kept outside the live directory.

## Discounted custom-plan calculations

Using the supplied discounted rates:

```text
monthly = 2.60 × vCPU + 1.95 × GB RAM + 0.065 × GB disk + 1.95 × TB transfer
```

| Shape | Calculation | Approx. monthly | Use |
|---|---:|---:|---|
| 1 vCPU / 1 GB / 20 GB / 1 TB | 2.60 + 1.95 + 1.30 + 1.95 | $7.80 | Supplied custom example; worse than fixed 1/1 |
| 2 vCPU / 4 GB / 25 GB / 1 TB | 5.20 + 7.80 + 1.63 + 1.95 | $16.58 | Intermediate canary/control node |
| 4 vCPU / 8 GB / 25 GB / 1 TB | 10.40 + 15.60 + 1.63 + 1.95 | **$29.58** | Recommended vertical baseline |
| 4 vCPU / 12 GB / 25 GB / 1 TB | 10.40 + 23.40 + 1.63 + 1.95 | $37.38 | Memory-heavy fallback |
| 8 vCPU / 16 GB / 25 GB / 1 TB | 20.80 + 31.20 + 1.63 + 1.95 | $55.58 | Future single-node option if available |

The final Cloudzy checkout is authoritative; these are calculations from the
rates supplied in the panel. Confirm that custom plans have the same PAYG,
resize, IP, and snapshot behavior before purchase.

## Fixed-plan comparison

| Plan | Resources | Monthly | Role |
|---|---:|---:|---|
| Fixed $3.22 | 1 vCPU / 512 MB / 20 GB | $3.22 | Reject: too little memory |
| Fixed $4.52 | 1 vCPU / 1 GB / 25 GB | $4.52 | Smoke/snapshot only |
| Fixed $9.72 | 1 vCPU / 2 GB / 60 GB | $9.72 | Small canary; still one CPU |
| Fixed $18.82 | 2 vCPU / 4 GB / 120 GB | $18.82 | Intermediate staging |
| Fixed $34.42 | 4 vCPU / 8 GB / 240 GB | $34.42 | Fallback to custom 4/8 |
| Fixed $45.47 | 4 vCPU / 12 GB / 300 GB | $45.47 | Memory fallback |
| Fixed $64.97 | 8 vCPU / 16 GB / 350 GB | $64.97 | Currently at capacity |

The custom 4/8/25/1 shape is approximately $4.84/month cheaper than fixed
4/8 and still supplies enough disk and bandwidth for the current workload.
The fixed tier is preferable only when custom resize/deployment is unavailable
or its checkout price differs materially.

## Horizontal comparison

Horizontal scaling buys isolation and independent room capacity, not a free
performance increase. The main comparison is:

| Layout | Total resources | Approx. monthly | Trade-off |
|---|---:|---:|---|
| 1 × custom 4/8/25/1 | 4 vCPU / 8 GB | $29.58 | Cheapest and simplest baseline |
| 2 × custom 2/4/25/1 | 4 vCPU / 8 GB | $33.15 | Same total resources, two failure domains and more operations |
| 1 × custom 4/8 + 1 × custom 2/4 | 6 vCPU / 12 GB | $46.16 | Control node plus one room node |
| 1 × custom 4/8 + 2 × custom 2/4 | 8 vCPU / 16 GB | $62.73 | Control node plus two room nodes |
| Fixed 4/8 + 2 × fixed 2/4 | 8 vCPU / 16 GB | $72.06 | Same topology, higher listed cost |

Start with one node. A two-node 2/4 split is not justified before load
testing because it costs more than custom 4/8 while leaving each node with
only two vCPUs. If horizontal isolation is required, use a 4/8 control node
and custom 2/4 room nodes.

## Phase 0 — Preserve and retire the smoke host

The consistent local server backup is:

`C:\Users\gesto\YIMO-server-backups\20260904-222221\yimo-server-state-consistent.tar.gz`

Keep it private: it contains the tournament database and runtime secrets. The
replacement workflow is documented in `deploy/cloudzy/cloud-init-recovery.yaml`
and `deploy/cloudzy/setup-yimo-vps.sh`.

If the 1 GB VPS is no longer needed, terminate it only after checking the
archive hash. Powering it off alone does not stop PAYG billing; Cloudzy states
that a powered-off VPS continues billing while it exists.
[Cloudzy regular VPS guide](https://cloudzy.com/kb/cloudzy-cloud-vps-regular-cpu/)

## Phase 1 — Vertical staging and load test

### Provisioning

1. Keep the VPS on the 1/1 tier only while validating the startup template.
2. Select custom 4/8/25/1 if the panel supports it for PAYG resize.
3. If custom resize is unavailable, select fixed 4/8.
4. Preserve the same IP when resizing; update the client endpoint only if a
   new VPS must be created instead of resized.
5. Keep room ports private to the room nodes/control firewall policy and use
   HTTPS before real participant credentials.

### Workload levels

Run the same build and configuration through these levels:

1. Lobby plus one practice room.
2. Five rooms with 25 concurrent players.
3. Ten rooms with 50 concurrent players.
4. Twenty rooms with 100 concurrent players.
5. A 20% headroom run with 120 session clients and normal chat, heartbeats,
   result submission, reconnects, and room churn.

### Pass criteria

- Tournament API p95 below 500 ms at the 100-player target.
- CPU below 75% sustained; report spikes separately.
- RAM below 75% sustained, with no swap growth.
- No room heartbeat loss, unauthorized join, duplicate result, or accepted
  protocol mismatch.
- Ten-player room limits remain enforced.
- A single room restart does not corrupt other matches.
- SQLite backup and restore complete without data loss.

If custom 4/8 passes, it is the first-event plan at approximately $29.58/month.
If only fixed 4/8 is available, use $34.42/month. The supplied fixed hourly
figure for 4/8 is $0.0512, so two hours is approximately $0.1024.

## Phase 2 — Scaling triggers

Stay vertical and choose custom 4/12 or fixed 4/12 when CPU is healthy but
memory is the constraint: RAM exceeds 75%, swap grows, or Java/Node processes
show memory pressure. Do not buy RAM to fix CPU saturation.

Move horizontally when:

- custom/fixed 4/8 remains CPU-bound at the target;
- room simulation must be isolated from lobby/API failure;
- maintenance must happen while matches continue; or
- the event grows beyond 20 rooms.

### Horizontal topology

- **Control node:** custom 4/8/25/1 or fixed 4/8. Runs Nginx/HTTPS, tournament
  API and SQLite, global lobby, monitoring, and backups.
- **Room node 1:** custom 2/4/25/1. Runs assigned tournament rooms.
- **Room node 2:** custom 2/4/25/1. Runs assigned tournament rooms.

Approximate total: $62.73/month custom. Start with one room node at about
$46.16/month total if load proves that one room node is enough.

Required code/operations before this topology is production-ready:

1. Add `nodeId`, advertised host, and room-port range to room registration.
2. Make allocation select a healthy node and reserve a unique room slot.
3. Include node identity in signed room tokens and result signatures.
4. Give each room node its own firewall port range; expose only HTTPS/API on
   the control node.
5. Keep SQLite on the one control node. Do not run multiple SQLite writers or
   put the file on a shared filesystem.
6. Add node drain, heartbeat-loss, room restart, reconnect, and result replay
   handling.
7. Test control-node failure separately from room-node failure.

## Phase 3 — Custom-plan guardrails

Choose custom only when:

- the exact shape is available for the required billing mode;
- the checkout price matches or beats the calculation;
- the plan can be resized/restored as needed; and
- the shape solves a measured CPU/RAM problem.

Custom is now a valid recommendation for 4/8/25/1 because it is cheaper than
fixed 4/8 and the measured disk footprint is small. It is not a reason to
overprovision storage or bandwidth: 25 GB and 1 TB already exceed the current
application footprint and likely event traffic.

## Tournament-week runbook

### T-30 to T-14 days

- Run the full Phase 1 workload and save the metrics.
- Freeze the client/server build and record commit and SHA-256 checksums.
- Choose custom vertical, fixed vertical, or horizontal topology from the
  thresholds above.
- Prepare a golden image only after removing runtime secrets and tournament
  data. Cloudzy snapshots capture the entire disk state and are billed by
  snapshot size. [Cloudzy snapshot guide](https://cloudzy.com/kb/how-to-take-a-snapshot/)

### T-7 days

- Restore/deploy the approved image and verify the current public IP.
- Use HTTPS and a domain before issuing real participant credentials.
- Run a two-client match, room restart, reconnect, result audit, and backup
  restore.
- Confirm the local server backup remains readable and private.

### Event day

- Start the selected PAYG tier before registration.
- Monitor CPU, RAM, swap, API p95, room heartbeats, join rejects, and result
  failures.
- Do not resize or add nodes during an active match unless the incident plan
  requires it; scale between rounds where possible.
- Keep an organizer-only path for forfeits and result corrections.

### After the event

- Export the final database and audit log locally.
- Drain rooms and verify the backup hash.
- Resize down or terminate deliberately. A powered-off PAYG server may still
  bill while it exists.

## Final recommendation

Choose **custom 4 vCPU / 8 GB / 25 GB / 1 TB** if the discounted PAYG price is
confirmed and the panel supports resize. Otherwise choose fixed 4 vCPU / 8 GB.
Use vertical scaling for the first serious event. Add custom 2/4 room nodes
only when load-test evidence or fault-isolation requirements justify the extra
operational complexity. Do not choose the 512 MB/1 GB tiers for production,
and do not use custom merely to buy less storage.
