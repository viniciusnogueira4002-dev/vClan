package org.vine.vclans.model;

public enum ClanPermission {
    INVITE("Convidar Membros"),
    KICK("Expulsar Membros"),
    PROMOTE("Promover Membros"),
    DEMOTE("Rebaixar Membros"),
    EDIT_INFO("Editar Informações"),
    TAG_COLOR("Alterar Cor da Tag"),
    ALLIANCE("Gerenciar Alianças"),
    DUEL("Iniciar e Aceitar Duelos"),
    WAR("Gerenciar Guerras"),
    BANK_DEPOSIT("Depositar no Banco"),
    BANK_WITHDRAW("Sacar do Banco"),
    SETTINGS("Alterar Configurações"),
    DISBAND("Desfazer Clã");

    private final String description;

    ClanPermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

