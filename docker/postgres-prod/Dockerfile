FROM postgres:15-alpine

# Variables de entorno iniciales
ENV POSTGRES_DB=prod_db
ENV POSTGRES_USER=prod_user
ENV POSTGRES_PASSWORD=prod_password

# Puerto por defecto
EXPOSE 5432

# Verificaciones de disponibildiad de la base
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 CMD pg_isready -U $POSTGRES_USER -d $POSTGRES_DB
