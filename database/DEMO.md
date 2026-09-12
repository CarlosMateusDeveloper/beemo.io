# Dados de demonstração — ClinicOS

Carga aplicada no banco local `clinica`, com data-base **12/09/2026**.
Os registros anteriores foram preservados. Cadastros sintéticos usam `[DEMO]`,
CPF/CRM/ANS deliberadamente inválidos, DDD `00` e e-mail `example.invalid`.
Nenhuma mensagem, cobrança ou recurso foi enviado a serviços externos.

## Conteúdo

- 60 pacientes, 6 médicos e 4 convênios fictícios, com 8 planos e 24 procedimentos.
- 456 consultas: 336 históricas, 24 na data-base e 96 futuras.
- 300 prontuários com sinais vitais; 24 exames e 6 entradas na lista de espera.
- 414 faturas, 240 pagamentos e 27 despesas pagas, pendentes ou canceladas.
- 4 lotes, 12 glosas e 4 recursos simulados.
- 18 conversas de WhatsApp: 6 aguardando, 6 em atendimento e 6 com assistente.
- Os recebimentos fictícios da data-base estão vinculados ao turno do caixa.

## Roteiro sugerido

1. **Dashboard:** indicadores e evolução dos últimos 30/90 dias.
2. **Agenda:** selecione 12/09/2026 ou a semana seguinte e um médico `[DEMO]`.
3. **Pacientes:** busque `[DEMO]` e abra o histórico de um paciente.
4. **Caixa:** recebimentos e pendências do dia; depois DRE, fluxo consolidado e despesas.
5. **Convênios:** planos, procedimentos, auditorias, lotes e glosas.
6. **WhatsApp:** use os filtros de aguardando, em atendimento e assistente.
   A conversa é um histórico simulado; envio real depende da integração.

## Reexecutar

Na raiz do repositório, usando as dependências existentes do chatbot:

```powershell
chatbot/.venv/Scripts/python.exe database/seed_demo.py --dry-run
chatbot/.venv/Scripts/python.exe database/seed_demo.py
```

O script usa os defaults locais do `backend/docker-compose.yml` e aceita
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` e `ID_CLINICA`.
Não imprime credenciais. Toda carga inicial ocorre em uma transação; uma falha
cancela as inserções. O PostgreSQL pode consumir números de sequência mesmo
em `--dry-run`, comportamento normal sem exclusão de dados.

A identificação `demo.v1.*@example.invalid` impede duplicação. Reexecutar não
move as datas nem restaura dados editados durante a apresentação. O arquivo
`demo_manifest.json` registra os IDs inseridos na carga inicial para rastreio;
nenhum comando de limpeza destrutiva é executado automaticamente.
