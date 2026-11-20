# ⚔️ vClans — Sistema Completo de Clãs (Rede Eclypse)

Sistema de clãs de alta performance, modular e profissional desenvolvido sob medida para a **Rede Eclypse** (Minecraft 1.20.1 com loader **Forge 47.4.13** em ambiente híbrido Bukkit/Forge como Mohist / Arclight).

---

## 📌 1. Análise do Ambiente e Arquitetura Compatível

### 1.1 Ambiente Real Identificado
- **Versão do Jogo:** Minecraft 1.20.1
- **Loader:** Forge 47.4.13
- **Ambiente de Execução:** Servidor híbrido Forge/Bukkit (Arclight / Mohist 1.20.1).
- **Projetos Existentes:** Plugins Bukkit/Spigot compatíveis como `vtags`, `vsaladotempo`, integrados com **LuckPerms**, **PlaceholderAPI**, **Vault** e mod **DragonMineZ**.

### 1.2 Por que a arquitetura escolhida é um Plugin Spigot/Bukkit 1.20.1?
1. **Roteamento de Eventos de Combate no Híbrido:** O núcleo híbrido converte ataques de mods (incluindo projéteis e skills do DragonMineZ) nos eventos padrão da Bukkit (`EntityDamageByEntityEvent`, `EntityDamageEvent`, `PotionSplashEvent`, `AreaEffectCloudApplyEvent`). Um plugin Bukkit intercepta com prioridade `HIGHEST`, garantindo cancelamento absoluto de dano entre aliados.
2. **Integração de Ecosistema:** Permite suporte nativo e direto a PlaceholderAPI (para TAB, Scoreboard e Chat), LuckPerms para permissões e SQLite/MySQL assíncrono sem exigir mods no cliente.
3. **Display Entities Nativas (1.20.1):** Utiliza as entidades `TextDisplay` adicionadas nativamente no Minecraft 1.19.4/1.20.1 para criar painéis de ranking em alta definição com billboard dinâmico, fundo translúcido e formatação rica sem necessidade de mods de terceiros.

---

## 🛡️ 2. Proteção Total Contra Fogo Amigo (Friendly Fire)

A proteção contra fogo amigo foi implementada no `FriendlyFireManager` e no `CombatListener` com prioridade `HIGHEST`:
- **Ataques Corpo a Corpo:** Espadas, punhos, armas de mods (Forge/DragonMineZ).
- **Projéteis:** Flechas normais, espectrais, com efeitos (tipped), tridentes, foguetes disparados com besta, bolas de fogo de ghast/dragão/blaze, crânios de wither, ovos, bolas de neve e poções arremessáveis.
- **Explosões Atribuíveis:** TNT ativada por aliado, camas/âncoras detonadas, raios canalizados por tridente (`Channeling`).
- **Nuvens e Poções:** Poções de dano imediato, veneno, fraqueza, lentidão e wither não afetam aliados na área de impacto.
- **Entidades Domesticadas / Summons:** Lobos e pets domesticados por um membro não atacam outros membros do clã.
- **Alianças:** Proteção estendida a clãs aliados caso configurado em `alliances.yml` (`friendly-fire-disabled: true`).
- **Bypass Seguro:** Permissão restrita `vclans.bypass.friendlyfire` (e `eclypseclans.bypass.friendlyfire`) exclusiva para admins (desativada por padrão).

---

## 🖥️ 3. Menus Gráficos (GUI) — `/clan`

O menu `/clan` adapta sua interface dinamicamente:

### 3.1 Jogador Sem Clã:
- **Fundar Clã:** Instruções claras e custos (se configurados no Vault).
- **Convites Recebidos:** Interface interativa listando convites pendentes com tempo restante, permitindo Aceitar (botão esquerdo) ou Recusar (botão direito).
- **Ranking dos Clãs:** Top 10 geral por pontos, duelos, guerras e KDR.
- **Alternar Convites:** Ativar ou desativar o recebimento de novos convites.

