# Issue #83: Notification Detail — pruebas y evidencia

## Resultado verificado el 14 de octubre de 2026

- Backend: 370 pruebas, 0 fallos y 0 errores; Checkstyle y ArchUnit aprobados.
- Frontend: 11 pruebas aprobadas y compilación de producción correcta.
- Integración PostgreSQL: intentada, bloqueada porque Docker Desktop no inicia;
  Testcontainers informa `Could not find a valid Docker environment`.
- Recorrido manual con servicios y comparación visual: pendiente. No se presentan
  capturas sintéticas como evidencia de una ejecución integrada.

## Alcance y contrato

La ruta `/notifications/:id` consulta `GET /api/v1/notifications/{id}/status`.
La respuesta conserva sus campos anteriores y agrega `priority`, `correlationId` y
`providerAttempts`. En este proyecto el UUID de la notificación ya es la clave de
correlación entre outbox, Kafka y auditoría: `correlationId` expone ese mismo UUID;
no representa un trace ID HTTP ni un nuevo identificador suministrado por el cliente.

Cada intento contiene `providerName`, `role` (`PRIMARY` o `FALLBACK`), `success`,
`code`, `errorDetail`, `latencyMillis` y `timestamp` (inicio de la invocación).
El número visible corresponde a su posición en el historial persistido, incluidos
reintentos manuales posteriores. `CIRCUIT_OPEN` identifica una llamada bloqueada
sin petición HTTP; `NOT_CONFIGURED` identifica un fallback sin configurar. Sus
códigos no se presentan como códigos HTTP. Una duración de 0 ms es válida.

La migración Flyway V5 crea `delivery_attempts`. Los intentos se guardan en la misma
transacción que el estado terminal y los logs de fallo. No se mantiene una
transacción de BD durante las llamadas HTTP. Las notificaciones antiguas conservan
su timeline pero no obtienen intentos retrospectivos inventados. Los siblings de
una agregación conservan el comportamiento existente: el proveedor se invoca para
el líder, cuyos intentos se registran; no se inventan llamadas para cada sibling.

El mockup `design/detail-claude-preview.png` no está en las referencias locales
disponibles. Se usan los tokens, tarjetas y temas del frontend. La equivalencia
visual exacta con ese mockup queda pendiente de disponer de la referencia.

## 1. Pruebas automatizadas

Desde la raíz, con JDK 21 (no Java 8):

```powershell
$env:JAVA_HOME = "$env:USERPROFILE/.jdks/temurin-21.0.12.1"
.\mvnw.cmd -pl aegis-notification-service -am test
```

Si tu JDK está en otra ubicación, ajustá `JAVA_HOME`. Este comando ejecuta
Checkstyle, pruebas de aplicación, controladores, proveedores y reglas ArchUnit.
Los resultados quedan en `aegis-notification-service/target/surefire-reports`.

Con Docker Desktop abierto y funcionando, ejecutá la prueba de persistencia:

```powershell
.\mvnw.cmd -pl aegis-notification-service -am verify "-Dit.test=DeliveryAttemptRepositoryAdapterIT" "-Dfailsafe.failIfNoSpecifiedTests=false"
```

Testcontainers inicia PostgreSQL y aplica las migraciones reales. La prueba valida
orden de inserción, aislamiento por notificación y conservación de intentos de una
segunda ejecución. Resultados: `aegis-notification-service/target/failsafe-reports`.
Si Docker no funciona, esta prueba no puede considerarse aprobada.

Frontend, en otra terminal:

```powershell
cd aegis-admin-frontend
pnpm install --frozen-lockfile
pnpm run test:ci
pnpm run build
```

Las pruebas cubren consulta por ID, orden cronológico de todos los logs, estilo de
fallo/fallback, breadcrumb, 404, error y reintento, cambio de ID con cancelación de
la petición anterior, datos estructurados de intentos y estado vacío para registros
antiguos. El build verifica los templates Angular y presupuestos de producción.

## 2. Iniciar el entorno local

Todos los comandos siguientes parten de la raíz salvo que se indique otra carpeta.
Primero abrí Docker Desktop y esperá a que el motor esté disponible:

```powershell
docker version
docker compose -f docker-compose.full.yml up -d keycloak postgres mongo kafka redis
docker compose -f docker-compose.full.yml ps
```

Ejecutá cada aplicación Java en su propia terminal y dejala abierta. Configurá
`JAVA_HOME` como arriba en cada terminal si tu Java predeterminado es Java 8.

