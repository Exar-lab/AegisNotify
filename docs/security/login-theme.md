# AegisNotify Login Theme

The custom Keycloak login theme (`aegis`) that brands the sign-in page of the `aegis` realm
(issue #199). For realm, client and scope setup see [`keycloak.md`](keycloak.md).

## Location and layout

```text
docker/keycloak/themes/aegis/
└── login/
    ├── theme.properties          # parent theme + stylesheet registration
    ├── template.ftl              # page layout (header, brand, card, footer)
    └── resources/
        └── css/login.css         # colours, fonts, component styling
```

`theme.properties`:

```properties
parent=keycloak.v2
import=common/keycloak
styles=css/login.css
```

The theme extends Keycloak's `keycloak.v2` login theme. Anything not overridden (forms, messages,
other `.ftl` pages, i18n bundles) comes from the parent. Only `template.ftl` and `login.css` are
customized.

## Deployment

The realm selects the theme with `"loginTheme": "aegis"` in
[`aegis-realm.json`](../../docker/keycloak/aegis-realm.json). Docker Compose mounts the folder
read-only into Keycloak's themes directory:

```yaml
volumes:
  - ./docker/keycloak/themes/aegis:/opt/keycloak/themes/aegis:ro
```

There is no build step: the theme is plain FreeMarker + CSS, loaded straight from the mount.
`docker compose up -d keycloak` is enough, in both `docker-compose.yml` and
`docker-compose.full.yml` (which includes the former).

For a Keycloak you run some other way, copy `docker/keycloak/themes/aegis` to
`<keycloak>/themes/aegis` (or package it as a JAR under `providers/` with a `META-INF/keycloak-themes.json`),
then in the admin console: *Realm settings → Themes → Login theme → `aegis`*.

## Customizing

Edit files on the host and reload the login page. With `start-dev`, Keycloak disables theme
caching, so changes appear on refresh; if a change does not show up, restart the container
(`docker compose restart keycloak`). In a production-mode Keycloak, caching is on and a restart
is required.

### Colours and fonts

`login.css` defines the palette as CSS variables on `:root`:

| Variable | Default | Use |
|---|---|---|
| `--aegis-bg` | `#0e0f12` | Page background |
| `--aegis-surface` | `#15161a` | Login card |
| `--aegis-border` | `#26282e` | Card and input borders |
| `--aegis-text` | `#f2f2f0` | Primary text |
| `--aegis-text-secondary` | `#9a9ea5` | Labels, links, hints |
| `--aegis-accent` | `#f4094c` | Buttons, brand icon, focus |
| `--aegis-accent-hover` | `#ff4d80` | Hover state |

Change these values to re-colour the whole page. Fonts are Inter (body) and Outfit (headings),
loaded with an `@import` from Google Fonts at the top of `login.css`. For air-gapped
environments, replace that import with locally served font files placed under `resources/` and
referenced via `@font-face`.

Most rules use `!important` to win over the parent PatternFly styles, so a new override usually
needs it too.

### Brand name and logo

In `template.ftl`, the header block (`#kc-header-wrapper`) holds the logo and name:

- The logo is an inline SVG inside `<span class="aegis-brand-icon">`. Replace the SVG, or
  swap it for `<img src="${url.resourcesPath}/img/logo.svg" alt="">` and add the file under
  `resources/img/`.
- The product name is the literal `<span>AegisNotify</span>` right after it.
- The page `<title>` comes from the realm display name (`${msg("loginTitle", realm.displayName)}`);
  set it in *Realm settings → General*.

The template references `${url.resourcesPath}/img/favicon.ico`. The theme does not ship that file,
so Keycloak resolves it through the parent theme. Add `resources/img/favicon.ico` to give the
login page its own favicon.

### Adding assets

Anything under `login/resources/` is served at `${url.resourcesPath}/`. To load an extra
stylesheet, list it in `theme.properties` (space-separated):

```properties
styles=css/login.css css/extra.css
```

### Template notes

`template.ftl` is a full copy of the parent's page template with the Aegis header and styles
applied, written against Keycloak 26.0. When upgrading Keycloak, diff it against the parent
`keycloak.v2` `template.ftl` of the new version and re-apply the Aegis changes, because Keycloak
may change macros or markup that the copy depends on.

## Verifying

1. `docker compose up -d keycloak`
2. Open `http://localhost:8088/realms/aegis/account` (redirects to the login page).
3. Confirm the dark background, the AegisNotify brand and the accent-coloured button.

If the page looks like stock Keycloak, check that the realm's *Login theme* is `aegis` and that
the volume mount path exists inside the container.

## Known gaps

- `docs/security/screenshots/` lists capture placeholders that have not been added, so this page
  has no screenshot yet.
- Only the *login* theme is customized. Account, admin and email themes use Keycloak defaults.