### 3.2 Jogador Com Clã:
- **Visão Geral do Clã:** Ícone central com nome, tag colorida, líder, data de fundação, KDR e pontos.
- **Lista de Membros:** Paginação completa com cabeças de jogadores, cargos, tempo de entrada e status online/offline.
- **Gerenciar Membros:** Promover, rebaixar, expulsar ou transferir liderança respeitando a hierarquia rígida.
- **Convidar Jogador:** Lista todos os jogadores online elegíveis e permite enviar convite com 1 clique.
- **Alianças:** Visualizar aliados atuais, convidar novos clãs ou desfazer alianças.
- **Duelos de Clã:** Seleção de clãs oponentes online para desafiar na arena disponível.
- **Configurações do Clã:** Paleta de cores da tag, modo de entrada (aberto ou convite), fogo amigo interno e notificações.
- **Sair / Desfazer Clã:** Menus modais de confirmação de segurança (Verde = Confirmar, Vermelho = Cancelar).

---

## 🏆 4. Top Clãs em HD no Spawn

- **Tecnologia:** Utiliza a entidade nativa `TextDisplay` do Minecraft 1.20.1.
- **Visual:** Renderizado com billboard dinâmico (`CENTER`, acompanha a rotação do jogador), escala ampliada (1.15x), sombra de texto e fundo escuro translúcido estilo Anime RPG.
- **Destaques:** Medalhas 🥇, 🥈, 🥉 para os 3 primeiros colocados, tag customizada, líder, vitórias em duelos e guerras, pontos e KDR.
- **Atualização Inteligente:** Executada de forma assíncrona a cada 5 minutos (configurável), sem impactar o tick do servidor.
- **Comandos de Configuração:**
  - `/clan admin top set` — Posiciona o painel visual no local exato do jogador.
  - `/clan admin top reload` — Força a atualização imediata dos dados do painel.
  - `/clan admin top remove` — Remove o painel do spawn.

---

## ⚔️ 5. Sistema de Duelos e Guerras

### 5.1 Duelos (Partidas Rápidas):
- Desafio enviado via GUI ou `/clan desafiar <tag>`.
- O oponente aceita via `/clan duelo aceitar <tag>`.
- Jogadores são teleportados para `Spawn 1` e `Spawn 2` da arena.
- Contagem regressiva (5s) com títulos, sons e barreira de início.
- Detecção automática de eliminações, combate limpo, anti-combat logging e teleportação de volta ao ponto de saída configurado.
- Configuração de arenas:
  - `/clan admin arena criar <nome>`
  - `/clan admin arena setspawn1 <nome>`
  - `/clan admin arena setspawn2 <nome>`
  - `/clan admin arena setexit <nome>`

### 5.2 Guerras (Persistentes):
- Declaração de guerra: `/clan guerra declarar <tag>`.
- O clã alvo aceita com: `/clan guerra aceitar <tag>`.
- Abates no mundo de sobrevivência contam pontos para a guerra.
- Condições de vitória: Atingir a meta de kills (ex: 30 kills) ou maior pontuação ao expirar o tempo (ex: 48h).
- Opção de rendição: `/clan guerra render-se`.

---

## 🏷️ 6. Chat, Scoreboard e PlaceholderAPI

### 6.1 Chat
- Se ativado `chat.override-chat-format: true`, aplica formatação com `[Cargo] [TAG] Jogador: Mensagem`.
- Se `chat.override-chat-format: false`, integra-se perfeitamente com plugins de chat externos (ex: LPC, EssentialsChat) através dos placeholders.

### 6.2 Scoreboard & Nametag
- Se `nametag.use-vanilla-teams: true`, atualiza a tag `[TAG]` diretamente no prefixo da equipe vanilla acima da cabeça do jogador e no tab list.
- Compatível com TAB / AnimatedScoreboard via PlaceholderAPI.

### 6.3 Placeholders Disponíveis:
Suporta tanto `%vclans_*%` quanto `%eclypseclans_*%`:

