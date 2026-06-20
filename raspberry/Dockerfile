# ─── Build stage ──────────────────────────────────────────────────────────────
# Install the dependencies into an isolated virtualenv, so the runtime image
# carries only the packages — not pip's caches or any build leftovers.
# python:3.x-slim is multi-arch and runs natively on the Raspberry Pi 5 (arm64).
FROM python:3.12-slim AS builder

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

WORKDIR /app

# Copy requirements first so this layer is cached until they actually change.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ─── Runtime stage ────────────────────────────────────────────────────────────
FROM python:3.12-slim AS runtime

# Run as an unprivileged user — never root inside the container.
RUN useradd --create-home --uid 1000 occupi

WORKDIR /app

# Bring in the prebuilt virtualenv from the builder stage.
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1

# Application code (the .dockerignore keeps caches, venv and secrets out).
COPY . .

USER occupi

CMD ["python", "main.py"]
