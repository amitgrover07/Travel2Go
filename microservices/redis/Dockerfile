FROM redis:alpine

EXPOSE 8080

CMD ["sh", "-c", "if [ -z \"$SPRING_REDIS_PASSWORD\" ]; then redis-server --port ${PORT:-8080} --protected-mode no; else redis-server --port ${PORT:-8080} --requirepass \"$SPRING_REDIS_PASSWORD\" --protected-mode no; fi"]
