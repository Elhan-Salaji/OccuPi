FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY mock_pis.py demo_pis.py ./

# Non-root, same discipline as the real Pi sender image.
RUN useradd --uid 1000 --create-home occupi
USER occupi

CMD ["python", "mock_pis.py"]
