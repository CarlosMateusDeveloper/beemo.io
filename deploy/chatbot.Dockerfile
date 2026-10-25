FROM python:3.13-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY chatbot/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt
COPY --chown=10001:10001 chatbot/ /app/chatbot/
USER 10001:10001
EXPOSE 8082
CMD ["uvicorn", "chatbot.main:app", "--host", "0.0.0.0", "--port", "8082", "--no-access-log"]
