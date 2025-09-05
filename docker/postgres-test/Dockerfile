FROM postgres:15-alpine

# Variables de entorno iniciales
ENV POSTGRES_DB=test_db
ENV POSTGRES_USER=test_user
ENV POSTGRES_PASSWORD=test_password

# Puerto por defecto
EXPOSE 5433

# Verificaciones de disponibildiad de la base
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 CMD pg_isready -U $POSTGRES_USER -d $POSTGRES_DB