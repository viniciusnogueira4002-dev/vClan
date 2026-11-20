package org.vine.vclans;

import org.vine.vclans.command.ClanChatCommand;
import org.vine.vclans.command.ClanCommand;
import org.vine.vclans.command.EventoCommand;
import org.vine.vclans.database.ClanRepository;
import org.vine.vclans.database.DatabaseManager;
import org.vine.vclans.database.MySQLDatabase;
import org.vine.vclans.database.SQLiteDatabase;
import org.vine.vclans.hook.DragonMineZHook;
import org.vine.vclans.hook.PlaceholderHook;
import org.vine.vclans.hook.VaultHook;
import org.vine.vclans.listener.ChatListener;
import org.vine.vclans.listener.ClanFarmWarListener;
import org.vine.vclans.listener.CombatListener;
import org.vine.vclans.listener.PlayerListener;
import org.vine.vclans.manager.*;
import org.vine.vclans.menu.MenuListener;
import org.vine.vclans.network.ClanModNetworkManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class VClans extends JavaPlugin {

    private static VClans instance;

    private FileConfiguration messagesConfig;
    private File messagesFile;

    private DatabaseManager databaseManager;
    private ClanRepository repository;

    private ClanManager clanManager;
    private InviteManager inviteManager;
    private FriendlyFireManager friendlyFireManager;
    private DuelManager duelManager;
    private WarManager warManager;
    private ArenaManager arenaManager;
    private TopDisplayManager topDisplayManager;
    private ScoreboardManager scoreboardManager;
    private GlobalEventManager globalEventManager;
    private BankUpgradeManager bankUpgradeManager;
    private SeasonWarManager seasonWarManager;
    private ClanModNetworkManager modNetworkManager;

    private VaultHook vaultHook;
    private DragonMineZHook dragonMineZHook;

    @Override
    public void onEnable() {
        instance = this;

        long start = System.currentTimeMillis();
        getLogger().info("==========================================");
        getLogger().info("    Iniciando VClans - 1.20.1       ");
        getLogger().info("       Ambiente Híbrido Forge/Bukkit      ");
        getLogger().info("==========================================");

        // Carregar configurações
        saveDefaultConfigs();

        // Inicializar Banco de Dados
        setupDatabase();

        // Inicializar Hooks
        this.vaultHook = new VaultHook();
        this.dragonMineZHook = new DragonMineZHook(this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this, "vclans").register();
            new PlaceholderHook(this, "eclypseclans").register();
            getLogger().info("PlaceholderAPI detectado e hooks 'vclans' e 'eclypseclans' registrados.");
        }

        // Inicializar Managers
        this.scoreboardManager = new ScoreboardManager(this);
        this.clanManager = new ClanManager(this, repository);
        this.inviteManager = new InviteManager(this);
        this.friendlyFireManager = new FriendlyFireManager(this);
        this.arenaManager = new ArenaManager(this);
        this.duelManager = new DuelManager(this);
        this.warManager = new WarManager(this);
        this.topDisplayManager = new TopDisplayManager(this);
        this.globalEventManager = new GlobalEventManager(this);
        this.bankUpgradeManager = new BankUpgradeManager(this);
        this.seasonWarManager = new SeasonWarManager(this);
        this.modNetworkManager = new ClanModNetworkManager(this);

        // Registrar Listeners
        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new MenuListener(), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ChatListener(this), this);
        pm.registerEvents(new ClanFarmWarListener(this), this);
        pm.registerEvents(globalEventManager, this);

        // Registrar Comandos
        ClanCommand clanCommand = new ClanCommand(this);
        if (getCommand("clan") != null) {
            getCommand("clan").setExecutor(clanCommand);
            getCommand("clan").setTabCompleter(clanCommand);
        }

        ClanChatCommand clanChatCommand = new ClanChatCommand(this);
        if (getCommand("clanchat") != null) {
            getCommand("clanchat").setExecutor(clanChatCommand);
        }

        EventoCommand eventoCommand = new EventoCommand(this);
        if (getCommand("evento") != null) {
            getCommand("evento").setExecutor(eventoCommand);
            getCommand("evento").setTabCompleter(eventoCommand);
        }

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("VClans ativado com sucesso em " + elapsed + "ms!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Desativando VClans e salvando dados com segurança...");

        if (duelManager != null) {
            duelManager.cleanup();
        }

        if (warManager != null) {
            warManager.cleanup();
        }

        if (inviteManager != null) {
            inviteManager.cleanup();
        }

        if (scoreboardManager != null) {
            scoreboardManager.cleanup();
        }

        if (topDisplayManager != null) {
            topDisplayManager.cleanup();
        }

        if (globalEventManager != null) {
            globalEventManager.cleanup();
        }

        if (seasonWarManager != null) {
            seasonWarManager.cleanup();
        }

        if (bankUpgradeManager != null) {
            bankUpgradeManager.cleanup();
        }

        if (modNetworkManager != null) {
            modNetworkManager.cleanup();
        }

        if (dragonMineZHook != null) {
            dragonMineZHook.cleanup();
        }

        if (clanManager != null) {
            clanManager.flushAll();
        }

        // Cancelar todas as tarefas pendentes do scheduler para liberar threads e memória
        Bukkit.getScheduler().cancelTasks(this);

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("VClans desativado e memória liberada com sucesso.");
    }

    private void saveDefaultConfigs() {
        saveDefaultConfig();
        saveResourceIfNotExists("messages.yml");
        saveResourceIfNotExists("menus.yml");
        saveResourceIfNotExists("ranks.yml");
        saveResourceIfNotExists("duels.yml");
        saveResourceIfNotExists("wars.yml");
        saveResourceIfNotExists("alliances.yml");
        saveResourceIfNotExists("top.yml");
        saveResourceIfNotExists("database.yml");
        saveResourceIfNotExists("eventoglobal.yml");

        messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(getDataFolder(), resourcePath);
        if (!file.exists()) {
            saveResource(resourcePath, false);
        }
    }

    private void setupDatabase() {
        File dbFile = new File(getDataFolder(), "database.yml");
        FileConfiguration dbConfig = YamlConfiguration.loadConfiguration(dbFile);

        String type = dbConfig.getString("type", "SQLITE").toUpperCase();

        if (type.equals("MYSQL")) {
            String host = dbConfig.getString("mysql.host", "localhost");
            int port = dbConfig.getInt("mysql.port", 3306);
            String db = dbConfig.getString("mysql.database", "eclypse_clans");
            String user = dbConfig.getString("mysql.username", "root");
            String pass = dbConfig.getString("mysql.password", "");
            boolean ssl = dbConfig.getBoolean("mysql.ssl", false);

            getLogger().info("Conectando ao banco de dados MySQL em " + host + ":" + port + "...");
            this.databaseManager = new MySQLDatabase(host, port, db, user, pass, ssl);
        } else {
            String file = dbConfig.getString("sqlite.file", "clans.db");
            getLogger().info("Inicializando armazenamento SQLite local (" + file + ")...");
            this.databaseManager = new SQLiteDatabase(getDataFolder(), file);
        }

        this.databaseManager.initTables();
        this.repository = new ClanRepository(this.databaseManager);
    }

    public void reloadAll() {
        reloadConfig();
        messagesFile = new File(getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        arenaManager.loadArenas();
        topDisplayManager.loadConfig();
        topDisplayManager.spawnOrUpdateDisplay();
        if (globalEventManager != null) {
            globalEventManager.loadConfig();
        }
        if (seasonWarManager != null) {
            seasonWarManager.loadConfig();
        }
    }

    public static VClans getInstance() {
        return instance;
    }

    public FileConfiguration getMessages() {
        return messagesConfig;
    }

    public ClanRepository getRepository() {
        return repository;
    }

    public ClanManager getClanManager() {
        return clanManager;
    }

    public InviteManager getInviteManager() {
        return inviteManager;
    }

    public FriendlyFireManager getFriendlyFireManager() {
        return friendlyFireManager;
    }

    public DuelManager getDuelManager() {
        return duelManager;
    }

    public WarManager getWarManager() {
        return warManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public TopDisplayManager getTopDisplayManager() {
        return topDisplayManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public GlobalEventManager getGlobalEventManager() {
        return globalEventManager;
    }

    public BankUpgradeManager getBankUpgradeManager() {
        return bankUpgradeManager;
    }

    public SeasonWarManager getSeasonWarManager() {
        return seasonWarManager;
    }

    public ClanModNetworkManager getModNetworkManager() {
        return modNetworkManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public DragonMineZHook getDragonMineZHook() {
        return dragonMineZHook;
    }
}

