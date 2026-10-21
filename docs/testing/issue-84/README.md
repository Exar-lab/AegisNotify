# Issue #84 — Guía de pruebas y evidencias

Ejecutado el 14 de octubre de 2026, zona America/Costa_Rica.

## 1. Ejecutar tú mismo los tests automatizados

Abre PowerShell 7 en la raíz del repositorio (la carpeta que contiene `mvnw.cmd`).
Abre Docker Desktop y espera a que indique que el motor está funcionando.
Los tests de PostgreSQL crean un contenedor aislado: no usan los registros de tu base local.

```powershell
$env:JAVA_HOME = 'C:/Users/luisg/.jdks/temurin-21.0.12.1'
$env:JAVA_TOOL_OPTIONS = '-Dapi.version=1.44'
& "$env:JAVA_HOME/bin/java.exe" -version
./mvnw.cmd -pl aegis-notification-service -am test '-Dtest=ListNotificationsServiceTest,NotificationControllerTest,NotificationRepositoryAdapterIT,ArchitectureTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Esperado: Java 21, **46 tests**, cero fallos/errores, `BUILD SUCCESS` y cero infracciones Checkstyle.
La propiedad `api.version` permite usar el Testcontainers actual con Docker Desktop 29.
No es necesario desactivar Checkstyle ni modificar dependencias.

Para ejecutar solo servicio, controlador y arquitectura sin PostgreSQL/Docker:

```powershell
./mvnw.cmd -pl aegis-notification-service -am test '-Dtest=ListNotificationsServiceTest,NotificationControllerTest,ArchitectureTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

Esperado: **38 tests**. Esto NO sustituye la prueba de integración de PostgreSQL.

Frontend, también desde la raíz:

```powershell
pnpm.cmd --dir aegis-admin-frontend test:ci
pnpm.cmd --dir aegis-admin-frontend build
```

Esperado: **4 archivos / 14 tests aprobados**, luego `Application bundle generation complete`.
Para trabajar con ejecución continua: `pnpm.cmd --dir aegis-admin-frontend test`.

## 2. Preparar la prueba manual

En esta sesión ya están disponibles el frontend, Keycloak, Eureka, Gateway y el servicio actualizado.
Abre <http://localhost:4200/notifications>. Usuario local: `aegis-dev`; contraseña local: `dev123`.

Si necesitas levantar todo desde cero:

1. En la raíz, inicia la infraestructura:

```powershell
docker compose -f docker-compose.yml -f docker-compose.full.yml up -d
```

2. En una terminal PowerShell dedicada, desde la raíz, inicia Eureka:

```powershell
$env:JAVA_HOME = 'C:/Users/luisg/.jdks/temurin-21.0.12.1'
./mvnw.cmd -pl aegis-eureka-server spring-boot:run
```

3. En otra terminal dedicada, inicia el servicio ACTUAL de notificaciones:

```powershell
$env:JAVA_HOME = 'C:/Users/luisg/.jdks/temurin-21.0.12.1'
$env:SENDGRID_API_KEY = 'local-test'
$env:TWILIO_ACCOUNT_SID = 'local-test'
$env:TWILIO_AUTH_TOKEN = 'local-test'
$env:FCM_PROJECT_ID = 'local-test'
$env:FCM_ACCESS_TOKEN = 'local-test'
$env:NOTIFICATION_KAFKA_CONSUMER_ENABLED = 'false'
./mvnw.cmd -pl aegis-notification-service spring-boot:run '-Dspring-boot.run.profiles=local'
```

Estos valores son de prueba, no permiten envíos reales. Para estas pruebas de lectura no hacen falta
credenciales reales de proveedores. Espera `Started NotificationServiceApplication` en el puerto 8082.
Si el puerto está ocupado, detén la ejecución anterior desde su terminal o IDE y vuelve a iniciarla.
No ejecutes dos versiones del mismo servicio simultáneamente.