Terminal A:

```powershell
.\mvnw.cmd -pl aegis-eureka-server spring-boot:run
```

Terminal B:

```powershell
.\mvnw.cmd -pl aegis-api-gateway spring-boot:run
```

Terminal C (servicio de notificaciones, inicialmente solo lectura de los fixtures):

```powershell
$env:SENDGRID_API_KEY = 'local-test-only'
$env:TWILIO_ACCOUNT_SID = 'local-test-only'
$env:TWILIO_AUTH_TOKEN = 'local-test-only'
$env:FCM_PROJECT_ID = 'local-test-only'
$env:FCM_ACCESS_TOKEN = 'local-test-only'
.\mvnw.cmd -pl aegis-notification-service spring-boot:run
```

Esperá el mensaje de arranque en 8082 y que Flyway aplique V5. Los nombres de
variables de FCM son los definidos en `application.yml`; verificá tus overrides
locales si usás otro perfil. Eureka debe registrar `aegis-notification-service`;
el gateway lo descubre por nombre, por lo que puede tardar unos segundos.

Terminal D:

```powershell
cd aegis-admin-frontend
pnpm start
```

Abrí `http://localhost:4200`. En el realm local importado, el usuario de desarrollo
es `aegis-dev` y su contraseña es `dev123`. No cambies ni desactives la autenticación
para capturar evidencia.

## 3. Datos reproducibles para capturas de la pantalla

Con la migración V5 aplicada, ejecutá desde la raíz:

```powershell
Get-Content -Raw docs/testing/notification-detail-fixtures.sql | docker exec -i aegis-postgres psql -v ON_ERROR_STOP=1 -U aegis -d aegisnotify
```

Si configuraste otro usuario/BD en `.env`, ajustá `-U` y `-d`. El script es
repetible y no modifica notificaciones ajenas a los UUID de demostración. Crea
datos sintéticos: etiquetá las capturas como **fixtures de UI**, no como entregas
reales ni como prueba de publicación en Kafka.

| Escenario | URL después de `http://localhost:4200` | Resultado esperado |
| --- | --- | --- |
| Primario exitoso | `/notifications/83000000-0000-4000-8000-000000000001` | Badge SENT, un PRIMARY exitoso con 202 y 25 ms |
| Fallback | `/notifications/83000000-0000-4000-8000-000000000002` | Dos PRIMARY 503, FALLBACK 202, tres filas en ese orden |
| Fallo crítico | `/notifications/83000000-0000-4000-8000-000000000003` | PRIMARY 401 y FALLBACK 503 con motivos; aviso de fallo crítico y evento DLQ sintético |
| Pendiente | `/notifications/83000000-0000-4000-8000-000000000004` | PENDING, un evento, intentos vacíos |
| No encontrado | `/notifications/83000000-0000-4000-8000-000000000099` | Mensaje Notification not found y enlace de regreso |

En cada escenario verificá destinatario, EMAIL, HIGH, template, correlation ID y
fechas. Contrastá las filas con el JSON de la solicitud `/status` en DevTools >
Network > Fetch/XHR. Todos los eventos deben aparecer por fecha, incluso los
fallos entre PROCESSING y el estado terminal. Las fechas se muestran en la zona
horaria local del navegador; el atributo `datetime` conserva el instante ISO.

También comprobá:

1. Breadcrumb Notifications: navega a `/notifications`. La lista sigue siendo el
   placeholder existente; construirla no forma parte del issue #83.
2. Tema claro y oscuro con el control del layout.
3. Ancho móvil de 390 px en DevTools: tarjetas apiladas, IDs ajustados y tabla con
   desplazamiento horizontal dentro de su panel.
4. En DevTools > Network activá Offline y recargá con la sesión ya iniciada. Si
   Keycloak impide llegar a la pantalla, usá Request blocking sobre `*/status` en
   vez de Offline. Debe aparecer el error; quitá el bloqueo y presioná Try again.
5. Para ver Loading, usá throttling Slow 3G y recargá.

## 4. Entrega real de la aplicación contra proveedores locales simulados

Esta prueba sí recorre creación → outbox → Kafka → proveedores → persistencia →
consulta. Los proveedores son servidores HTTP locales, no cuentas SendGrid reales.
No uses credenciales reales. Primero cargá el SQL anterior para disponer del template.

Terminal E, desde la raíz, con Python 3:

