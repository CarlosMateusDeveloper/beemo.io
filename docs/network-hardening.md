# HTTPS e superfície de rede da VPS

Este procedimento complementa [o deploy Docker](deploy-docker-production.md). O Caddy é o único serviço com portas publicadas no `docker-compose.production.yml`: `80/tcp` e `443/tcp`. O próprio Caddy solicita e renova certificados para `APP_FRONTEND_URL` e `APP_API_URL` e redireciona HTTP para HTTPS. Os volumes `caddy_data` e `caddy_config` preservam certificados e estado do ACME.

## Antes de liberar tráfego

1. Configure DNS dos dois nomes públicos para a VPS. Em `.env.production`, use URLs HTTPS sem caminho e defina `APP_WEBSOCKET_URL=wss://<host-do-frontend>`. O WebSocket da agenda usa esse host. Configure `ACME_EMAIL` e deixe `80/tcp` e `443/tcp` alcançáveis para a emissão do certificado.
2. Confirme que `docker compose --env-file .env.production -f docker-compose.production.yml config -q` passa e que **somente** o serviço `proxy` possui `ports`. `expose` não publica uma porta no host. O Caddy bloqueia `/actuator` no domínio da API.
3. Inicie o stack pelo [procedimento de deploy](deploy-docker-production.md). Não aplique HSTS ao domínio antes de confirmar HTTPS com certificado válido. A política configurada vale por um ano e não inclui subdomínios ou preload.

## Acesso SSH e firewall do host

Execute estas etapas pelo console da VPS ou mantendo uma sessão SSH aberta. Primeiro crie um usuário administrativo não root com `sudo`, instale sua chave pública em `~/.ssh/authorized_keys` e confirme **uma segunda sessão** de login por chave. Guarde acesso ao console do provedor para recuperação.

No Ubuntu/Debian, instale a configuração versionada e valide o resultado efetivo antes de recarregar o serviço:

```bash
sudo install -m 0644 deploy/ssh/99-clinisis.conf /etc/ssh/sshd_config.d/99-clinisis.conf
sudo /usr/sbin/sshd -t
sudo /usr/sbin/sshd -T | grep -E '^(pubkeyauthentication|authenticationmethods|passwordauthentication|kbdinteractiveauthentication|permitrootlogin) '
sudo systemctl reload ssh
```

Confirme no `sshd -T`: `pubkeyauthentication yes`, `authenticationmethods publickey`, `passwordauthentication no`, `kbdinteractiveauthentication no` e `permitrootlogin no`. Se outra diretiva anterior prevalecer, corrija a ordem dos arquivos `sshd_config` e repita o teste antes do reload. Em outra sessão, teste o login por chave do administrador e confirme que login por senha e `root` são recusados.

Instale o UFW e permita apenas HTTP, HTTPS e o IP ou CIDR de administração na porta SSH. Substitua `203.0.113.10/32` pelo seu endereço real; se usa IPv6, adicione também o CIDR IPv6 correspondente. Ajuste a porta SSH se ela não for 22. Revise regras já existentes antes de ativar o UFW; regras antigas de `ALLOW` podem ampliar a superfície permitida.

```bash
sudo apt-get install ufw
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow from 203.0.113.10/32 to any port 22 proto tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw status numbered
sudo ufw enable
sudo ufw status verbose
```

Se o IP administrativo muda, permita a nova origem **antes** de remover a antiga. Regras de firewall do provedor devem ter o mesmo conjunto de portas. Docker pode publicar portas usando regras próprias; mantenha o Compose sem `ports` nos serviços internos e verifique o host após cada mudança.

## Verificação externa e regressão

De outra rede, execute uma varredura TCP completa (`nmap -Pn -p- <IP-da-VPS>`) e registre as portas vistas; apenas 80, 443 e a porta SSH aprovada devem responder, sendo SSH acessível somente do CIDR permitido. Verifique também `ss -lntup` no host e `docker compose ... ps` para distinguir portas em escuta de portas publicadas.

Para ambos os domínios, execute `curl -Iv https://<host>` sem `-k` e confirme validade do certificado e cadeia TLS. `curl -I http://<host>` deve retornar redirecionamento para a URL HTTPS. `curl -I https://<host>` deve trazer HSTS, `Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` e `Referrer-Policy`. A API e PostgreSQL não devem responder em portas diretas; `/actuator/health` pelo domínio público deve retornar 404.

No navegador, teste login local, login Google/Microsoft se configurados, carregamento de fontes, agenda com WebSocket, chatbot e download de exportação. Confira o console por violações de CSP. A política permite estilos inline usados pelo React, fontes do Google, chamadas à API configurada e WebSocket do frontend; scripts continuam limitados à própria origem. Ao alterar qualquer origem externa, revise a CSP antes do deploy.

Registre as saídas, data, IP de origem da varredura e resultado dos fluxos. O fechamento da #62 exige essa evidência na VPS real; a configuração no repositório, sozinha, não comprova certificado, firewall ou SSH efetivos.
