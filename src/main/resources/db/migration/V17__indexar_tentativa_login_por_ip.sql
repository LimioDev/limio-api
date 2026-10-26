-- Rate limit do login por IP (decisão do tech lead, 2026-10-26): busca as últimas falhas do IP.
CREATE INDEX idx_tentativa_login_ip_criado_em ON tentativa_login (ip, criado_em DESC);
