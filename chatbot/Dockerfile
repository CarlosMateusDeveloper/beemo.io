FROM python:3.13-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
RUN groupadd --system clinisis && useradd --system --gid clinisis --create-home --home-dir /app clinisis
WORKDIR /app
COPY chatbot/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY chatbot chatbot
RUN chown -R clinisis:clinisis /app
USER clinisis
EXPOSE 8082
CMD ["uvicorn", "chatbot.main:app", "--host", "0.0.0.0", "--port", "8082", "--proxy-headers"]