4. En otra terminal, inicia Gateway:

```powershell
$env:JAVA_HOME = 'C:/Users/luisg/.jdks/temurin-21.0.12.1'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:4200'
./mvnw.cmd -pl aegis-api-gateway spring-boot:run
```

5. En otra terminal, inicia Angular:

```powershell
pnpm.cmd --dir aegis-admin-frontend start
```

6. Espera el registro del servicio en Eureka y abre <http://localhost:4200/notifications>.
No necesitas reiniciar nada que ya esté funcionando con el código actual.

### Datos reproducibles

Ya inserté **24 fixtures** con plantilla `issue84-manual-fixture` y destinatarios `issue84-00@example.com`
hasta `issue84-23@example.com`. Cubren 4 canales, 3 prioridades, 8 estados y los días 13 y 14 de octubre.
Son registros sintéticos de lectura: se insertaron directamente en PostgreSQL, sin outbox ni envíos.
Los destinatarios sintéticos de SMS/WhatsApp/Push no representan datos válidos para entrega.
El script es idempotente: repetirlo no duplica los IDs.

```powershell
Get-Content -Raw docs/testing/issue-84/seed.sql | docker exec -i aegis-postgres psql -U aegis -d aegisnotify -v ON_ERROR_STOP=1
```

Hay 7 registros previos: el total actual sin filtros es **31**. Si agregas otros registros, ese total cambiará.
Usa búsqueda `issue84-manual-fixture` para obtener siempre el conjunto de 24 fixtures.

## 3. Probar la interfaz paso a paso

Los filtros se aplican al pulsar **Apply filters** o Enter. **Clear** restablece filtros y tamaño a 20.
Las fechas de la interfaz usan la zona horaria del navegador. Los conteos de fechas siguientes
corresponden a America/Costa_Rica.

| Paso | Acción | Resultado esperado |
| --- | --- | --- |
| 1 | Entra a `/notifications` e inicia sesión. | Carga el listado, no un título vacío. Actualmente 31 resultados. |
| 2 | Busca `issue84-manual-fixture` y pulsa Apply filters. | 24 resultados, filas con los cuatro canales y distintos badges. |
| 3 | Selecciona 10 en Rows per page. | Showing 1–10 of 24; Page 1 of 3; Previous deshabilitado. |
| 4 | Pulsa Next. | Showing 11–20 of 24; Page 2 of 3; no se repiten IDs de la primera página. |
| 5 | Pulsa Next otra vez. | Showing 21–24 of 24; 4 filas; Next deshabilitado. |
| 6 | Pulsa Previous. | Regresa a la segunda página. |
| 7 | Con búsqueda `issue84`, selecciona EMAIL + MEDIUM + SENT VIA FALLBACK y pulsa Apply filters. | 1 resultado: `issue84-04@example.com`; proveedor `Local fixture provider`; Page 1 of 1. |
| 8 | Copia el UUID de esa fila; pulsa Clear; busca el UUID y aplica. | Aparece solamente esa notificación. También funciona una parte del UUID. |
| 9 | Clear; busca `ISSUE84-04@EXAMPLE.COM` y aplica. | La misma fila: búsqueda sin distinguir mayúsculas. |
| 10 | Clear; busca `issue84`; From y Through = 2026-10-14; aplica. | 12 resultados. El día final se incluye completo. |
| 11 | Cambia From a 2026-10-15 manteniendo Through en 2026-10-14; aplica. | Mensaje de fechas inválidas; conserva la consulta anterior y no envía otra petición. |
| 12 | Clear; busca `issue84-no-such-notification`; aplica. | 0 results; No notifications found; Showing 0–0 of 0; navegación deshabilitada. |
| 13 | Pulsa Clear filters en el estado vacío. | Vuelve al listado completo. |
| 14 | Busca `evidence@example.com`, aplica y pulsa View. | Abre `/notifications/68f4ab20-8aff-45e4-9047-38cfe53b458c`; destinatario e ID coinciden. Este registro previo muestra detalle con fallback e intentos. |
| 15 | Pulsa Notifications en el breadcrumb del detalle. | Regresa al listado. |
| 16 | Alterna el tema y compara estados SENT, SENT VIA FALLBACK y FAILED CRITICAL con Detail y Dashboard. | Mismos colores e iconos donde se muestran; no depender solo del color para reconocer el estado. |
| 17 | Abre DevTools → modo dispositivo y prueba 390 px de ancho. | Filtros se reorganizan; tabla con desplazamiento horizontal; controles accesibles. |