```powershell
python docs/testing/notification-provider-stub.py --primary 503 --fallback 202
```

Detené solo el servicio de notificaciones con Ctrl+C, conservá las variables de
credenciales ficticias de la terminal C y configurá:

```powershell
$env:SENDGRID_BASE_URL = 'http://localhost:8091'
$env:SENDGRID_SECONDARY_BASE_URL = 'http://localhost:8092'
$env:SENDGRID_SECONDARY_API_KEY = 'local-test-only'
$env:NOTIFICATION_KAFKA_RELAY_ENABLED = 'true'
$env:NOTIFICATION_KAFKA_CONSUMER_ENABLED = 'true'
$env:NOTIFICATION_KAFKA_TOPIC_REPLICATION_FACTOR = '1'
$env:NOTIFICATION_KAFKA_TOPIC_MIN_IN_SYNC_REPLICAS = '1'
.\mvnw.cmd -pl aegis-notification-service spring-boot:run
```

En otra terminal obtené un token y creá una notificación. No captures el token:

```powershell
$login = Invoke-RestMethod -Method Post -Uri 'http://localhost:8088/realms/aegis/protocol/openid-connect/token' -Body @{ client_id = 'aegis-dev-cli'; username = 'aegis-dev'; password = 'dev123'; grant_type = 'password'; scope = 'notification:read notification:write' }
$headers = @{ Authorization = "Bearer $($login.access_token)" }
$body = @{ channel = 'EMAIL'; recipient = 'evidence@example.com'; templateName = 'detail-evidence'; priority = 'HIGH'; parameters = @{ name = 'Evidence' } } | ConvertTo-Json
$created = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/notifications' -Headers $headers -ContentType 'application/json' -Body $body
$created
```

Si el client ID de tu realm fue personalizado, usá el `clientId` del cliente con
password grant en `docker/keycloak/aegis-realm.json`. Esperá el procesamiento y consultá:

```powershell
$detail = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/notifications/$($created.id)/status" -Headers $headers
$detail | ConvertTo-Json -Depth 10
```

Abrí `http://localhost:4200/notifications/ID_DEVUELTO`. Debe mostrar fallback exitoso,
los reintentos efectivamente realizados, sus 503 y el 202 del fallback. El circuito
puede abrirse antes de consumir todos los reintentos: en ese caso debe aparecer
`CIRCUIT_OPEN`, no una llamada HTTP inventada.

Repetí con `--primary 202 --fallback 202` para SENT y con
`--primary 401 --fallback 503` para FAILED_CRITICAL. Detené el stub con Ctrl+C antes
de reiniciarlo. Reiniciá el servicio de notificaciones entre escenarios para
reiniciar el estado del circuito y creá una notificación nueva en cada caso.

Para verificar publicación real en DLQ, ejecutá y buscá el UUID creado:

```powershell
docker exec aegis-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic notifications-dlq --from-beginning --timeout-ms 10000
```

Contrastá el UUID con el mensaje de Kafka en el tópico `notifications-dlq`;
el badge FAILED_CRITICAL por sí solo no prueba que el broker haya recibido el mensaje.

## 5. Evidencia para el PR

- Salida de backend con BUILD SUCCESS y total de pruebas.
- Salida de frontend con tests aprobados y build exitoso.
- Resultado de Testcontainers con Docker operativo; si no se ejecutó, indicarlo.
- Capturas de SENT, fallback, fallo crítico, pendiente, 404, oscuro y móvil.
- JSON de `/status` y captura de la tabla para el mismo UUID; ocultá tokens y cookies.
- Para una prueba de entrega completa, terminal del stub mostrando las solicitudes,
  UUID creado, respuesta terminal y pantalla correspondiente. Diferenciá esta evidencia
  de los fixtures SQL.

Texto sugerido para el PR (completar resultados reales):

> Implements `/notifications/:id` with notification metadata, chronological audit
> timeline, persisted primary/retry/fallback attempts, and loading/error/404 states.
> Extends the status response with priority, correlation ID and attempt telemetry;
> adds Flyway V5. Existing notifications retain their audit trail without fabricated
> historical attempts. Correlation uses the existing notification UUID.
>
> Validation: [commands and actual results]. Manual evidence: [screenshots].
> Exact comparison with `detail-claude-preview.png`: pending reference availability.
>
> Closes #83

No abrir el PR hasta que el issue tenga `status:approved`, según su requisito.