| Placeholder | Descrição |
|---|---|
| `%vclans_has_clan%` | `true` se o jogador tiver clã, `false` caso contrário |
| `%vclans_name%` | Nome do clã |
| `%vclans_tag%` | Tag do clã sem cores |
| `%vclans_tag_colored%` | Tag com a cor configurada pelo clã |
| `%vclans_tag_formatted%` | Tag com colchetes e cor (ex: `[ECY]`) ou vazio se não tiver clã |
| `%vclans_role%` | Nome do cargo do jogador |
| `%vclans_role_prefix%` | Prefixo do cargo (ex: `[Líder]`) |
| `%vclans_leader%` | Nome do líder do clã |
| `%vclans_points%` | Pontuação atual do clã |
| `%vclans_kdr%` | Taxa de abates/mortes do clã |
| `%vclans_members_count%`| Quantidade de membros atuais |
| `%vclans_duel_wins%` | Vitórias em duelos |
| `%vclans_war_wins%` | Vitórias em guerras |
| `%vclans_top_1_name%` | Nome do clã Top 1 |
| `%vclans_top_1_tag%` | Tag do clã Top 1 |

---

## 💾 7. Banco de Dados e Performance

- Suporte completo a **SQLite** (local, com modo `WAL` ultra-rápido) e **MySQL / MariaDB**.
- **O(1) Memory Cache:** Todos os membros e clãs ficam em memória RAM em estruturas `ConcurrentHashMap`, garantindo que eventos frequentes (dano, chat, scoreboard) **nunca** façam consultas lentas na thread principal do servidor.
- **Salvamento Assíncrono:** Todas as operações de banco de dados são despachadas assincronamente pelo `BukkitScheduler`.
- **Salvamento no Shutdown:** Ao desligar ou reiniciar o servidor (`onDisable`), todas as alterações pendentes são descarregadas em lote de forma síncrona, eliminando risco de perda de dados.

---

## ⌨️ 8. Lista de Comandos e Permissões

### Comandos de Jogador:
- `/clan` — Abre o menu principal interativo.
- `/clan criar <nome> <tag>` — Cria um novo clã.
- `/clan info [tag]` — Exibe detalhes completos de um clã.
- `/clan convidar <jogador>` — Convida um jogador online.
- `/clan aceitar <tag>` — Aceita um convite de clã.
- `/clan recusar <tag>` — Recusa um convite de clã.
- `/clan sair` — Sai do clã atual (com cooldown configurável).
- `/clan expulsar <jogador>` — Expulsa um membro de cargo inferior.
- `/clan promover <jogador>` — Promove um membro a um cargo acima.
- `/clan rebaixar <jogador>` — Rebaixa um membro a um cargo abaixo.
- `/clan lider <jogador>` — Transfere a liderança do clã.
- `/clan tag <nova-tag>` — Altera a tag do clã.
- `/clan descricao <texto>` — Atualiza a descrição do clã.
- `/clan top` — Abre o ranking geral de clãs.
- `/clan duelo` — Abre o menu de duelos.
- `/clan desafiar <tag>` — Desafia um clã diretamente.
- `/clan alianca [convidar|remover] <tag>` — Gerencia alianças.
- `/clan guerra [declarar|aceitar|render-se] [tag]` — Gerencia guerras.
- `/clan convites` — Ativa ou desativa o recebimento de convites.

### Comandos Administrativos (`vclans.admin` ou `eclypseclans.admin`):
- `/clan admin` — Abre o painel administrativo de clãs.
- `/clan admin top set` — Posiciona o painel de Top Clãs no spawn.
- `/clan admin top reload` — Atualiza o ranking do painel.
- `/clan admin top remove` — Remove o painel do spawn.
- `/clan admin arena criar <nome>` — Cria uma arena de duelos.
- `/clan admin arena setspawn1 <nome>` — Define o ponto do time 1.
- `/clan admin arena setspawn2 <nome>` — Define o ponto do time 2.
- `/clan admin arena setexit <nome>` — Define o ponto de saída.
- `/clan reload` — Recarrega todos os arquivos de configuração.

---

## 📦 9. Arquivo Compilado
O jar final gerado e testado com sucesso está localizado em:
`C:\Users\Vinicius Nogueira\Desktop\pluginss\clan\vClans.jar`
e `build\libs\vClans.jar`.
