# Guia de Arquitetura e Padrão de Menus - vClans

Este documento orienta agentes de IA e desenvolvedores trabalhando no plugin **vClans** (`C:\Plugins\vClan`), detalhando como funciona a integração com a **Rede Eclypse** (Mohist 1.20.1 / Forge 47.4.13) e, principalmente, **como montar menus seguindo o padrão visual atual usado no mod (`Astral Aberto` do `EclypseCore`)** integrado ao protocolo do plugin.

---

## 1. Visão Geral do Plugin (`vClans`)

- **Nome / Artefato:** `vClans` (`build/libs/vClans.jar`, definido em `build.gradle`)
- **Plataforma:** Spigot / Bukkit API `1.20.1` rodando em servidor híbrido Forge/Bukkit (Mohist 1.20.1, Java 17)
- **Pacote Base:** `org.vine.vclans`
- **Classe Principal:** `org.vine.vclans.VClans`
- **Hooks Ativos:**
  - `VaultHook` (economia em Zenis/Money com `getBalance`, `hasMoney`, `withdraw`, `deposit`)
  - `DragonMineZHook` (leitura/saque exatos de Training Points `TP` via `Resources.getTrainingPointsExact()`/`setTrainingPointsExact()` + sync `ResourceSyncS2C`/`StatsSyncS2C` e multiplicador passivo no `DMZEvent.TPGainEvent`)
  - `PlaceholderHook` (`%vclans_*%` e `%eclypseclans_*%`, incluindo `%vclans_banco_tp%`, `%vclans_banco_money%`, `%vclans_guerra_pontos%`, `%vclans_guerra_rank%`, `%vclans_mult_tp%`, `%vclans_mult_money%`, `%vclans_mult_guerra%`, `%vclans_max_membros%`)
  - `LuckPerms` (permissões via Bukkit/Vault)
- **Estrutura Principal:**
  - `command/`: `ClanCommand`, `ClanChatCommand`, `EventoCommand`
  - `manager/`: `ClanManager` (inclui cooldown persistente de 24h/`86400s` para troca de Tag via `clan.tag.change-cooldown-seconds`), `BankUpgradeManager` (banco mão única, 4 upgrades passivos e `openClanModUi(Player, int)`), `SeasonWarManager` (temporada semanal de guerra PvE/Farm/PvP + janelas 2x em `wars.yml`), `WarManager`, `DuelManager`, `InviteManager`, `FriendlyFireManager`, `ArenaManager`, `TopDisplayManager` (listener `EntitiesLoadEvent`, hologramas `TextDisplay` voláteis com `setPersistent(false)` + chave PDC `vclans_top_display` e auto-limpeza de duplicatas), `ScoreboardManager`, `GlobalEventManager`
  - `listener/`: `CombatListener`, `PlayerListener`, `ChatListener`, `ClanFarmWarListener` (multiplicadores de farm em mobs/blocos e proteção contra blocos colocados por jogadores)
  - `menu/`: menus Bukkit em inventário herdando `Menu.java` (`MainMenu`, `BankUpgradeMenu`, `RankingMenu`, `MembersMenu`, `MemberActionMenu`, `AlliancesMenu`, `DuelMenu`, `ClanSettingsMenu`, `InvitePlayerMenu`, `PendingInvitesMenu`, `TagColorMenu`, `ConfirmationMenu`, `AdminMenu`) — mantidos como **fallback** caso o `EclypseCore` esteja ausente/desativado
  - `network/`: `ClanModNetworkManager.java` (canal `vclans:ui` compatível com Bukkit Plugin Messaging)
  - `database/`: `DatabaseManager` (inclui tabela `eclypse_clan_economy`, `last_tag_change` em `eclypse_clans` e colunas de doação/guerra em `eclypse_clan_members`), `ClanRepository`, `SQLiteDatabase`, `MySQLDatabase`
- **Prefixo Oficial (`messages.yml` e `eventoglobal.yml`):**
  `&#7B2CBF&lE&#8E37D7&lC&#A247E8&lL&#B75CFF&lY&#CB75FF&lP&#DF92FF&lS&#F0B2FF&lE &r&#9A7AA0» &r` (`ECLYPSE »`)