### Revisar las peticiones desde DevTools

1. F12 → Network → Fetch/XHR. Activa Preserve log.
2. Aplica un filtro y abre `GET /api/v1/notifications`.
3. Revisa Query String Parameters: `page`, `size` y los filtros aplicados.
4. Response debe ser un objeto con `content`, `totalElements`, `page`, `size`.
5. Al cambiar de página cambia `page`; al aplicar filtros vuelve a `page=0`.
6. En una fecha local 2026-10-14, verifica `from=2026-10-14T06:00:00.000Z` y
   `to=2026-10-15T06:00:00.000Z` si tu navegador está en Costa Rica.
7. No incluyas tokens, cookies ni encabezados Authorization en capturas que vayas a compartir.

### Carga y error/reintento — procedimiento para repetir manualmente

Estos estados también están cubiertos por tests automatizados. No se capturaron como pruebas visuales en esta sesión.

1. Network → Throttling → Slow 3G. Aplica un filtro y observa `Loading notifications…`.
2. Espera a que termine y verifica que aparece el resultado. Devuelve Throttling a No throttling.
3. Con la página cargada, activa Offline en Network y aplica otro filtro.
4. Debe aparecer `Couldn't load notifications` y Retry.
5. Desactiva Offline y pulsa Retry: debe cargar la consulta pendiente correctamente.
6. Para revisar cancelación: con Slow 3G, aplica rápidamente dos búsquedas distintas.
   Debe prevalecer la segunda; la primera puede aparecer como canceled en Network.

## 4. Probar el endpoint real

Con el entorno levantado y los fixtures insertados, ejecuta desde PowerShell 7:

```powershell
./docs/testing/issue-84/check-api.ps1
```

Esperado: **16 comprobaciones True**. Usa Gateway en 8080 y obtiene el token local en memoria;
no guarda el token en las evidencias. Guarda respuestas y resultados en `evidence/api-checks.json`.
Para aislar Gateway: `./docs/testing/issue-84/check-api.ps1 -BaseUrl http://localhost:8082`.

Para hacer una petición tú mismo:

```powershell
$tokenResponse = Invoke-RestMethod -Method Post -Uri 'http://localhost:8088/realms/aegis/protocol/openid-connect/token' -Body @{grant_type='password';client_id='aegis-dev-cli';username='aegis-dev';password='dev123';scope='notification:read'}
$authHeaders = @{Authorization="Bearer $($tokenResponse.access_token)"}
Invoke-RestMethod 'http://localhost:8080/api/v1/notifications?page=0&size=10&search=issue84&channel=EMAIL&priority=MEDIUM&status=SENT_VIA_FALLBACK' -Headers $authHeaders | ConvertTo-Json -Depth 6
```

Debe devolver una fila y `totalElements: 1`. Cambia `page=-1` o `size=101`: debe responder 400.
Sin Authorization debe responder 401. `GET .../notifications?limit=6` conserva el array del Dashboard.

## 5. Evidencias obtenidas

