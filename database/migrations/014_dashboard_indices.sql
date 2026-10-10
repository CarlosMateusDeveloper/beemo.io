-- Aplicar após 012/013. A política RLS já restringe id_clinica.
-- A consulta mais frequente filtra tenant + intervalo, com profissional opcional.
CREATE INDEX IF NOT EXISTS idx_dashboard_agenda_periodo
    ON agenda(id_clinica, data_slot, id_medico);
-- Evita varredura das consultas ao buscar a primeira visita dos pacientes do período.
CREATE INDEX IF NOT EXISTS idx_dashboard_consulta_paciente
    ON consulta(id_clinica, id_paciente, id_agenda);
-- Os UNIQUE existentes em consulta(id_clinica,id_agenda) e fatura(id_clinica,id_consulta)
-- atendem aos joins e garantem uma única fatura por consulta.