---

## 2. Arquitetura do Menu Atual no Mod (`Astral Aberto`)

Atualmente, os menus customizados do mod (`EclypseCore` — ex.: `DungeonBuyScreen`, `DungeonMenuScreen`, `DungeonBossPromptScreen`, `KeyForgeScreen`, `CompanionScreen` e `ZenkaiScreen`) **não usam texturas PNG de janela inteira nem inventário de baú**. Eles são desenhados programaticamente no cliente Forge 1.20.1 usando o tema visual **Astral Aberto** (`kk.sleexzx.features.rifts.client.gui`).

Quando o **vClans** abre ou sincroniza uma interface customizada no mod do cliente, a tela no cliente deve seguir exatamente essa mesma stack visual para manter identidade única no servidor.

### 2.1. Primitivas do Sistema Visual (Cliente Forge 1.20.1)

No mod (`C:\Plugins\EclypseCore\src\main\java\kk\sleexzx\features\rifts\client\gui`), o menu é montado sobre 5 componentes principais:

1. **`KitCanvas` (Resolução Lógica Proporcional)**
   - **Canvas completo (2 ou 3 colunas):** `580 x 346` (padrão de `ZenkaiScreen`, `CompanionScreen`, `DungeonBuyScreen`, `DungeonMenuScreen`).
   - **Canvas modal (confirmações/alertas):** `368 x 224` (padrão de `DungeonBossPromptScreen`).
   - **Cálculo em `init()` e no início de `render(...)`:**
     ```java
     this.canvas.computeFisico(this.width, this.height, 1.6D, 16);
     ```
   - **Conversão do mouse:**
     - Em `render(...)`: `this.lx = this.canvas.localXi(mouseX); this.ly = this.canvas.localYi(mouseY);`
     - Em `mouseClicked(...)` / `mouseReleased(...)`: `double x = this.canvas.localX(mouseX); double y = this.canvas.localY(mouseY);`
   - **Renderização encapsulada:** tudo do menu é desenhado entre `this.canvas.begin(graphics)` e `this.canvas.end(graphics)` dentro de um bloco `try / finally`.

2. **`DungeonUi` + Fonte `eclypsecore:menu_dmz`**
   - **Fonte:** `assets/eclypsecore/font/menu_dmz.json` (`eclypsecore:chakra_petch.ttf`, `size: 9.75`, `oversample: 4.0`, com fallback para `minecraft:default`).
   - **Texto:**
     - `DungeonUi.drawAt(graphics, texto, x, y, corARGB, scale)`
     - `DungeonUi.centered(graphics, texto, centerX, y, corARGB, scale, outline)`
     - `DungeonUi.width(texto)` / `DungeonUi.inkWidth(texto, scale)`
     - `DungeonUi.wrap(texto, maxW)`
   - **Painéis e barras nativos:**
     - `DungeonUi.panel(graphics, x, y, w, h, bgColor, borderColor)` — retângulo com borda de `1px`
     - `DungeonUi.surfacePanel(graphics, x, y, w, h, accentColor)` — cartão padrão (`0xD811131B`, borda `0xCC2C3246`, filete superior na cor `accentColor`)
     - `DungeonUi.cornerBrackets(graphics, x, y, w, h, color, len)` — cantoneiras `2px` de destaque (usado em cards/itens selecionados)
     - `DungeonUi.bar(graphics, x, y, w, h, ratio, bgColor, fillColor, borderColor)` — barra de progresso/TP/nível

