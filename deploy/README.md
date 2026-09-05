# Stage 7: snapshot-ready Cloudzy staging

Stage 7 is deliberately split into two capacities:

1. **Stage 7A — bootstrap and snapshot:** use the selected Cloudzy plan shown
   in the organizer panel: **1 vCPU, 1 GB RAM, 25 GB storage, 1 TB transfer**.
   This is enough to validate installation, the YIMO lobby, the tournament
   API, one practice room, backups, and the restore path. It is not a claim
   that this plan can host the final 100-player event.
2. **Stage 7B — tournament staging:** restore the golden snapshot onto a
   larger host or several hosts, then run the 100-player/20-room load test.
   The final size is chosen from measurements, not guessed from the
   bootstrap VM.

The screenshot price is **$0.0067/hour**, so one or two hours of staging is
about **$0.0067–$0.0134** before taxes or provider charges. The provider’s
current plan page and account panel can differ; the account panel is the
authority for the selected server.

## Scaling decision

“Vertical” means making one server larger (more vCPU/RAM). “Horizontal”
means adding multiple servers. For YIMO, the practical sequence is:

- use one 1 GB VM briefly to build and capture the golden image;
- restore it to a 4 vCPU/8 GB staging VM for the first meaningful load test;
- if room CPU is the bottleneck, add room nodes horizontally and keep the
  tournament control service on one API node;
- keep SQLite on one control-service node until the service is migrated to a
  database designed for multi-writer deployment.

Twenty tournament room processes and 100 concurrent players should not be
promised on the 1 GB bootstrap host. The tournament service has one SQLite
writer, and the Java room pool has a bounded 50-port range; both are explicit
capacity limits.

## What the automation does

- `cloud-init.yaml` installs the small base, checks out an explicitly pinned
  Git revision, and runs the bootstrap script.
- `bootstrap-vps.sh` installs Java 8, Node 24, Nginx, UFW, the system user,
  and systemd units. It opens only SSH, HTTP, the YIMO lobby port `23762`,
  and the documented room range `30000:30049`. It also creates a 512 MB
  swapfile and applies bounded Java/Node memory profiles for the 1 GB plan.
- `first-boot.sh` runs on every restored instance. It detects the current
  IPv4 address, writes a fresh `yimo.properties`, and creates runtime
  tournament secrets only when they are missing.
- `install-release.sh` installs a locally built release atomically while
  keeping the previous release directory as a rollback copy.
- `setup-yimo-vps.sh` is the replacement-VPS path: it checks out a pinned
  repository revision, runs the base bootstrap, downloads and verifies the
  pinned public release, strips Windows-only files, and starts the lobby and
  tournament services. It creates fresh runtime secrets through
  `first-boot.sh`; no secrets are stored in the script.
- `prepare-snapshot.sh` stops services, removes the database and generated
  secrets, clears the saved IP, and leaves the first-boot unit enabled. A
  provider snapshot restored to a new IP therefore configures itself on its
  first boot.

The scripts do not call the Cloudzy control plane. Provisioning, snapshot
creation, and resizing remain deliberate actions in the provider console.
This prevents an accidental VM or billing change while the design is being
tested.

## Local-only credentials

The repository root contains an ignored `.env.local` file for organizer
machine values:

```text
CLOUDZY_API_TOKEN=put-a-token-here-only-if-a-supported-Cloudzy-CLI-is-added
CLOUDZY_SERVER_ID=
CLOUDZY_PUBLIC_IP=
CLOUDZY_REGION=
YIMO_SSH_CIDR=
```

The current Stage 7 scripts do not consume `CLOUDZY_API_TOKEN`; there is no
Cloudzy API connector enabled in this workspace. Keep the token in that file
on the organizer computer, never in Git, a release archive, cloud-init,
source code, or chat. The server’s generated organizer token is kept only in
`/root/yimo-admin-token.txt` and is removed before a snapshot.

## Rebuild a replacement VPS

If the original VPS is terminated, create a fresh Ubuntu 24.04 VPS with the
organizer SSH key, copy the setup script to it, and run it as root. Set the
SSH CIDR before running; do not use a broad range unless the short-lived
bootstrap is behind another firewall:

```bash
scp deploy/cloudzy/setup-yimo-vps.sh root@NEW_SERVER_IP:/root/
ssh root@NEW_SERVER_IP \
  'YIMO_SSH_CIDR=YOUR_PUBLIC_IP/32 bash /root/setup-yimo-vps.sh'
```

The script defaults to the published `v2.0.0` release and verifies its
SHA-256 before installation. To use a later approved build, set
`YIMO_REPO_REF`, `YIMO_RELEASE_URL`, and `YIMO_RELEASE_SHA256` in the SSH
command. If `/etc/yimo/bootstrap.env` already exists, the script preserves it;
edit that file first when restoring to a different IP or changing the firewall
policy. It leaves practice rooms disabled unless
`YIMO_ENABLE_PRACTICE_ROOMS=1` is explicitly supplied.

