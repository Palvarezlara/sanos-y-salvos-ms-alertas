# ms-alertas

Microservicio de **alertas de zona** y **correos** de Sanos y Salvos (Spring Boot 4, Java 17, MySQL, SNS + SQS).

- Crea una alerta geográfica cuando se reporta una mascota y la desactiva al reunificarla o eliminarla.
- Envía por correo los contactos, la confirmación de reunificación y los avisos de mensajes nuevos del chat.
- No accede a la base de ms-mascotas: le consulta el contacto del dueño por su endpoint interno.

## Endpoints

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /alertas/zona?lat=&lng=&radioKm=` | Público | Alertas activas cerca de un punto (radio 0,1 a 50 km; coordenadas aproximadas a ~1 km). |
| `GET /alertas` | Administradores | Lista todas las alertas activas. |
| `POST` / `PATCH` / `DELETE /alertas` | Administradores | Alta manual, cambio de estado y borrado (normalmente las crea la mensajería). |
| `POST /contactos` | Público, con límite de uso | Encola un mensaje de contacto para el dueño del reporte (responde 202; 429 si se supera el límite). |

Todo va bajo `/api/v1` (`SPRING_MVC_SERVLET_PATH`). Los administradores son el grupo `Administradores` de Cognito.

## Mensajería

El topic SNS `sanos-eventos` reparte los eventos a colas SQS (cada una con su DLQ) según el atributo `tipoEvento`:

| Evento | Cola | Qué hace |
|---|---|---|
| `mascota.reportada` | `cola-mascota-reportada` | Crea la alerta de zona (radio 1,5 km). |
| `mascota.reunificada` | `cola-mascota-reunificada` | Desactiva la alerta y avisa por correo. |
| `mascota.eliminada` | `cola-mascota-eliminada` | Desactiva la alerta del reporte borrado. Cola opcional. |
| `contacto.solicitado` | `cola-contacto` | Envía el correo de contacto. |
| `chat.mensaje.nuevo` | `cola-chat-aviso` | Avisa por correo de un mensaje nuevo (sin su texto). Cola opcional. |

Las colas son independientes, así que un evento de cierre puede llegar antes que `mascota.reportada`: la tabla
`mascotas_cerradas` lo recuerda y la alerta no se crea. Una restricción única impide dos alertas activas del mismo reporte.
`/actuator/health` pasa a DOWN si un consumidor está caído o no puede leer su cola.

## Variables de entorno

La configuración vive en el Config Server (`config/ms-alertas.yml`); cada variable la resuelve el entorno del contenedor.

| Variable | Obligatoria | Descripción |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Sí | MySQL (RDS en AWS). Sin valores por defecto. |
| `INTERNAL_API_TOKEN` | Sí | Secreto de servicio (mín. 32 caracteres) para `/internal/**` de ms-mascotas; el mismo valor en ms-mascotas, ms-alertas y ms-chat. Sin él no arranca. |
| `MS_MASCOTAS_BASE_URL` | Sí | URL interna de ms-mascotas. |
| `COGNITO_USER_POOL_ID`, `COGNITO_CLIENT_ID`, `COGNITO_REGION` | Sí | Validación del JWT. |
| `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_HOST`, `MAIL_PORT` | Sí | SMTP (Gmail con contraseña de aplicación). |
| `FRONTEND_URL` | Sí | Base de los enlaces de los correos. |
| `AWS_REGION`, `AWS_SNS_TOPIC_ARN`, `SQS_COLA_*_URL` | Sí | SNS y SQS. `SQS_COLA_CHAT_AVISO_URL` y `SQS_COLA_MASCOTA_ELIMINADA_URL` son opcionales. |
| `AWS_ENDPOINT_OVERRIDE` | No | Apunta a LocalStack en local. |
| `CONTACTO_MAX_POR_MASCOTA`, `CONTACTO_MAX_POR_CORREO`, `CONTACTO_VENTANA_MINUTOS` | No | Límite de `POST /contactos` (10 y 5 por 60 min por defecto). |
| `JPA_DDL_AUTO` | No | `update` en desarrollo; en AWS `validate` o `none`. |
| `SWAGGER_ENABLED` | No | `true` solo en local; por defecto Swagger está apagado. |

## Cómo levantarlo en local

1. Copia `.env.example` a `.env` y completa los valores (`.env` no se versiona).
2. Levanta antes `config-server` y `discovery-server`, y la red `sanos-y-salvos-network`; el script `levantar-sanos-y-salvos.sh` de la raíz del proyecto lo hace en orden.
3. `docker compose up -d --build`: arranca MySQL, LocalStack (crea el topic y las colas con `localstack-init/init-sqs-sns.sh`, que debe tener saltos de línea LF) y el servicio en el puerto 8083.

## Pruebas

```bash
./mvnw test
```

86 pruebas, sin dependencias externas (H2 en modo MySQL, servidores HTTP locales, SQS simulado):

- **Seguridad** (`AlertaControllerSeguridadTest`): rechazo sin token, sin rol de administrador y Swagger apagado.
- **Zona** (`AlertaRepositoryTest`): Haversine, caja de búsqueda hacia los cuatro lados y unicidad de la alerta activa.
- **Mensajería** (`*ListenerTest`, `ConsumidoresSqsTest`): eventos, orden inverso, duplicados, salud de los consumidores y asuntos sin inyección de encabezados.
- **Contactos** (`LimitadorContactosTest`, `ContactoServiceImplTest`) y **cliente de ms-mascotas** (`MascotasClientTest`, con secreto y 401).