3. **`BotaoKit` (Botões 9-Slice)**
   - **Estilos (`BotaoKit.Estilo`):**
     - `BotaoKit.Estilo.LARANJA`: botão de ação principal, aba ativa ou confirmação.
     - `BotaoKit.Estilo.ESCURO`: botão secundário, navegação, paginação e botão de fechar.
   - **Dimensão mínima:** `BotaoKit.ALTURA = 20` (botão quadrado `20x20` usa automaticamente os sprites `*-quadrado-<estado>`; largura > `20` usa 9-slice horizontal/vertical).
   - **Assets no Resource Pack do mod:**
     - `textures/gui/kit/botao/(laranja|escuro)-(normal|hover|pressionado|desativado)-(esquerda|centro|direita).png`
     - `textures/gui/kit/botao/(laranja|escuro)-quadrado-(normal|hover|pressionado|desativado).png`
     - Ícones em `textures/gui/kit/icones/`: `fechar`, `confirmar`, `ranking`, `ajuda`, `mais`, `menos`, `seta-esquerda`, `seta-direita`, `seta-cima`, `seta-baixo`.
   - **Padrão de rótulo nítido:** deixe `btn.rotulo = ""` e desenhe o texto manualmente por cima do botão usando `DungeonUi.centered(...)` com escala `1.10F`–`1.40F`.
   - **Interação:** registre os botões visíveis do frame em `visibleButtons`, faça hit-test em `mouseClicked` armazenando `pressedBotao` e finalize no `mouseReleased` (o `BotaoKit` já dispara `Kit.somClique()` e executa `acao.run()` apenas se soltar dentro do botão).

4. **`Kit` e `DungeonKit` (Helpers de UI e Preview)**
   - `Kit.txt("...")`: cria `MutableComponent` já com o `Style` da fonte `menu_dmz`.
   - `Kit.truncar(font, texto, maxW)`: trunca com `..` evitando estouro de largura.
   - `Kit.icone("fechar")`: resolve o `ResourceLocation` do ícone.
   - `DungeonKit.inside(lx, ly, x, y, w, h)`: colisão retangular com mouse lógico.
   - `DungeonKit.strip(texto)`: remove códigos `§.` quando for renderizar texto colorido por ARGB no canvas.
   - `DungeonKit.preview(entityId)` / `InventoryScreen.renderEntityInInventoryFollowsMouse(...)`: preview 3D de jogador/entidade no painel central quando aplicável.

---

### 2.2. Paleta Oficial e Estrutura Visual (`Astral Aberto`)

#### Paleta de Cores (ARGB)
| Uso | Cor Hex | Observação |
| :--- | :--- | :--- |
| Backdrop tela cheia | `0x9903050B` | Desenhado antes de `canvas.begin(...)` |
| Linha topo / base (tela cheia) | `0x557C3AED` / `0x4438BDF8` | `1px` de altura no topo (`y=0`) e base (`y=height-1`) |
| Fundo de painel / card normal | `0xD811131B` / `0xCC0D111E` | Translúcido escuro |
| Card hover / selecionado | `0xDA14192A` / `0xEB181E32` | Destaque sutil no hover/seleção |
| Badge / pill de status (topo) | `0xC80E121D` | Borda violeta sutil `0xFF3D3058` |
| Borda sutil padrão | `0xFF283550` | Borda de cards/caixas inativas |
| Ciano (principal / TP / seleção) | `0xFF38BDF8` | `CIANO` |
| Violeta / Violeta Claro | `0xFF7C3AED` / `0xFFA78BFA` | `VIOLETA` / `VIOLETA_CLARO` |
| Dourado / Dourado Claro | `0xFFF59E0B` / `0xFFFDE047` | Zenis, rankings, ações premium |
| Verde / Vermelho | `0xFF4CD97B` / `0xFFF87171` | Concluído/Ativo vs Erro/Bloqueado |
| Texto título / primário / secundário | `0xFFFFFFFF` / `0xFFF1F5F9` / `0xFFCBD5E1` | Muted secundário: `0xFF9FB4D6` |

#### Anatomia Padrão de uma Tela `580 x 346`
1. **Fundo da `Screen`:**
   - Sobrescreva `renderBackground(GuiGraphics)` vazio (para não escurecer duas vezes).
   - Em `render(...)`, antes de `canvas.begin(graphics)`:
     - `graphics.fill(0, 0, this.width, this.height, 0x9903050B);`
     - `graphics.fill(0, 0, this.width, 1, 0x557C3AED);`
     - `graphics.fill(0, this.height - 1, this.width, this.height, 0x4438BDF8);`
