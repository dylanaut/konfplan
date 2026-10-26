package kreyj.konfplan.domain.lizenz;

/**
 * @param claims nur bei {@link LizenzZustand#LIZENZIERT} und {@link LizenzZustand#GRACE} gesetzt
 * @param grund  nur bei {@link LizenzZustand#EINGESCHRAENKT} gesetzt
 */
public record LizenzStatus(LizenzZustand zustand, LizenzClaims claims, LizenzFehler.Grund grund) {

    public static LizenzStatus unlizenziert() {
        return new LizenzStatus(LizenzZustand.UNLIZENZIERT, null, null);
    }

    public static LizenzStatus lizenziert(LizenzClaims claims) {
        return new LizenzStatus(LizenzZustand.LIZENZIERT, claims, null);
    }

    public static LizenzStatus grace(LizenzClaims claims) {
        return new LizenzStatus(LizenzZustand.GRACE, claims, null);
    }

    public static LizenzStatus eingeschraenkt(LizenzFehler.Grund grund) {
        return new LizenzStatus(LizenzZustand.EINGESCHRAENKT, null, grund);
    }
}
