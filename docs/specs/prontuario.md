# Spec — /prontuario (ClinicOS)

## Objetivo

Criar uma página operacional para consulta e gerenciamento dos prontuários dos pacientes. O foco é encontrar rapidamente um paciente, visualizar seu histórico e iniciar/continuar um atendimento.

---

## 1. Estrutura da página

`/prontuario`

```
┌─────────────────────────────────────────────────────────────┐
│ Prontuários                              [+ Novo atendimento]│
│ Consulte e gerencie os registros clínicos dos pacientes.     │
├─────────────────────────────────────────────────────────────┤
│ [ 🔍 Buscar paciente... ] [Profissional ▼] [Período ▼]       │
├─────────────────────────────────────────────────────────────┤
│ Paciente       Último atendimento   Profissional   Status    │
│ ─────────────────────────────────────────────────────────── │
│ João Silva     24/08/2026           Dr. Carlos     Finalizado│
│ Maria Santos   22/08/2026           Dra. Ana       Pendente  │
│ ...                                                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Cabeçalho

- **Título:** Prontuários
- **Descrição:** "Consulte e gerencie os registros clínicos dos pacientes."
- **Ação primária:** `+ Novo atendimento`

O botão deve abrir o fluxo de criação de atendimento, preferencialmente solicitando primeiro o paciente.

---

## 3. Busca e filtros

A busca deve ser o elemento mais importante da página.

### Campo de busca

Busca por:
- Nome do paciente
- CPF
- Telefone

Placeholder: `Buscar paciente por nome, CPF ou telefone...`

### Filtros

- Profissional
- Período do último atendimento
- Status do prontuário

Status iniciais: **Finalizado**, **Pendente**.

A busca deve atualizar os resultados sem exigir navegação para outra página.

---

## 4. Tabela

| Campo | Descrição |
|---|---|
| Paciente | Nome completo |
| Último atendimento | Data do último registro |
| Profissional | Profissional responsável pelo último atendimento |
| Status | Estado do atendimento/prontuário |
| Ações | Visualizar/continuar |

A tabela deve ser limpa e permitir leitura rápida.

Ações:
- Visualizar prontuário
- Quando houver atendimento pendente: **Continuar atendimento**

---

## 5. Visualização do prontuário

Ao selecionar um paciente, abrir `/prontuario/:patientId`.

Estrutura:

```
Paciente
João Silva
34 anos · CPF · Telefone

[Resumo] [Histórico] [Documentos]

────────────────────────────────────

Histórico de atendimentos

24 AGO 2026
Dr. Carlos
Consulta
[Ver atendimento]

15 JUL 2026
Dr. Carlos
Consulta
[Ver atendimento]

02 JUN 2026
Dra. Ana
Consulta
[Ver atendimento]

                         [+ Novo atendimento]
```

---

## 6. Histórico

O histórico deve ser cronológico, com o atendimento mais recente primeiro.

Cada registro deve apresentar:
- Data
- Horário
- Profissional
- Tipo de atendimento
- Status
- Resumo curto
- Ação para visualizar detalhes

Ao abrir o atendimento, mostrar o registro completo.

---

## 7. Novo atendimento

Fluxo:

```
Selecionar paciente
        ↓
Novo atendimento
        ↓
Preencher formulário
        ↓
Salvar como rascunho ou finalizar
```

O formulário deve permitir:
- Data/hora
- Profissional
- Tipo de atendimento
- Anamnese/relato
- Evolução
- Observações
- Conduta/plano
- Anexos

Ações:
- Salvar rascunho
- Finalizar atendimento

O rascunho não deve ser considerado um atendimento finalizado.

---

## 8. Requisitos de UX

- Busca deve ser rápida e evidente.
- Não utilizar excesso de cards ou elementos decorativos.
- A tabela deve ser o elemento central da página.
- O usuário deve conseguir chegar ao prontuário de um paciente em poucos cliques.
- Estado vazio deve explicar o próximo passo.
- Estado de carregamento deve utilizar skeleton.
- Exibir confirmação antes de ações destrutivas.
- Formulários devem preservar dados não salvos quando possível.
- Interface responsiva.

---

## 9. Estados necessários

- Loading
- Empty state
- No search results
- Error state
- Success feedback
- Prontuário sem atendimentos
- Atendimento em rascunho
- Atendimento finalizado

---

## 10. Regra de produto

Não transformar `/prontuario` em um dashboard.

A página deve responder rapidamente a três perguntas:

1. Qual paciente estou procurando?
2. Qual foi o último atendimento?
3. Como acesso ou registro o atendimento?

O objetivo é que um profissional consiga **buscar → abrir → consultar → registrar** com o mínimo de fricção.
