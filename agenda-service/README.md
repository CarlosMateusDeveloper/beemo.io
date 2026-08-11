# agenda-service — rodando localmente (sem Docker)

Checklist pra subir o Postgres, o `agenda-service` e o frontend na sua
máquina e testar o fluxo de agenda em tempo real de ponta a ponta.

## 1. Instalar o PostgreSQL

Baixe o instalador oficial em https://www.postgresql.org/download/windows/
(ou https://get.enterprisedb.com/postgresql/) e instale normalmente — nessa
máquina, ao contrário do sandbox onde eu tentei, você deve ter permissão de
administrador pra isso funcionar sem problema.

Anote a senha que você definir para o usuário `postgres` (superusuário) na
instalação — vai precisar dela no próximo passo.

Confirme que `psql` está disponível no terminal (o instalador normalmente já
adiciona ao PATH; se não adicionar, abra o "SQL Shell (psql)" que vem junto,
ou adicione manualmente a pasta `bin` da instalação ao PATH).

## 2. Criar o banco e o usuário do projeto

Todo o projeto (backend Java, agenda-service, e este checklist) já assume
as mesmas credenciais por padrão — usuário `clinica`, senha `clinica`, banco
`clinica`, em `localhost:5432` (é o mesmo padrão do `backend/docker-compose.yml`,
só que agora sem Docker).

```
psql -U postgres -h localhost
```

Dentro do `psql` (vai pedir a senha do `postgres` que você definiu na instalação):

```sql
CREATE ROLE clinica WITH LOGIN PASSWORD 'clinica';
CREATE DATABASE clinica OWNER clinica;
\q
```

## 3. Aplicar o schema

Da raiz do repositório (`clinica/`):

```
psql -U clinica -d clinica -h localhost -f database/schema_clinica.sql
```

Vai pedir a senha `clinica`. Se der erro em algum `CREATE TABLE`/`CREATE TYPE`
porque já existe, é sinal de que essa parte já tinha sido aplicada antes —
normalmente não precisa fazer nada a respeito, só segue.

## 4. Rodar o agenda-service

Da pasta `agenda-service/`:

```
go run .
```

Se a conexão com o banco funcionar, ele imprime algo como:
```
agenda-service ouvindo na porta 8081 (websocket em /ws/agenda)
```

As credenciais acima (`clinica`/`clinica`/`clinica`/`localhost:5432`) já são
o default — só precisa sobrescrever via variável de ambiente
(`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) se você tiver
usado outra coisa no passo 2.

## 5. Rodar o frontend

Em outro terminal, na pasta `frontend/`:

```
npm run dev
```

Abra `http://localhost:5173/agenda` (ou a porta que o Vite mostrar).

## 6. Testar o tempo real de verdade

1. Cadastre pelo menos um médico e um paciente direto no banco (ainda não
   há tela de cadastro pronta) — via `psql -U clinica -d clinica`:
   ```sql
   INSERT INTO especialidade (nome) VALUES ('Clínica geral');
   INSERT INTO medico (nome, crm, id_especialidade) VALUES ('Dra. Camila Rocha', '12345-SP', 1);
   INSERT INTO paciente (nome, cpf, data_nascimento, ddd, numero)
     VALUES ('Maria Souza', '11122233344', '1990-01-01', '84', '999999999');
   ```
2. Abra `/agenda` em **duas abas** do navegador.
3. Numa aba, clique num horário livre e crie uma consulta.
4. Confirme que a **outra aba atualiza sozinha**, sem dar refresh — isso é
   o WebSocket (`/ws/agenda`) funcionando de verdade.
5. Arraste o card pra outro horário numa aba e confirme que a outra reflete
   a mudança também.

## Nota sobre o backend Java

`backend/src/main/resources/application.yml` está com
`hibernate.ddl-auto: update` — ou seja, ao subir o backend Java, o Hibernate
também vai tentar ajustar o schema com base nas entidades JPA. Rode o
`schema_clinica.sql` **antes** de subir o backend Java pela primeira vez,
pra ele encontrar as tabelas já no formato certo em vez de tentar inferir
sozinho.