| Archivo | Qué demuestra |
| --- | --- |
| [backend-tests.log](evidence/backend-tests.log) | 46 tests, integración PostgreSQL, ArchUnit, Checkstyle y BUILD SUCCESS. |
| [frontend-tests.log](evidence/frontend-tests.log) | 14 tests aprobados en 4 archivos. |
| [frontend-build.log](evidence/frontend-build.log) | Compilación Angular aprobada. |
| [api-checks.json](evidence/api-checks.json) | 16 checks sobre HTTP real, datos y conteos reales, sin JWT almacenado. |
| [01-list.jpg](evidence/01-list.jpg) | Listado de escritorio con totales, estados y proveedores. |
| [02-pagination.jpg](evidence/02-pagination.jpg) | Segunda página, tamaño 10, total 31. |
| [03-combined-filters.jpg](evidence/03-combined-filters.jpg) | Filtros combinados: una fila esperada. |
| [04-date-range.jpg](evidence/04-date-range.jpg) | Un día local completo: 12 fixtures. |
| [05-invalid-dates.jpg](evidence/05-invalid-dates.jpg) | Validación de fechas invertidas. |
| [06-empty.jpg](evidence/06-empty.jpg) | Estado vacío con conteo cero. |
| [07-detail-navigation.jpg](evidence/07-detail-navigation.jpg) | Navegación desde fila al detalle correcto y badge de fallback. |
| [08-mobile.jpg](evidence/08-mobile.jpg) | Vista a 390 px de ancho. |

Las capturas muestran datos locales/sintéticos y respuestas reales; no se interceptaron ni simularon endpoints.
El mockup exacto `design/notifications-list-claude-preview.png` no existe en esta copia:
estas evidencias no acreditan coincidencia visual exacta con ese archivo.

## 6. Diagnóstico rápido

- **Ves “results” sin número o la tabla vacía pese a tener datos**: backend viejo. En esta sesión
  lo encontramos y lo reiniciamos. Con `page=0` la respuesta debe ser objeto paginado, no array.
- **401**: vuelve a iniciar sesión o solicita un token nuevo.
- **403**: el token debe tener scope `notification:read`.
- **503**: revisa servicio 8082, Gateway 8080 y registro en Eureka 8761.
- **CORS**: Gateway debe permitir `http://localhost:4200`.
- **Could not find a valid Docker environment**: abre Docker Desktop; usa Java 21 y la propiedad API indicada.
- **Port already in use**: detén la ejecución anterior de ese servicio desde tu IDE/terminal.
- **La fecha muestra cero filas**: usa las fechas de los fixtures (13/14 de octubre de 2026), no necesariamente hoy.


### Fallo detectado al ejecutar la suite completa

La primera ejecución de `clean verify` detectó cuatro errores de contexto en
`KafkaMessageBrokerAdapterIT` y `KafkaDeadLetterQueueAdapterIT`.
Causa: el perfil `test` excluye la base de datos y esas clases no sustituían
`DeliveryAttemptRepositoryAdapter`; su constructor necesita un `JdbcTemplate` inexistente
bajo ese perfil. Se agregó el mock del adaptador en ambas clases, siguiendo el patrón de
los demás adaptadores de persistencia. Kafka continúa siendo real con Testcontainers.
El log de la nueva ejecución completa se encuentra en `evidence/full-verify.log`.


La misma ejecución detectó tres fallos de `SecurityConfigTest` en Gateway: el perfil de
pruebas apuntaba a los puertos locales 8082/8083, donde estaba activo el backend de desarrollo.
Se aislaron esas rutas con `http://127.0.0.1:0`, conservando la ruta específica de la prueba de
propagación del token. Las pruebas ya no dependen de que los servicios locales estén apagados.

Resultado: la ejecución completa pasó Eureka, Config, Notification (incluidas las 25 IT)
y Audit. Tras corregir el aislamiento de Gateway, se reanudó con
`./mvnw.cmd verify -rf :aegis-api-gateway`: Gateway (48 tests) y User (64 tests)
pasaron, con `BUILD SUCCESS`. Evidencia: `evidence/verify-resumed.log`.
La validación quedó completada en dos ejecuciones; `full-verify.log` conserva el fallo
intermedio de Gateway para mantener el diagnóstico original.
