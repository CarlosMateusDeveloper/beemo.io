package main

import "fmt"

type Situacao string

const (
	SituacaoLivre     Situacao = "Livre"
	SituacaoOcupado   Situacao = "Ocupado"
	SituacaoBloqueado Situacao = "Bloqueado"
)

func (s Situacao) valid() bool {
	switch s {
	case SituacaoLivre, SituacaoOcupado, SituacaoBloqueado:
		return true
	}
	return false
}

// Agenda espelha a tabela "agenda" do Postgres (mesmo banco usado pelo
// backend Java). DataSlot e HoraSlot ficam como string ("2026-08-01" /
// "14:30:00") para simplificar o JSON trocado com o frontend.
type Agenda struct {
	ID       int      `json:"id"`
	IDMedico int      `json:"idMedico"`
	Situacao Situacao `json:"situacao"`
	DataSlot string   `json:"dataSlot"`
	HoraSlot string   `json:"horaSlot"`
}

func (a Agenda) validate() error {
	if a.IDMedico <= 0 {
		return fmt.Errorf("idMedico é obrigatório")
	}
	if !a.Situacao.valid() {
		return fmt.Errorf("situacao inválida: %q", a.Situacao)
	}
	if a.DataSlot == "" {
		return fmt.Errorf("dataSlot é obrigatório")
	}
	if a.HoraSlot == "" {
		return fmt.Errorf("horaSlot é obrigatório")
	}
	return nil
}
