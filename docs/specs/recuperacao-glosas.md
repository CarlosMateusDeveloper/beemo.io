# Spec — Recuperação de Glosas (ClinicOS)

## 1. Objetivo

Criar dentro de `/convenios` um fluxo completo para analisar, contestar e acompanhar glosas, permitindo que a clínica tente recuperar valores recusados pelo convênio.

Fluxo:

```
Glosa identificada → Análise → Preparação do recurso → Envio → Acompanhamento → Resultado
```

O objetivo não é apenas registrar a glosa, mas **maximizar a recuperação do valor perdido**.

---

## 2. Fluxo da glosa

```
GLOSA RECEBIDA
      ↓
   ANÁLISE
      ↓
É RECORRÍVEL?
   ↙       ↘
 NÃO        SIM
  ↓          ↓
CONFIRMADA  PREPARAR RECURSO
                ↓
          RECURSO ENVIADO
                ↓
          AGUARDANDO RETORNO
                ↓
        ┌───────┴────────┐
        ↓                ↓
    RECUPERADA        NEGADA
```

---

## 3. Identificação da glosa

Uma glosa deve possuir:

- ID
- Lote de origem
- Atendimento
- Paciente
- Convênio
- Plano
- Procedimento
- Valor faturado
- Valor glosado
- Data da glosa
- Código/motivo informado pelo convênio
- Descrição do motivo
- Prazo para recurso
- Status
- Responsável
- Origem

A glosa pode ser:

- Importada do retorno do convênio;
- Registrada manualmente.

Futuramente, o sistema pode interpretar automaticamente arquivos de retorno dos convênios.

---

## 4. Análise da glosa

Ao abrir uma glosa, o responsável deve encontrar um painel de análise.

```
Glosa #1024

Valor glosado
R$ 350,00

Motivo
Ausência de autorização

Prazo para recurso
05/09/2026

────────────────────────

Atendimento
Paciente
Procedimento
Profissional
Data

────────────────────────

Documentos disponíveis
✓ Prontuário
✓ Guia
✓ Solicitação médica
✕ Autorização
```

**Ações**

- Recorrer
- Aceitar glosa
- Solicitar documentação
- Alterar responsável

---

## 5. Classificação da glosa

Antes de criar o recurso, o responsável deve classificar:

**Recorribilidade**
- Recorrível
- Não recorrível
- Necessita análise

**Motivo** — utilizar categorias padronizadas:
- Autorização
- Documentação
- Código/procedimento
- Elegibilidade
- Cobertura
- Cobrança
- Prazo
- Duplicidade
- Outros

Essa classificação será fundamental posteriormente para descobrir por que a clínica está sofrendo glosas.

---

## 6. Preparação do recurso

Se a glosa for recorrível: **Criar recurso**.

O sistema deve abrir um formulário.

**Informações**

**Justificativa** — campo de texto para explicar por que a glosa deve ser reconsiderada.

**Documentos/evidências** — permitir anexar:
- Prontuário;
- Guia;
- Solicitação médica;
- Autorização;
- Laudos;
- Comprovantes;
- Outros documentos.

Os documentos existentes no ClinicOS devem poder ser selecionados sem precisar fazer novo upload.

---

## 7. Sugestão automática de recurso

O ClinicOS pode gerar uma sugestão com base em:

- motivo da glosa;
- procedimento;
- regras do convênio;
- documentos disponíveis;
- histórico de recursos semelhantes.

Exemplo:

> **Sugestão de justificativa**
>
> "O procedimento foi realizado mediante autorização nº 92831, registrada em 25/08/2026. O documento de autorização está anexado ao recurso..."

O usuário deve revisar e aprovar antes do envio.

**A IA não deve enviar recursos autonomamente.**

---

## 8. Checklist antes do envio

Antes de permitir o envio:

**Preparação do recurso**
- ✓ Motivo analisado
- ✓ Justificativa preenchida
- ✓ Documentação obrigatória anexada
- ✓ Evidências conferidas
- ✓ Responsável definido
- ✓ Prazo válido

`[Enviar recurso]`

Se faltar algo:

> ❌ Não é possível enviar. Documento obrigatório ausente.

