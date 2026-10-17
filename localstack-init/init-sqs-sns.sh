#!/usr/bin/env bash
# Se ejecuta solo una vez, cuando LocalStack termina de levantar (ver
# "/etc/localstack/init/ready.d" en el docker-compose). Recrea en el
# emulador local la misma infraestructura que arma Terraform en AWS real
# (terraform/mensajeria.tf): un topic SNS, dos colas SQS con su DLQ, cada
# una suscrita al topic filtrando por el atributo "tipoEvento".
set -euo pipefail

echo "Creando infraestructura de mensajeria en LocalStack..."

TOPIC_ARN=$(awslocal sns create-topic --name sanos-eventos --query TopicArn --output text)
echo "Topic: $TOPIC_ARN"

crear_cola() {
  local nombre="$1"
  local tipo_evento="$2"

  local dlq_url dlq_arn url arn

  dlq_url=$(awslocal sqs create-queue --queue-name "${nombre}-fallidos" --query QueueUrl --output text)
  dlq_arn=$(awslocal sqs get-queue-attributes --queue-url "$dlq_url" --attribute-names QueueArn --query Attributes.QueueArn --output text)

  cat > /tmp/redrive.json <<EOF
{"RedrivePolicy": "{\"deadLetterTargetArn\":\"$dlq_arn\",\"maxReceiveCount\":\"3\"}"}
EOF
  url=$(awslocal sqs create-queue --queue-name "$nombre" --attributes file:///tmp/redrive.json --query QueueUrl --output text)
  arn=$(awslocal sqs get-queue-attributes --queue-url "$url" --attribute-names QueueArn --query Attributes.QueueArn --output text)

  cat > /tmp/sub.json <<EOF
{"RawMessageDelivery": "true", "FilterPolicy": "{\"tipoEvento\":[\"$tipo_evento\"]}"}
EOF
  awslocal sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$arn" --attributes file:///tmp/sub.json

  echo "Cola: $nombre -> $url (filtra tipoEvento=$tipo_evento)"
}

crear_cola "cola-contacto" "contacto.solicitado"
crear_cola "cola-mascota-reportada" "mascota.reportada"

echo "LocalStack listo."
