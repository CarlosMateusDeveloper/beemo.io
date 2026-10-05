# Landing page do Clinisis

Página inicial pública do Clinisis, compartilhada com o frontend do ClinicOS. A rota `/` sempre exibe a landing, mesmo com sessão ativa. O botão **Entrar** abre `/login`, e o dashboard autenticado fica em `/dashboard`. O login concluído por SSO ou magic link leva ao dashboard.

A landing não depende da API nem da restauração de sessão. As interações de oportunidades, jornadas e resultados usam somente exemplos em memória, sem coleta de leads ou envio de mensagens. Seus estilos ficam limitados ao componente, preservando o tema das telas do sistema.

## Executar o sistema com a página inicial

No diretório `frontend`, execute:

```powershell
npm run dev -- --port 5173
```

Abra http://localhost:5173/. O build de `frontend` já inclui a landing e o acesso ao login no mesmo domínio. Publique `frontend/dist` com fallback de navegação para `index.html`, como exigido pelas demais rotas do sistema.

## Prévia independente

No diretório `landing-page`, execute:

```powershell
npm run dev -- --host 127.0.0.1 --port 5174 --strictPort
```

Abra http://127.0.0.1:5174. O botão **Entrar** aponta para o ClinicOS em http://localhost:5173/login durante o desenvolvimento.

## Build e destino do login

```powershell
npm run build
```

O resultado é gerado em `dist` do projeto em que o comando foi executado. No frontend principal, o login sempre aponta para `/login`, sem configuração adicional. Apenas para publicar a prévia independente de `landing-page`, defina `VITE_APP_URL` com a URL base pública do ClinicOS antes de compilar. O componente acrescenta `/login` ao endereço. Sem essa configuração, o link de login fica oculto no build independente de produção.

O CTA **Quero conhecer o sistema** abre a demonstração interativa na própria página. Não há agendamento ou envio de formulário implícito.

## Estrutura

- `src/ClinisisLanding.jsx`: narrativa, navegação, integração conceitual e perguntas frequentes.
- `src/ProductPreview.jsx`: marca, ícones vetoriais e demonstrações interativas.
- `src/ClinisisLanding.css`: estilos e adaptações para celular/tablet.
- `index.html`: idioma, título, descrição e metadados de compartilhamento.
- `../frontend/src/main.jsx`: rota inicial pública e rotas autenticadas do sistema.

As fontes Manrope e DM Sans são carregadas do Google Fonts, com fonte de sistema como alternativa. Exemplos de números, pacientes e mensagens são identificados na página. Integrações são apresentadas como previstas no MVP, sem alegar compatibilidade com fornecedores específicos.
