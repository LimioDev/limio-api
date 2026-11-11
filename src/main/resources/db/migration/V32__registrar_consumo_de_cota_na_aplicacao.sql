-- Cancelamento do serviço combinado (ETI-94): o estorno da cota só vale pra escolha que consumiu cota do Prestador
-- FREE. O plano é consultado no instante da escolha e o Prestador pode virar GOLD depois, então o fato "esta escolha
-- gastou uma vaga" precisa ficar gravado na própria candidatura, e não ser deduzido do plano de hoje.
ALTER TABLE aplicacao ADD COLUMN cota_consumida BOOLEAN NOT NULL DEFAULT FALSE;

-- Escolhas já feitas: até aqui o plano era um stub que devolvia sempre FREE (ConsultaPlanoPrestador), então toda
-- candidatura que chegou a ser escolhida consumiu cota.
UPDATE aplicacao SET cota_consumida = TRUE WHERE escolhida_em IS NOT NULL;
