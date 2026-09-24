# DM8 (达梦) demo data

Two seed scripts target a running DM8 instance:

- `seed_finance.sql` — the **Finance domain** of the demo dataset (`FINANCE` schema:
  `cost_center`, `account_subject`, `budget`, `reimbursement`, `invoice`, ~9,400 rows).
  It is a deterministic full rebuild (`DROP SCHEMA ... CASCADE` + recreate), so it is safe
  to re-run.
- `seed_dm8.sql` — the minimal `DEMO.EMP` table used by `data-fabric/e2e/smoke_test.py`
  for the cross-source JOIN check.

Apply `seed_finance.sql`:

```bash
docker exec -i -e LANG=zh_CN.UTF-8 data-fabric-dm8 \
  /opt/dmdbms/bin/disql SYSDBA/SYSDBA_dm001@127.0.0.1:5236 < data-fabric/datasets/dm8/seed_finance.sql
```

> `LANG=zh_CN.UTF-8` is required. DIsql mis-tokenizes a statement containing more than one
> multi-byte (Chinese) string literal unless it can tell the client is UTF-8; without it the
> insert statements fail with `[-2007]:Syntax error`.

Apply `seed_dm8.sql` (once; `DEMO.EMP` persists in the `dm8-data` volume):

```bash
docker exec -i -e LANG=zh_CN.UTF-8 data-fabric-dm8 \
  /opt/dmdbms/bin/disql SYSDBA/SYSDBA_dm001@127.0.0.1:5236 < data-fabric/datasets/dm8/seed_dm8.sql
```

Both scripts only need the instance to be up; see `../README.md` for the full three-domain
loader (`seed_all.sh`).
