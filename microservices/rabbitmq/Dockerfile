FROM rabbitmq:3-management

EXPOSE 8080 15672

CMD ["sh", "-c", "export RABBITMQ_NODE_PORT=${PORT:-8080} && rabbitmq-server"]
