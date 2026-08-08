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

// bootstrapSchema cria o tipo/tabela de agenda caso ainda não existam. O
// serviço Go é o dono da tabela "agenda" (o backend Java só guarda o FK
// id_agenda em consulta).
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
