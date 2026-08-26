import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from chatbot.controllers import contato_controller, mensagem_controller, webhook_controller, whatsapp_controller

app = FastAPI(title="ChatBot")

allowed_origins = os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:5173").split(",")
app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(webhook_controller.router)
app.include_router(contato_controller.router)
app.include_router(mensagem_controller.router)
app.include_router(whatsapp_controller.router)


@app.get("/health")
def health():
    return {"status": "ok"}
