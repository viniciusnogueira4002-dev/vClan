package org.vine.vclans.model;

/**
 * Tesouraria dual do clã (TP do DragonMineZ + Dinheiro via Vault).
 * Política mão única: aceita depósitos e gastos atômicos em upgrades (sem saque).
 */
public class ClanBank {

    private long tp;
    private double money;

    public ClanBank() {
        this.tp = 0L;
        this.money = 0.0;
    }

    public ClanBank(long tp, double money) {
        this.tp = Math.max(0L, tp);
        this.money = Double.isFinite(money) ? Math.max(0.0, money) : 0.0;
    }

    public synchronized long getTp() {
        return tp;
    }

    public synchronized void setTp(long tp) {
        this.tp = Math.max(0L, tp);
    }

    public synchronized double getMoney() {
        return money;
    }

    public synchronized void setMoney(double money) {
        if (!Double.isFinite(money)) {
            return;
        }
        this.money = Math.max(0.0, money);
    }

    public synchronized boolean addTp(long amount) {
        if (amount <= 0L) {
            return false;
        }
        try {
            this.tp = Math.addExact(this.tp, amount);
        } catch (ArithmeticException overflow) {
            this.tp = Long.MAX_VALUE;
        }
        return true;
    }

    public synchronized boolean addMoney(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            return false;
        }
        double next = this.money + amount;
        if (!Double.isFinite(next)) {
            return false;
        }
        this.money = Math.round(next * 100.0) / 100.0;
        return true;
    }

    public synchronized boolean hasEnough(long costTp, double costMoney) {
        if (costTp < 0L || !Double.isFinite(costMoney) || costMoney < 0.0) {
            return false;
        }
        return this.tp >= costTp && this.money + 1e-6 >= costMoney;
    }

    /**
     * Consome TP e Money de forma atômica caso ambos os saldos sejam suficientes.
     */
    public synchronized boolean trySpend(long costTp, double costMoney) {
        if (!hasEnough(costTp, costMoney)) {
            return false;
        }
        this.tp = Math.max(0L, this.tp - costTp);
        double remainingMoney = Math.max(0.0, this.money - costMoney);
        this.money = Math.round(remainingMoney * 100.0) / 100.0;
        return true;
    }
}