2. **Cabeçalho (`y = 0 .. 34`):**
   - Eyebrow rastreado à esquerda: `"ECLYPSE  •  CLÃS"` em `(2, 1)`, cor `DOURADO` ou `CIANO`, escala `1.10F`.
   - Título principal em `(2, 14)`, cor `0xFFFFFFFF`, escala `1.50F`.
   - Pill(s) de saldo/status (ex.: Zenis e TP do jogador / banco do clã) alinhadas à esquerda do botão de fechar (`zx = 554 - w`, `y = 2`, altura `21`, filete `2px` à esquerda).
   - Botão Fechar em `(560, 2, 20, 20)` com `Estilo.ESCURO`, `rotulo = ""`, `icone = Kit.icone("fechar")`.
   - Abas (quando houver múltiplos painéis no mesmo menu, ex.: Visão Geral / Banco & Upgrades / Membros / Guerra): posicionadas abaixo do título (`y ~ 36..44`) ou no rodapé, usando `BotaoKit` com `LARANJA` para aba ativa e `ESCURO` para inativas.
3. **Corpo (`y = 40 .. 338`, dividido em 2 ou 3 colunas):**
   - **Esquerda (`x = 0, w ~ 172..184`):** lista clicável com cards de altura fixa (`38px`, gap `4px`), filete lateral de `2px` (`3px` se selecionado), `cornerBrackets` no selecionado e scrollbar à direita se exceder o número visível de linhas (`mouseScrolled`).
   - **Centro (`x ~ 188, w ~ 196`):** palco visual — resumo do clã (tag formatada, brasão/skin do líder com `InventoryScreen.renderEntityInInventoryFollowsMouse` ou ranking em destaque, barra de progresso/status).
   - **Direita (`x ~ 396, w ~ 184`):** inspetor do item/upgrade/membro selecionado — bônus atual vs próximo nível, custos (TP e Zenis), pré-requisitos e botão principal de ação na base (`BotaoKit` altura `24`–`28`, `Estilo.LARANJA`).

---

### 2.3. Regras Críticas para Evitar Bugs Visuais

1. **Escala mínima de texto (`>= 1.05F`):**
   - Em `1080p` com GUI Scale `2`, `canvas.computeFisico(..., 1.6D, 16)` aplica escala `0.8x` no canvas.
   - Na fonte `chakra_petch` (`menu_dmz`), qualquer `drawAt` ou `centered` com escala `<= 1.0F` faz o OpenGL descartar a linha superior de pixels (cortando o topo de números como `0`, `5`, `8`, `9` e títulos).
   - Use sempre **`1.05F`–`1.20F`** em labels/números e **`1.35F`–`1.70F`** em títulos.
   - Ao truncar texto que será desenhado com escala `s`, divida a largura disponível pela escala: `Kit.truncar(this.font, texto, Math.round(maxW / s))`.
2. **`canvas.scissor(...)` exige coordenadas absolutas `(minX, minY, maxX, maxY)`:**
   - **Nunca** passe `(x, y, largura, altura)`.
   - Sempre chame `graphics.disableScissor()` em bloco `finally`.
3. **Tooltips sempre fora do `canvas`:**
   - Durante os `draw*` dentro do `canvas.begin(graphics)`, apenas detecte hover com `this.lx`/`this.ly` e salve em `this.pendingTooltip`.
   - Chame `graphics.renderComponentTooltip(this.font, this.pendingTooltip, mouseX, mouseY)` **somente depois** de `this.canvas.end(graphics)` usando `mouseX` e `mouseY` originais da tela.
4. **Preview 3D de entidades sem vazar profundidade:**
   - Se renderizar jogador/NPC no painel central, isole com `canvas.scissor(...)`, `poseStack.pushPose()`, `poseStack.translate(0, 0, 100)` e `Mth.clamp(...)` nos ângulos de olhar do mouse.
5. **Anti-spam de pacotes no clique:**
   - Mantenha cooldown local de clique (`400ms`–`1000ms`) para ações de escrita (depósito, compra de upgrade, convite, etc.) espelhando o rate-limit do servidor.

---

