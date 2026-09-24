# DM8 (达梦) container

Runs a Dameng DM8 instance for the data-fabric demo, on port `5236`, attached to the
`data-fabric-net` network so Trino can query it as a catalog.

The official DM8 image is not published, so the container is built from the official
Linux installer. The installer is **not** committed.

## Build prerequisites

1. Download the DM8 Linux installer for Ubuntu 22.04 x86_64 from the Dameng website
   (e.g. `dm8_2025xxxx_x86_Ubuntu22_64.iso`).
2. Mount/extract the ISO and place its `DMInstall.bin` next to this `Dockerfile`:

   ```
   data-fabric/deploy/dm8/DMInstall.bin
   ```

`DMInstall.bin` is git-ignored. The `install_dm.exp` Expect script performs a
non-interactive install (answers the language/key/confirm prompts; no `dm.key` is
required — DM8 ships a built-in trial license).

## Run

`docker compose` builds and starts it automatically (see `../docker-compose.yml`):

```bash
cd data-fabric/deploy
docker compose up -d dm8
```

The instance is initialized on first start (`dminit`, `CASE_SENSITIVE=0`, UTF-8) and
served by `dmserver`. Runtime data lives in the `dm8-data` volume.

Connection: `jdbc:dm://dm8:5236`, `SYSDBA` / `SYSDBA_dm001`.