## Build the release on Windows

Run from the repository root in PowerShell:

```powershell
.\deploy\build-stage7-release.ps1 `
  -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot' `
  -OutputDir 'C:\path\outside\the\repo\YIMO-Graphwar-2.0.0-stage7'
```

The script compiles all production Java sources with Java 8, creates the
client, lobby, and room JARs, copies resources and tournament sources, and
writes `SHA256SUMS.txt` plus a zip archive. It refuses to overwrite an
existing output directory.

## Create the 1 GB staging VM

1. In Cloudzy, choose Ubuntu 24.04 LTS, the 1 vCPU/1 GB plan, NVMe storage,
   a public IPv4 address, and SSH-key authentication.
2. Copy `cloud-init.yaml` and replace:
   - `YIMO_REPO_REF` with the approved commit or release tag;
   - `YIMO_SSH_CIDR` with the organizer’s public IP in CIDR form, such as
     `203.0.113.42/32`.
3. Submit the file as user-data while creating the VM. Do not put an API
   token, HMAC secret, participant code, or private key in it.
4. Wait for cloud-init to finish, then verify:

```bash
sudo systemctl status yimo-first-boot.service --no-pager
sudo systemctl status nginx.service --no-pager
sudo cat /etc/yimo/yimo.properties
```

The bootstrap template intentionally refuses to continue while its
`REPLACE_WITH_*` safety placeholders remain.

## Upload and install the release

From the organizer computer, upload the built release directory (not
`.env.local`):

```powershell
scp -r C:\path\outside\the\repo\YIMO-Graphwar-2.0.0-stage7 root@SERVER_IP:/root/yimo-release
ssh root@SERVER_IP 'bash /opt/yimo-source/deploy/cloudzy/install-release.sh --release-dir /root/yimo-release'
```

If `/opt/yimo-source` is not present, upload the `cloudzy` scripts too and
run the equivalent `install-release.sh` path from that checkout. Then check:

```bash
curl http://SERVER_IP/healthz
sudo systemctl status yimo-global.service yimo-tournament.service --no-pager
sudo journalctl -u yimo-tournament.service -n 80 --no-pager
```

Retrieve the generated organizer token over the protected SSH connection
only when configuring the local tournament admin:

```bash
ssh root@SERVER_IP 'cat /root/yimo-admin-token.txt'
```

Do not paste that token into GitHub or commit it. Because this staging setup
uses an IP and HTTP, do not use real participant credentials or expose the
admin page publicly. Add a domain and HTTPS before the event.

## Capture and restore the golden snapshot

After the health check and a local smoke match pass:

```bash
ssh root@SERVER_IP 'bash /usr/local/sbin/yimo-prepare-snapshot.sh'
```

Confirm that the command reports a safe stopped image. Create the provider
snapshot in the Cloudzy console. The snapshot checklist is:

- `/etc/yimo/tournament.env` is absent;
- `/root/yimo-admin-token.txt` is absent;
- `/var/lib/yimo/tournament.sqlite*` is absent;
- `/etc/yimo/yimo.properties` is absent;
- `/etc/yimo/bootstrap.env` has an empty `YIMO_PUBLIC_IP`;
- `yimo-first-boot.service` is enabled;
- no `.env.local`, SSH private key, or provider credential is on the VM.

When the snapshot is restored, boot networking first, then the enabled
first-boot unit detects the new IP, regenerates the tournament environment,
and starts the installed services. The provider’s cloud-init behavior can
vary for cloned snapshots; the systemd first-boot unit is the reliable
restore hook.

## Smoke-game mode on the 1 GB VM

The small VM can host a lobby, the tournament API, and one lightweight
practice room after the bounded memory profile is installed. It is still not
an event server. To enable the practice room for a short manual test:

```bash
sudo sed -i 's/^YIMO_ENABLE_PRACTICE_ROOMS=.*/YIMO_ENABLE_PRACTICE_ROOMS=1/' /etc/yimo/bootstrap.env
sudo systemctl enable --now yimo-public-rooms.service
sudo ss -ltn | grep -E ':(30000|30001|30002|30003|30004) '
```

Room sockets now bind inside `room.port.start`–`room.port.end`, so the
Cloudzy firewall can reach them. Stop the optional room when finished:

```bash
sudo systemctl disable --now yimo-public-rooms.service
```

Keep the room disabled while capturing the golden snapshot unless the
practice-room smoke test is part of the image validation.

## Stage 7 checkpoint

The checkpoint is complete when all of these pass on the 1 GB VM:

- `/healthz` returns the expected YIMO build and protocol;
- the YIMO lobby accepts a matching client and rejects an incompatible one;
- the tournament service can create a local test bracket and result;
- the optional practice room starts only when explicitly enabled;
- the snapshot contains no runtime database or secrets;
- a restored clone reconfigures itself to its new IP.

Do not treat this checkpoint as the final tournament load test. Stage 7B is
the next approval gate after the snapshot is proven.
