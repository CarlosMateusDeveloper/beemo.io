package main

import (
	"database/sql"
	"fmt"
	"log"
	"os"

	_ "github.com/lib/pq"
)

func env(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func openDB() *sql.DB {
	host := env("DB_HOST", "localhost")
	port := env("DB_PORT", "5432")
	name := env("DB_NAME", "clinica")
	user := env("DB_USER", "clinica")
	password := env("DB_PASSWORD", "clinica")
	sslmode := env("DB_SSLMODE", "disable")

	dsn := fmt.Sprintf("host=%s port=%s dbname=%s user=%s password=%s sslmode=%s",
		host, port, name, user, password, sslmode)

	db, err := sql.Open("postgres", dsn)
	if err != nil {
		log.Fatalf("erro ao abrir conexão com o postgres: %v", err)
	}
	if err := db.Ping(); err != nil {
		log.Fatalf("não foi possível conectar ao postgres em %s:%s/%s: %v", host, port, name, err)
	}
	return db
}

// bootstrapSchema cria o tipo/tabela de agenda caso ainda não existam — é uma
// conveniência para rodar o agenda-service isolado em dev (sem o restante do
// schema). Em qualquer ambiente integrado, database/schema_clinica.sql é a
// fonte de verdade (inclusive da FK id_medico -> medico, que este bootstrap
// não recria de propósito: medico não é uma tabela que este serviço deveria
// criar). Se schema_clinica.sql já rodou, isso tudo vira no-op.
//
// "consulta" (ver consulta.go) não tem bootstrap aqui: ela referencia
// paciente e agenda, então só faz sentido existir depois do schema
// compartilhado. Se as tabelas não existirem, as queries falham com um erro
// claro do Postgres em vez de tentar recriar tabelas de outro domínio.
func bootstrapSchema(db *sql.DB) {
	stmts := []string{
		`DO $$ BEGIN
			CREATE TYPE situacao_agenda AS ENUM ('Livre', 'Ocupado', 'Bloqueado');
		EXCEPTION WHEN duplicate_object THEN NULL;
		END $$;`,
		`CREATE TABLE IF NOT EXISTS agenda (
			id_agenda INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
			id_medico INT NOT NULL,
			situacao situacao_agenda NOT NULL DEFAULT 'Livre',
			data_slot DATE NOT NULL,
			hora_slot TIME NOT NULL,
			UNIQUE (id_medico, data_slot, hora_slot)
		);`,
	}
	for _, stmt := range stmts {
		if _, err := db.Exec(stmt); err != nil {
			log.Fatalf("erro ao aplicar schema da agenda: %v", err)
		}
	}
}
