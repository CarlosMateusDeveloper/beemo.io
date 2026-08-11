package main

import "fmt"

type StatusConsulta string

const (
	StatusAgendada      StatusConsulta = "Agendada"
	StatusConfirmada    StatusConsulta = "Confirmada"
	StatusEmEspera      StatusConsulta = "Em Espera"
	StatusEmAtendimento StatusConsulta = "Em Atendimento"
	StatusRealizada     StatusConsulta = "Realizada"
	StatusCancelada     StatusConsulta = "Cancelada"
	StatusFaltou        StatusConsulta = "Faltou"
)

func (s StatusConsulta) valid() bool {
	switch s {
	case StatusAgendada, StatusConfirmada, StatusEmEspera, StatusEmAtendimento, StatusRealizada, StatusCancelada, StatusFaltou:
		return true
	}
	return false
}

type TipoConsulta string

const (
	TipoConsultaPadrao TipoConsulta = "Consulta"
	TipoRetorno        TipoConsulta = "Retorno"
	TipoExame          TipoConsulta = "Exame"
	TipoAvaliacao      TipoConsulta = "Avaliação"
)

func (t TipoConsulta) valid() bool {
	switch t {
	case TipoConsultaPadrao, TipoRetorno, TipoExame, TipoAvaliacao:
		return true
	}
	return false
}

// Consulta espelha a tabela "consulta", enriquecida com campos só-de-leitura
// vindos de "agenda" (médico/data/hora) e "paciente" (nome) via JOIN — assim
// o frontend não precisa cruzar isso na mão a partir de três chamadas.
type Consulta struct {
	ID             int            `json:"id"`
	IDPaciente     int            `json:"idPaciente"`
	PacienteNome   string         `json:"pacienteNome"`
	IDAgenda       int            `json:"idAgenda"`
	IDMedico       int            `json:"idMedico"`
	DataSlot       string         `json:"dataSlot"`
	HoraSlot       string         `json:"horaSlot"`
	Status         StatusConsulta `json:"status"`
	Tipo           TipoConsulta   `json:"tipo"`
	DuracaoMinutos int            `json:"duracaoMinutos"`
}

// createConsultaInput é o corpo de POST /consultas. Cria (ou reaproveita) o
// slot de agenda e a consulta numa operação só, do jeito que o formulário
// "Nova consulta" do frontend preenche hoje.
type createConsultaInput struct {
	IDPaciente     int            `json:"idPaciente"`
	IDMedico       int            `json:"idMedico"`
	DataSlot       string         `json:"dataSlot"`
	HoraSlot       string         `json:"horaSlot"`
	Tipo           TipoConsulta   `json:"tipo"`
	DuracaoMinutos int            `json:"duracaoMinutos"`
	Status         StatusConsulta `json:"status"`
}

func (c createConsultaInput) validate() error {
	if c.IDPaciente <= 0 {
		return fmt.Errorf("idPaciente é obrigatório")
	}
	if c.IDMedico <= 0 {
		return fmt.Errorf("idMedico é obrigatório")
	}
	if c.DataSlot == "" || c.HoraSlot == "" {
		return fmt.Errorf("dataSlot e horaSlot são obrigatórios")
	}
	if !c.Tipo.valid() {
		return fmt.Errorf("tipo inválido: %q", c.Tipo)
	}
	if c.DuracaoMinutos <= 0 {
		return fmt.Errorf("duracaoMinutos deve ser positivo")
	}
	if !c.Status.valid() {
		return fmt.Errorf("status inválido: %q", c.Status)
	}
	return nil
}

// updateConsultaInput é o corpo de PUT /consultas/{id}. Todo campo é opcional
// (ponteiro) — só o que vier preenchido é alterado. dataSlot+horaSlot juntos
// disparam um reagendamento (move o vínculo pra outro slot de agenda).
type updateConsultaInput struct {
	IDPaciente     *int            `json:"idPaciente"`
	Status         *StatusConsulta `json:"status"`
	Tipo           *TipoConsulta   `json:"tipo"`
	DuracaoMinutos *int            `json:"duracaoMinutos"`
	DataSlot       *string         `json:"dataSlot"`
	HoraSlot       *string         `json:"horaSlot"`
}

func (u updateConsultaInput) validate() error {
	if u.IDPaciente != nil && *u.IDPaciente <= 0 {
		return fmt.Errorf("idPaciente inválido")
	}
	if u.Status != nil && !u.Status.valid() {
		return fmt.Errorf("status inválido: %q", *u.Status)
	}
	if u.Tipo != nil && !u.Tipo.valid() {
		return fmt.Errorf("tipo inválido: %q", *u.Tipo)
	}
	if u.DuracaoMinutos != nil && *u.DuracaoMinutos <= 0 {
		return fmt.Errorf("duracaoMinutos deve ser positivo")
	}
	if (u.DataSlot != nil) != (u.HoraSlot != nil) {
		return fmt.Errorf("dataSlot e horaSlot devem vir juntos para reagendar")
	}
	return nil
}
