FROM python:3.12-slim AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
RUN useradd --create-home --uid 10001 reengage
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
USER reengage
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD ["python","-c","import urllib.request; urllib.request.urlopen('http://localhost:8000/health/live')"]
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8000","--workers","2","--proxy-headers"]