## 3. Fluxo de Rede entre o `vClans` (Servidor Bukkit) e o Menu no Mod (Cliente)

O **vClans** já possui uma ponte de rede implementada em:
- [ClanModNetworkManager.java](src/main/java/org/vine/vclans/network/ClanModNetworkManager.java)

### 3.1. Canal e Protocolo Existentes (`vclans:ui`)
- **Canal:** `"vclans:ui"` (registrado via `Bukkit.getMessenger()` como incoming/outgoing plugin channel; no Forge corresponde a um canal `ResourceLocation("vclans", "ui")` ou `SimpleChannel`/EventNetworkChannel compatível com Plugin Messaging)
- **Versão de Protocolo:** `ClanModNetworkManager.PROTOCOL_VERSION = 1`
- **Tamanho máximo C2S:** `MAX_C2S_PACKET_BYTES = 64`
- **Threading:** `onPluginMessageReceived` clona `message` e sempre despacha o processamento para a main thread do Bukkit (`Bukkit.getScheduler().runTask(...)`).
- **Rate limits no servidor:**
  - `250ms` para pedidos de sincronização (`C2S_REQUEST_SYNC`)
  - `400ms` para ações de escrita (`C2S_DEPOSIT_TP`, `C2S_DEPOSIT_MONEY`, `C2S_BUY_UPGRADE`)

#### Pacotes Client -> Server (`C2S`) atualmente aceitos
Todos iniciam com `byte version` (`1`) + `byte action`:
- `0x01` (`C2S_REQUEST_SYNC`): sem payload extra — o servidor responde com `S2C_SYNC_SNAPSHOT`.
- `0x02` (`C2S_DEPOSIT_TP`): `long amount` (`> 0`) — executa `BankUpgradeManager.depositTp(player, amount)`.
- `0x03` (`C2S_DEPOSIT_MONEY`): `double amount` (`Double.isFinite(amount) && amount > 0`) — executa `BankUpgradeManager.depositMoney(player, amount)`.
- `0x04` (`C2S_BUY_UPGRADE`): `byte upgradeId` (`UpgradeType.fromId(upgradeId)`) — executa `BankUpgradeManager.buyUpgrade(player, type)`.

#### Pacotes Server -> Client (`S2C`) atualmente enviados
Todos iniciam com `byte version` (`1`) + `byte type`:
- `0x01` (`S2C_SYNC_SNAPSHOT`):
  1. `boolean hasClan`
  2. `long playerTp` (via `DragonMineZHook.getPlayerTp`)
  3. `double playerMoney` (via `VaultHook.getBalance`)
  4. `boolean doubleWarActive`
  5. Se `hasClan == true`:
     - `UTF clanId`, `UTF name`, `UTF tag`, `UTF tagColor`, `UTF roleName`
     - `int membersCount`, `int maxMembers`
     - `boolean canDeposit`, `boolean canUpgrade`
     - `long bankTp`, `double bankMoney`
     - `int seasonWarPoints`, `int clanRank`
     - `byte upgradeCount` seguido para cada upgrade de: `byte id`, `int level`, `int maxLevel`, `double currentBonus`, `double nextBonus`, `long nextTpCost`, `double nextMoneyCost`
     - `byte topDonorsCount` (até 5) seguido para cada doador de: `UTF name`, `long donatedTp`, `double donatedMoney`, `int warPointsContributed`
  6. `byte topWarCount` (até 10) seguido para cada clã do ranking de: `UTF name`, `UTF tag`, `UTF tagColor`, `int seasonWarPoints`
- `0x02` (`S2C_ACTION_FEEDBACK`):
  - `boolean success`, `UTF message`

### 3.2. Regras Obrigatórias para Novos Recursos / Expansão do Menu

1. **Servidor 100% Autoritativo:**
   - O cliente mod **nunca** calcula custos, descontos, permissões nem altera dados do clã/TP/dinheiro diretamente.
   - O mod envia apenas intenções compactas (ID da ação, ID numérico do alvo/upgrade, valor ou string curta validada) dentro de um limite rígido de bytes no C2S.
   - O `vClans` valida na main thread: jogador online, pertencimento ao clã, `ClanPermission`, `ClanRole`, cooldown/rate-limit, limites (`Double.isFinite`, valores negativos/zero) e só então muta banco/SQLite/MySQL/Vault/DMZ, enviando `S2C_ACTION_FEEDBACK` e um novo `S2C_SYNC_SNAPSHOT` em seguida.
