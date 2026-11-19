# AI service cua Courtly. Chi lang nghe trong mang noi bo; Spring Boot goi qua REST voi X-Internal-Token.
FROM python:3.12-slim
COPY --from=ghcr.io/astral-sh/uv:latest /uv /bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY courtly_ai ./courtly_ai
# artifacts/ (file .cbm) KHONG nam trong image: mount thu muc artifacts vao /app/artifacts.
ENV AI_ARTIFACT_DIR=/app/artifacts
EXPOSE 8001
CMD ["uv", "run", "--no-dev", "uvicorn", "courtly_ai.service:app", "--host", "0.0.0.0", "--port", "8001"]
