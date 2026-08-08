from fastapi import FastAPI

from chatbot.controllers import contato_controller, mensagem_controller, webhook_controller

app = FastAPI(title="ChatBot")

app.include_router(webhook_controller.router)
app.include_router(contato_controller.router)
app.include_router(mensagem_controller.router)


@app.get("/health")
def health():
    return {"status": "ok"}