2. **Ordem Binária Idêntica (`DataOutputStream` <-> `DataInputStream` / `FriendlyByteBuf`):**
   - Ao usar `vclans:ui`, os tipos primitivos (`writeByte`, `writeBoolean`, `writeInt`, `writeLong`, `writeDouble`, `writeUTF`) no Bukkit e os leitores no mod Forge precisam estar na **mesma ordem exata** e com `UTF` no formato padrão Java (`2 bytes` unsigned short length + UTF-8 modificado, como `DataOutputStream.writeUTF`).
   - Qualquer quebra de formato ou adição de campo novo em pacote existente exige incrementar `ClanModNetworkManager.PROTOCOL_VERSION` (e validar versão igual nos dois lados) — e se houver pacotes via `ModMessages` no `EclypseCore`, incrementar também `EclypseCore.VERSION`.
3. **Fallback / Convivência com Menus Bukkit (`menu/*.java`):**
   - Jogadores ou sub-telas ainda não portadas para o mod continuam utilizando os menus de inventário em `org.vine.vclans.menu` (`MainMenu`, `BankUpgradeMenu`, `RankingMenu`, etc.). Ao portar uma nova tela para o mod, mantenha a lógica de negócio nos `*Manager` (nunca dentro de `Menu` Bukkit) para que tanto o menu em baú quanto a tela Astral Aberto no mod compartilhem exatamente as mesmas regras e validações.

### 3.3. Ponte Reflexiva com `EclypseCore` (`VClansBridge`)
- Para abertura direta de telas customizadas no mod via comandos (`/clan`, `/clan banco`, `/clan top`, `/clan duelo`, `/clan alianca`, `/clan config`, `/clan admin` e convites pendentes), o `BankUpgradeManager.openClanModUi(Player, int initialTab)` chama `kk.sleexzx.features.clan.server.VClansBridge.openClanMenu(UUID, int)` via reflexão (`TAB_OVERVIEW=0`, `TAB_BANK=1`, `TAB_MEMBERS=2`, `TAB_COMBAT=3`, `TAB_RANKINGS=4`, `TAB_SETTINGS=5`, `TAB_ADMIN=6`).
- **Ordenação de fechamento de inventário:** se o jogador estiver com um inventário `org.vine.vclans.menu.Menu` aberto, `player.closeInventory()` **deve** ser chamado **antes** de disparar o pacote Forge `OpenClanMenuS2CPacket`, evitando que o pacote vanilla de fechamento de container feche a `ClanScreen` no cliente.
- Toda validação de permissões (`ClanPermission`), hierarquia (`ClanRole`), regex/comprimento/cooldown de tag/nome/descrição, economia Vault/DMZ, duelos, alianças, guerras e ações de Staff continua executando estritamente na thread principal do servidor via `VClansBridge`.

---

## 4. Compilação do `vClans`

Na raiz `C:\Plugins\vClan`:

```powershell
# Compilar e verificar erros Java
./gradlew.bat compileJava

# Gerar vClans.jar de produção (build/libs/vClans.jar)
./gradlew.bat build
```

---

## 5. Regras Operacionais para Agentes de IA

1. **Responder / Explicar Antes de Codar:**
   Ao receber uma dúvida, responda apenas o que foi perguntado sem gerar código não solicitado. Antes de editar arquivos de código, explique o plano e confirme alterações de escopo maior quando necessário.
2. **Proibição de `git commit` e `git push` Automáticos:**
   Nunca execute `git commit` ou `git push` sem autorização explícita do usuário.
3. **Segurança no Canal `vclans:ui`:**
   Sempre valide tamanho do pacote recebido, trate `Exception` sem propagar para o Netty/servidor, garanta execução na main thread do Bukkit e preserve rate-limits por `UUID`.