---

## 9. Controle de prazo

Cada recurso deve possuir:

- Data limite;
- Dias restantes;
- Status do prazo.

Classificação visual:

- 🟢 Mais de 7 dias
- 🟡 3–7 dias
- 🔴 Menos de 3 dias
- ⚫ Prazo expirado

O sistema deve gerar notificações para o responsável.

Exemplo:

> **Recurso vence amanhã**
>
> Glosa #1024 — R$ 350,00

---

## 10. Envio do recurso

Registrar:

- Data do envio;
- Usuário responsável;
- Canal utilizado;
- Protocolo;
- Documentos enviados;
- Justificativa enviada.

**Canais**

Inicialmente:
- Manual;
- Portal do convênio;
- E-mail.

Futuramente:
- Integrações/API;
- Padrões de troca de dados dos convênios.

---

## 11. Acompanhamento

Após o envio:

```
Recurso enviado
      ↓
Aguardando retorno
      ↓
Retorno recebido
      ↓
Resultado
```

**Status:**
- Rascunho
- Em preparação
- Enviado
- Aguardando retorno
- Em análise pelo convênio
- Recuperado
- Recuperado parcialmente
- Negado
- Prazo expirado

---

## 12. Resultado

Quando o convênio responder, registrar:

- Data da resposta;
- Resultado;
- Valor recuperado;
- Valor não recuperado;
- Motivo da negativa, se houver;
- Protocolo;
- Documento de resposta.

Exemplo:

```
Resultado do recurso

Valor glosado:        R$ 1.000,00
Valor recuperado:     R$ 800,00
Valor não recuperado: R$ 200,00

Resultado:
✓ Recuperação parcial
```

---

## 13. Histórico

Toda alteração deve gerar histórico:

```
29/08 10:32 — Glosa criada
29/08 11:05 — Classificada como recorrível
29/08 11:20 — Recurso iniciado
29/08 11:45 — Documentação anexada
29/08 12:10 — Recurso enviado
02/09 14:20 — Retorno recebido
02/09 14:25 — R$ 800 recuperados
```

Isso cria rastreabilidade completa.

---

## 14. Indicadores de recuperação

Dentro da área de Glosas, mostrar:

| Indicador | Exemplo | Descrição |
|---|---|---|
| Taxa de recuperação | 72% | Percentual do valor glosado que foi recuperado |
| Valor recuperável | R$ 12.450 | Glosas ainda passíveis de recurso |
| Valor recuperado | R$ 35.200 | — |
| Valor perdido | R$ 8.700 | — |
| Recursos pendentes | 14 | — |

---

## 15. Inteligência após a recuperação

O resultado do recurso deve alimentar o histórico do ClinicOS.

Exemplo:

> **Padrão identificado**
>
> 78% das glosas por "ausência de autorização" são recuperadas quando a autorização é anexada ao recurso.

Ou:

> **Problema recorrente**
>
> O procedimento 40301234 representa 34% das glosas da clínica nos últimos 90 dias.

Esses dados posteriormente alimentam o motor de prevenção de glosas.

---

## 16. Resultado final

O ciclo completo do ClinicOS passa a ser:

```
ATENDIMENTO
     ↓
PRÉ-AUDITORIA
     ↓
FATURAMENTO
     ↓
CONVÊNIO
     ↓
 ┌───┴────┐
 ↓        ↓
PAGO    GLOSA
          ↓
        ANÁLISE
          ↓
     É RECORRÍVEL?
       ↓       ↓
      NÃO      SIM
       ↓        ↓
    PERDIDA   RECURSO
                ↓
             ENVIO
                ↓
             RETORNO
                ↓
       ┌────────┴────────┐
       ↓                 ↓
  RECUPERADA           NEGADA
       ↓                 ↓
       └────────┬────────┘
                ↓
        ANÁLISE DE CAUSA
                ↓
        MELHORIA DAS REGRAS
                ↓
          MENOS GLOSAS
```

A parte mais importante é fechar esse ciclo. A recuperação não deve ser uma tela isolada: cada recurso e seu resultado devem gerar dados que posteriormente melhoram a Auditoria preventiva.
